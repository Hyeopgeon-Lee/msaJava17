package kopo.poly.filter;

import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Component
public class AutoRefreshOn401Filter implements GlobalFilter, Ordered {

    // -------------------------------------------------
    // 설정값(쿠키 이름)
    // -------------------------------------------------
    @Value("${jwt.token.access.name:jwtAccessToken}")
    private String accessCookieName;

    @Value("${jwt.token.refresh.name:jwtRefreshToken}")
    private String refreshCookieName;

    // -------------------------------------------------
    // 설정값(사용자 API 서버) — 상수처럼 바로 주입
    // 예) http://localhost:9100/login/v1/refresh
    // -------------------------------------------------
    @Value("${api.server.user.refresh-endpoint:/login/v1/refresh}")
    private String REFRESH_PATH;

    @Value("${api.server.user.protocol}://${api.server.user.host}:${api.server.user.port}${api.server.user.refresh-endpoint}")
    private String REFRESH_URL;

    private final WebClient webClient;

    private static final String ATTR_RETRIED = "X-RT-RETRIED";

    // -------------------------------------------------
    // helpers (간결하게)
    // -------------------------------------------------

    /**
     * 현재 요청이 /refresh 자신인지(무한 루프 방지)
     */
    private boolean isSelfRefreshCall(String path) {
        return CmmUtil.nvl(path).startsWith(CmmUtil.nvl(REFRESH_PATH, "/login/v1/refresh"));
    }

    /**
     * Authorization 없으면 AT 쿠키로 보강(Bearer …)해서 반환
     */
    private String extractAuthOrAtCookie(ServerWebExchange exchange) {
        String auth = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (!auth.isBlank()) return auth;
        var atCookie = exchange.getRequest().getCookies().getFirst(accessCookieName);
        return (atCookie == null) ? "" : "Bearer " + CmmUtil.nvl(atCookie.getValue());
    }

    /**
     * 선제 리프레시: RT 있고, AT(Authorization/쿠키) “없을 때만”
     */
    private boolean needPreRefresh(ServerWebExchange exchange) {
        boolean hasRt = exchange.getRequest().getCookies().containsKey(refreshCookieName);
        if (!hasRt) return false;
        return extractAuthOrAtCookie(exchange).isBlank();
    }

    /**
     * 응답에 Set-Cookie 일괄 반영
     */
    private void applySetCookies(ServerHttpResponse resp, List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return;
        setCookies.forEach(sc -> resp.getHeaders().add(HttpHeaders.SET_COOKIE, sc));
    }

