package kopo.poly.filter;

import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.factory.rewrite.CachedBodyOutputMessage;
import org.springframework.cloud.gateway.support.BodyInserterContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * 401 에러 발생 시 자동으로 리프레시 토큰을 사용해 액세스 토큰을 재발급하는 필터
 * - Security 필터보다 먼저 실행됨
 * - AT(Access Token) 없고 RT(Refresh Token) 있으면 /refresh API 호출 후 AT 주입
 * - 다운스트림에서 401 발생 시 /refresh 성공하면 1회 재시도
 */
@Slf4j
@RequiredArgsConstructor
@Component
@Order(-102) // SecurityWebFilterChain(-100)보다 먼저 실행됨
public class AutoRefreshOn401Filter implements WebFilter {

    // 액세스 토큰 쿠키 이름 (application.yml에서 설정)
    @Value("${jwt.token.access.name:jwtAccessToken}")
    private String accessCookieName;

    // 리프레시 토큰 쿠키 이름 (application.yml에서 설정)
    @Value("${jwt.token.refresh.name:jwtRefreshToken}")
    private String refreshCookieName;

    // 리프레시 엔드포인트 경로 (application.yml에서 설정)
    @Value("${api.server.user.refresh-endpoint:/login/v1/refresh}")
    private String REFRESH_PATH;

    // 리프레시 API 전체 URL (application.yml에서 설정)
    @Value("${api.server.user.protocol}://${api.server.user.host}:${api.server.user.port}${api.server.user.refresh-endpoint}")
    private String REFRESH_URL;

    // WebClient: 외부 API 호출용
    private final WebClient webClient;

    // 재시도 여부를 저장하는 속성 키
    private static final String ATTR_RETRIED = "X-RT-RETRIED";

    // 리프레시 결과를 담는 record (액세스 토큰, Set-Cookie 헤더)
    private record RefreshOutcome(String at, List<String> setCookies) {}

    // -------------------------- helpers --------------------------

    /**
     * 현재 요청이 리프레시 API 호출인지 확인
     * @param path 요청 경로
     * @return 리프레시 API 호출 여부
     */
    private boolean isSelfRefreshCall(String path) {
        return CmmUtil.nvl(path).startsWith(CmmUtil.nvl(REFRESH_PATH, "/login/v1/refresh"));
    }

    /**
     * Authorization 헤더 또는 AT 쿠키에서 토큰 추출
     * @param exchange 현재 요청
     * @return 토큰 값 (없으면 빈 문자열)
     */
    private String extractAuthOrAtCookie(ServerWebExchange exchange) {
        String auth = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (!auth.isBlank()) return auth;
        var atCookie = exchange.getRequest().getCookies().getFirst(accessCookieName);
        return (atCookie == null) ? "" : "Bearer " + CmmUtil.nvl(atCookie.getValue());
    }

    /**
     * 선제 리프레시가 필요한지 판단 (RT 존재 + AT/Authorization 부재)
     * @param exchange 현재 요청
     * @return 리프레시 필요 여부
     */
    private boolean needPreRefresh(ServerWebExchange exchange) {
        boolean hasRt = exchange.getRequest().getCookies().containsKey(refreshCookieName);
        if (!hasRt) return false;
        return extractAuthOrAtCookie(exchange).isBlank();
    }

    /**
     * 응답에 Set-Cookie 헤더 적용
     * @param resp 응답 객체
     * @param setCookies Set-Cookie 헤더 리스트
     */
    private void applySetCookies(ServerHttpResponse resp, List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return;
        setCookies.forEach(sc -> resp.getHeaders().add(HttpHeaders.SET_COOKIE, sc));
    }