    /**
     * 새 AT로 Authorization/Cookie 주입
     */
    private ServerWebExchange mutateWithNewAT(ServerWebExchange exchange, String at) {
        String newAt = CmmUtil.nvl(at);
        ServerHttpRequest.Builder rb = exchange.getRequest().mutate()
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + newAt));

        String cookieHeader = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE));
        String atPair = accessCookieName + "=" + newAt;

        if (!cookieHeader.contains(accessCookieName + "=")) {
            String merged = cookieHeader.isBlank() ? atPair : cookieHeader + "; " + atPair;
            rb.headers(h -> h.set(HttpHeaders.COOKIE, merged));
        }
        return exchange.mutate().request(rb.build()).build();
    }

    /**
     * Set-Cookie에서 AT 값 추출
     */
    private String extractAt(List<String> setCookies) {
        if (setCookies == null) return null;
        String prefix = accessCookieName + "=";
        for (String sc : setCookies) {
            int i = sc.indexOf(prefix);
            if (i >= 0) {
                String sub = sc.substring(i + prefix.length());
                int semi = sub.indexOf(';');
                return (semi >= 0) ? sub.substring(0, semi) : sub;
            }
        }
        return null;
    }

    /**
     * /refresh 호출 → 새 AT/쿠키 수집
     */
    private Mono<RefreshOutcome> callRefresh(ServerWebExchange exchange) {
        String cookieHeader = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE));
        String ua = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT));

        return webClient.post()
                .uri(REFRESH_URL)
                .headers(h -> {
                    if (!cookieHeader.isBlank()) h.set(HttpHeaders.COOKIE, cookieHeader);
                    if (!ua.isBlank()) h.set(HttpHeaders.USER_AGENT, ua);
                    h.set(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    h.setContentLength(0);
                })
                .retrieve()
                .toEntity(byte[].class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    log.debug("[AutoRefresh] refresh error: {}", e.toString());
                    return Mono.empty();
                })
                .flatMap(res -> {
                    if (!res.getStatusCode().is2xxSuccessful()) return Mono.empty();
                    List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
                    String at = extractAt(setCookies);
                    return Mono.justOrEmpty(new RefreshOutcome(at, setCookies));
                });
    }

    /**
     * 재시도 대비(POST/PUT/PATCH JSON) 바디 캐시 — /refresh는 스킵
     */
    private Mono<ServerWebExchange> cacheBodyIfNeeded(ServerWebExchange exchange) {
        HttpMethod m = exchange.getRequest().getMethod();
        boolean needsBody = (m == HttpMethod.POST || m == HttpMethod.PUT || m == HttpMethod.PATCH);
        if (!needsBody) return Mono.just(exchange);

        String path = exchange.getRequest().getPath().value();
        if (isSelfRefreshCall(path)) return Mono.just(exchange);

        MediaType ct = exchange.getRequest().getHeaders().getContentType();
        if (ct == null || !MediaType.APPLICATION_JSON.isCompatibleWith(ct)) return Mono.just(exchange);

        return ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders())
                .bodyToMono(byte[].class).defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    BodyInserter<byte[], ReactiveHttpOutputMessage> inserter = BodyInserters.fromValue(bytes);

                    HttpHeaders headers = new HttpHeaders();
                    headers.putAll(exchange.getRequest().getHeaders());
                    headers.remove(HttpHeaders.CONTENT_LENGTH);
                    headers.remove(HttpHeaders.TRANSFER_ENCODING);

                    CachedBodyOutputMessage cached = new CachedBodyOutputMessage(exchange, headers);
                    return inserter.insert(cached, new BodyInserterContext())
                            .then(Mono.defer(() -> {
                                ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
                                    @Override
                                    public HttpHeaders getHeaders() {
                                        return headers;
                                    }

                                    @Override
                                    public Flux<DataBuffer> getBody() {
                                        return cached.getBody();
                                    }
                                };
                                return Mono.just(exchange.mutate().request(decorator).build());
                            }));
                });
    }

    /**
     * 응답이 401일 때: 1회 리프레시 후 재시도
     */
    private Mono<Void> on401RetryOnce(ServerWebExchange exchange, GatewayFilterChain chain) {
        return cacheBodyIfNeeded(exchange).flatMap(ex -> {
            ServerHttpResponse original = ex.getResponse();

            ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    HttpStatusCode status = getStatusCode();
                    if (status == null) return super.writeWith(body);

                    String path = ex.getRequest().getPath().value();
                    if (isSelfRefreshCall(path)) return super.writeWith(body);

                    boolean unauthorized = (status.value() == HttpStatus.UNAUTHORIZED.value());
                    boolean notRetried = ex.getAttributeOrDefault(ATTR_RETRIED, Boolean.FALSE) == Boolean.FALSE;
                    boolean hasRt = ex.getRequest().getCookies().containsKey(refreshCookieName);

                    if (!(unauthorized && notRetried && hasRt)) return super.writeWith(body);

                    ex.getAttributes().put(ATTR_RETRIED, true);
                    log.debug("[AutoRefresh] 401 on {}, try refresh once", path);

                    return callRefresh(ex).flatMap(outcome -> {
                        if (outcome == null || CmmUtil.nvl(outcome.at()).isBlank()) {
                            log.debug("[AutoRefresh] refresh failed → keep 401");
                            return super.writeWith(body);
                        }
                        applySetCookies(original, outcome.setCookies());
                        ServerWebExchange retryEx = mutateWithNewAT(ex, outcome.at());
                        return chain.filter(retryEx);
                    }).switchIfEmpty(super.writeWith(body));
                }
            };

            return chain.filter(ex.mutate().response(decorated).build());
        });
    }

    // -------------------------------------------------
    // record
    // -------------------------------------------------
    private record RefreshOutcome(String at, List<String> setCookies) {
    }

    // -------------------------------------------------
    // GlobalFilter / Ordered
    // -------------------------------------------------
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // (A) 요청단 선제 리프레시: AT 없고 RT 있으면 /refresh
        if (!isSelfRefreshCall(path) && needPreRefresh(exchange)) {
            return callRefresh(exchange).flatMap(outcome -> {
                if (outcome != null && !CmmUtil.nvl(outcome.at()).isBlank()) {
                    applySetCookies(exchange.getResponse(), outcome.setCookies());
                    ServerWebExchange resumed = mutateWithNewAT(exchange, outcome.at());
                    log.debug("[AutoRefresh] pre-refresh ok for {}", path);
                    return chain.filter(resumed);
                }
                log.debug("[AutoRefresh] pre-refresh skipped/failed for {}", path);
                return on401RetryOnce(exchange, chain);
            });
        }

        // (B) 응답단 보강: 401 가로채 1회 리프레시 후 재시도
        return on401RetryOnce(exchange, chain);
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