    /**
     * 새로운 AT로 요청 객체 변형
     * @param exchange 기존 요청
     * @param at 새 액세스 토큰
     * @return 변형된 요청
     */
    private ServerWebExchange mutateWithNewAT(ServerWebExchange exchange, String at) {
        String newAt = CmmUtil.nvl(at);

        ServerHttpRequest.Builder rb = exchange.getRequest().mutate()
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + newAt));

        String cookieHeader = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE));
        String atPair = accessCookieName + "=" + newAt;

        // 기존 쿠키에 AT가 없으면 추가
        if (!cookieHeader.contains(accessCookieName + "=")) {
            String merged = cookieHeader.isBlank() ? atPair : cookieHeader + "; " + atPair;
            rb.headers(h -> h.set(HttpHeaders.COOKIE, merged));
        }
        return exchange.mutate().request(rb.build()).build();
    }

    /**
     * Set-Cookie 헤더에서 AT 값 추출
     * @param setCookies Set-Cookie 헤더 리스트
     * @return 액세스 토큰 값
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
     * 리프레시 API 호출 및 결과 반환
     * @param exchange 현재 요청
     * @return 리프레시 결과 (액세스 토큰, Set-Cookie)
     */
    private Mono<RefreshOutcome> callRefresh(ServerWebExchange exchange) {
        String cookieHeader = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE));
        String ua = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT));

        return webClient.post()
                .uri(REFRESH_URL)
                .headers(h -> {
                    // 기존 쿠키와 UA(User-Agent) 헤더 전달
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
                    // 2xx 응답만 처리
                    if (!res.getStatusCode().is2xxSuccessful()) return Mono.empty();
                    List<String> setCookies = res.getHeaders().get(HttpHeaders.SET_COOKIE);
                    String at = extractAt(setCookies);
                    return Mono.justOrEmpty(new RefreshOutcome(at, setCookies));
                });
    }

    /**
     * POST/PUT/PATCH 요청의 body 캐싱 (401 재시도 시 필요)
     * @param exchange 현재 요청
     * @return body가 캐싱된 요청
     */
    private Mono<ServerWebExchange> cacheBodyIfNeeded(ServerWebExchange exchange) {
        HttpMethod m = exchange.getRequest().getMethod();
        boolean needsBody = (m == HttpMethod.POST || m == HttpMethod.PUT || m == HttpMethod.PATCH);
        if (!needsBody) return Mono.just(exchange);

        String path = exchange.getRequest().getPath().value();
        // 리프레시 API 호출은 body 캐싱 불필요
        if (isSelfRefreshCall(path)) return Mono.just(exchange);

        MediaType ct = exchange.getRequest().getHeaders().getContentType();
        if (ct == null || !MediaType.APPLICATION_JSON.isCompatibleWith(ct)) return Mono.just(exchange);

        // body를 byte[]로 읽어서 캐싱
        return ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders())
                .bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    BodyInserter<byte[], ReactiveHttpOutputMessage> inserter = BodyInserters.fromValue(bytes);

                    HttpHeaders headers = new HttpHeaders();
                    headers.putAll(exchange.getRequest().getHeaders());
                    headers.remove(HttpHeaders.CONTENT_LENGTH);
                    headers.remove(HttpHeaders.TRANSFER_ENCODING);

                    var cached = new CachedBodyOutputMessage(exchange, headers);
                    return inserter.insert(cached, new BodyInserterContext())
                            .then(Mono.defer(() -> {
                                ServerHttpRequestDecorator decorator = new ServerHttpRequestDecorator(exchange.getRequest()) {
                                    @Override
                                    public @NonNull HttpHeaders getHeaders() {
                                        return headers;
                                    }

                                    @Override
                                    public @NonNull Flux<DataBuffer> getBody() {
                                        return cached.getBody();
                                    }
                                };
                                return Mono.just(exchange.mutate().request(decorator).build());
                            }));
                });
    }

    /**
     * 401 응답 시 1회 리프레시 후 재시도 처리
     * @param exchange 현재 요청
     * @param chain 필터 체인
     * @return Mono<Void>
     */
    private Mono<Void> on401RetryOnce(ServerWebExchange exchange, WebFilterChain chain) {
        return cacheBodyIfNeeded(exchange).flatMap(ex -> {
            ServerHttpResponse original = ex.getResponse();

            ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
                @Override
                public @NonNull Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
                    HttpStatusCode status = getStatusCode();
                    if (status == null) return super.writeWith(body);

                    String path = ex.getRequest().getPath().value();
                    // 리프레시 API 호출은 재시도 불필요
                    if (isSelfRefreshCall(path)) return super.writeWith(body);

                    boolean unauthorized = (status.value() == HttpStatus.UNAUTHORIZED.value());
                    boolean notRetried = ex.getAttributeOrDefault(ATTR_RETRIED, Boolean.FALSE) == Boolean.FALSE;
                    boolean hasRt = ex.getRequest().getCookies().containsKey(refreshCookieName);

                    // 401 + 미재시도 + RT 존재 시 리프레시 후 재시도
                    if (!(unauthorized && notRetried && hasRt)) return super.writeWith(body);

                    ex.getAttributes().put(ATTR_RETRIED, true);
                    log.debug("[AutoRefresh] 401 on {}, try refresh once", path);

                    return callRefresh(ex)
                            .flatMap(outcome -> {
                                // 리프레시 실패 시 기존 401 응답 유지
                                if (outcome == null || CmmUtil.nvl(outcome.at()).isBlank()) {
                                    log.debug("[AutoRefresh] refresh failed → keep 401");
                                    return Mono.defer(() -> super.writeWith(body));
                                }
                                // 리프레시 성공 시 Set-Cookie 적용 후 재시도
                                applySetCookies(original, outcome.setCookies());
                                ServerWebExchange retryEx = mutateWithNewAT(ex, outcome.at());
                                return chain.filter(retryEx);
                            })
                            .switchIfEmpty(Mono.defer(() -> super.writeWith(body)));
                }
            };

            return chain.filter(ex.mutate().response(decorated).build());
        });
    }

    // -------------------------- WebFilter --------------------------
    /**
     * WebFilter의 진입점
     * (A) Security 이전에 선제 리프레시 시도
     * (B) 401 응답 시 1회 리프레시 후 재시도
     */
    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        log.info("[AutoRefresh] (WebFilter) handling {}", path);

        // (A) Security 이전 선제 리프레시
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

        // (B) 응답 401 가로채기 → 1회 재시도
        return on401RetryOnce(exchange, chain);
    }
}
