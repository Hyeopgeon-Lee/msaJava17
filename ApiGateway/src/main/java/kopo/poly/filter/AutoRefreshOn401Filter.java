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

/**
 * AutoRefreshOn401Filter
 * -------------------------------------------------------------------------------------------------
 * 목적
 *  - 게이트웨이 레벨에서 Access Token(AT) 만료로 401이 발생했을 때,
 *    Refresh Token(RT)로 자동 /refresh를 호출하여 AT를 재발급 받은 뒤, 원 요청을 1회 재시도합니다.
 *
 * 동작 흐름(요약)
 *  (A) 요청 전 선제 리프레시(pre-refresh):
 *      - 요청에 Authorization 헤더/AT쿠키가 없고, RT쿠키가 있으면 /refresh를 먼저 호출하여 AT를 확보합니다.
 *      - 성공 시 새 AT를 Authorization/쿠키 둘 다에 주입하여 원 요청을 진행합니다.
 *
 *  (B) 응답 가로채기:
 *      - 원 요청 응답이 401(UNAUTHORIZED)일 경우에 한해, /refresh를 1번 호출하고 성공하면 원 요청을 1회 재시도합니다.
 *      - 무한루프 방지를 위해 /refresh 자체 호출이거나 이미 1회 재시도한 경우는 제외합니다.
 *
 * 주의사항
 *  - 본 필터는 "쿠키 기반 인증"을 가정합니다. RT는 HttpOnly 쿠키로, AT는 Authorization 헤더 또는 쿠키로 전송됩니다.
 *  - 보안상 프론트에서 userId 등을 신뢰하지 않고, 서버 측 토큰만 신뢰하는 구성을 권장합니다.
 *  - POST/PUT/PATCH 재시도를 위해 JSON 요청 바디를 캐시합니다(비멱등 주의). 멱등이 보장되지 않는 API에서는 재시도에 유의하세요.
 *  - WebFlux 환경에서 논블로킹으로 동작하며, WebClient는 빈으로 주입되어 커넥션 풀을 재사용합니다.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class AutoRefreshOn401Filter implements GlobalFilter, Ordered {

    // =============================================================================================
    // 설정값(쿠키 이름) — application.yml의 jwt.token.*와 매핑
    // =============================================================================================

    /** Access Token이 담긴 쿠키 이름 (예: jwtAccessToken) */
    @Value("${jwt.token.access.name:jwtAccessToken}")
    private String accessCookieName;

    /** Refresh Token이 담긴 쿠키 이름 (예: jwtRefreshToken) */
    @Value("${jwt.token.refresh.name:jwtRefreshToken}")
    private String refreshCookieName;

    // =============================================================================================
    // 설정값(리프레시 엔드포인트) — 게이트웨이가 호출할 사용자 API의 /refresh URL
    // =============================================================================================

    /**
     * 무한루프 방지를 위해 "현재 요청이 /refresh 인지"를 판별하는 기준 경로.
     *  - 기본값: /login/v1/refresh
     *  - 프리픽스/서브패스가 붙을 수 있으므로 "startsWith" 검사합니다.
     */
    @Value("${api.server.user.refresh-endpoint:/login/v1/refresh}")
    private String REFRESH_PATH;

    /**
     * 실제로 호출할 절대 URL (예: http://localhost:9001/login/v1/refresh)
     *  - 게이트웨이에서 백엔드 사용자 API로 직접 호출합니다.
     *  - 구성: protocol + host + port + refresh-endpoint
     */
    @Value("${api.server.user.protocol}://${api.server.user.host}:${api.server.user.port}${api.server.user.refresh-endpoint}")
    private String REFRESH_URL;

    /** WebClient: 논블로킹 HTTP 클라이언트 (빈으로 주입) */
    private final WebClient webClient;

    /** 재시도 여부 플래그를 교환 컨텍스트에 저장할 때 쓰는 키 (무한 루프/중복 재시도 방지) */
    private static final String ATTR_RETRIED = "X-RT-RETRIED";

    // =============================================================================================
    // 헬퍼 메서드 (작은 단위 책임/설명 중심)
    // =============================================================================================

    /**
     * 현재 요청 경로가 /refresh(자기 자신)인지 검사.
     * /refresh 처리 중에 다시 /refresh를 호출하면 무한 루프가 발생할 수 있으므로 이 검사는 매우 중요합니다.
     */
    private boolean isSelfRefreshCall(String path) {
        return CmmUtil.nvl(path).startsWith(CmmUtil.nvl(REFRESH_PATH, "/login/v1/refresh"));
    }

    /**
     * Authorization 헤더가 있으면 그대로 사용하고, 없으면 AT 쿠키를 읽어 Bearer 토큰으로 만들어 반환합니다.
     * (게이트웨이가 다운스트림으로 전달할 Authorization을 구성할 때 사용)
     */
    private String extractAuthOrAtCookie(ServerWebExchange exchange) {
        String auth = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (!auth.isBlank()) return auth;

        var atCookie = exchange.getRequest().getCookies().getFirst(accessCookieName);
        return (atCookie == null) ? "" : "Bearer " + CmmUtil.nvl(atCookie.getValue());
    }

    /**
     * 선제 리프레시 필요 여부 판단:
     *  - RT 쿠키가 있고,
     *  - Authorization 헤더 또는 AT 쿠키가 "없을 때"만 선제적으로 /refresh를 시도합니다.
     *  => 즉, "AT가 전혀 없는 최초 요청" 시 사용자 경험을 개선하기 위한 로직입니다.
     */
    private boolean needPreRefresh(ServerWebExchange exchange) {
        boolean hasRt = exchange.getRequest().getCookies().containsKey(refreshCookieName);
        if (!hasRt) return false;
        return extractAuthOrAtCookie(exchange).isBlank();
    }

    /**
     * 백엔드(/refresh) 응답 헤더의 Set-Cookie를 클라이언트로 그대로 전달합니다.
     *  - 새 AT/RT가 재발급되는 경우, 브라우저 쿠키 갱신을 위해 필수입니다.
     */
    private void applySetCookies(ServerHttpResponse resp, List<String> setCookies) {
        if (setCookies == null || setCookies.isEmpty()) return;
        setCookies.forEach(sc -> resp.getHeaders().add(HttpHeaders.SET_COOKIE, sc));
    }

    /**
     * 새로 발급받은 AT를 교환 객체(ServerWebExchange)에 주입:
     *  - Authorization 헤더에 "Bearer {AT}" 설정
     *  - 요청의 Cookie 헤더에도 AT 쿠키가 없다면 추가(백엔드가 쿠키를 읽는 구조 대비)
     *  - 이 메서드는 "원 요청을 다시 체인에 태울" 때 사용합니다.
     */
    private ServerWebExchange mutateWithNewAT(ServerWebExchange exchange, String at) {
        String newAt = CmmUtil.nvl(at);

        // (1) Authorization 주입
        ServerHttpRequest.Builder rb = exchange.getRequest().mutate()
                .headers(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + newAt));

        // (2) Cookie 헤더 병합(AT 쿠키가 없을 때만 추가)
        String cookieHeader = CmmUtil.nvl(exchange.getRequest().getHeaders().getFirst(HttpHeaders.COOKIE));
        String atPair = accessCookieName + "=" + newAt;

        if (!cookieHeader.contains(accessCookieName + "=")) {
            String merged = cookieHeader.isBlank() ? atPair : cookieHeader + "; " + atPair;
            rb.headers(h -> h.set(HttpHeaders.COOKIE, merged));
        }

        return exchange.mutate().request(rb.build()).build();
    }

    /**
     * /refresh 응답의 Set-Cookie 목록에서 AT 값만 추출합니다.
     *  - 쿠키 문자열은 "name=value; Path=/; HttpOnly; ..." 형태이므로,
     *    name= 다음부터 세미콜론(;) 이전까지를 값으로 간주합니다.
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
     * /refresh 호출:
     *  - 현재 요청의 Cookie/UA 헤더를 그대로 전달하여 백엔드 사용자 API의 리프레시 엔드포인트를 호출합니다.
     *  - 2xx가 아니면 실패로 간주하고 빈 Mono 반환
     *  - 성공 시 Set-Cookie 전체와 추출된 AT를 함께 결과로 전달
     *  - 타임아웃: 3초 (지연 방지)
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
                    // 바디 없음 표시 (일부 서버는 Content-Length 0 없으면 415/400을 낼 수도 있음)
                    h.setContentLength(0);
                })
                .retrieve()
                .toEntity(byte[].class)
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(e -> {
                    // 네트워크 오류/타임아웃 등은 리프레시 실패로 보고 원래 응답을 유지합니다.
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
     * (POST/PUT/PATCH + JSON) 요청에 대해 재시도를 가능하게 하려면,
     * 바디를 캐시해야 합니다. (리액티브 스트림은 한 번 소비하면 재사용 불가)
     *
     * - /refresh 자체 호출은 바디 캐시가 불필요하므로 스킵합니다.
     * - Content-Type이 application/json 호환일 때만 캐시합니다.
     * - 캐시 후 ServerHttpRequestDecorator로 바디를 다시 제공하는 요청으로 교체합니다.
     */
    private Mono<ServerWebExchange> cacheBodyIfNeeded(ServerWebExchange exchange) {
        HttpMethod m = exchange.getRequest().getMethod();
        boolean needsBody = (m == HttpMethod.POST || m == HttpMethod.PUT || m == HttpMethod.PATCH);
        if (!needsBody) return Mono.just(exchange);

        String path = exchange.getRequest().getPath().value();
        if (isSelfRefreshCall(path)) return Mono.just(exchange);

        MediaType ct = exchange.getRequest().getHeaders().getContentType();
        if (ct == null || !MediaType.APPLICATION_JSON.isCompatibleWith(ct)) return Mono.just(exchange);

        // ServerRequest를 사용하면 바디를 Mono<byte[]>로 쉽게 읽을 수 있습니다.
        return ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders())
                .bodyToMono(byte[].class)
                .defaultIfEmpty(new byte[0])
                .flatMap(bytes -> {
                    // 1) 읽어온 바디를 캐시 객체(CachedBodyOutputMessage)에 써놓고
                    BodyInserter<byte[], ReactiveHttpOutputMessage> inserter = BodyInserters.fromValue(bytes);

                    HttpHeaders headers = new HttpHeaders();
                    headers.putAll(exchange.getRequest().getHeaders());
                    // 재전송 시 길이/전송인코딩 헤더는 제거 (Netty가 다시 계산)
                    headers.remove(HttpHeaders.CONTENT_LENGTH);
                    headers.remove(HttpHeaders.TRANSFER_ENCODING);

                    CachedBodyOutputMessage cached = new CachedBodyOutputMessage(exchange, headers);
                    return inserter.insert(cached, new BodyInserterContext())
                            .then(Mono.defer(() -> {
                                // 2) 데코레이터로 바디를 재공급할 수 있는 요청으로 교체
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
     * 응답 가로채기:
     *  - 원 요청 응답이 401이고,
     *  - 아직 재시도하지 않았으며,
     *  - RT 쿠키가 존재한다면,
     *    → /refresh를 호출하여 성공 시 새 AT를 주입하고 원 요청을 1회 재시도합니다.
     *
     *  - /refresh 자체 호출이거나, 이미 재시도를 수행한 경우는 그대로 원 응답을 흘려보냅니다.
     */
    private Mono<Void> on401RetryOnce(ServerWebExchange exchange, GatewayFilterChain chain) {
        return cacheBodyIfNeeded(exchange).flatMap(ex -> {
            ServerHttpResponse original = ex.getResponse();

            // 응답을 가로채기 위해 데코레이터 사용
            ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(original) {
                @Override
                public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                    HttpStatusCode status = getStatusCode();
                    if (status == null) return super.writeWith(body);

                    String path = ex.getRequest().getPath().value();
                    if (isSelfRefreshCall(path)) return super.writeWith(body); // /refresh 응답은 건드리지 않음

                    boolean unauthorized = (status.value() == HttpStatus.UNAUTHORIZED.value());
                    boolean notRetried = ex.getAttributeOrDefault(ATTR_RETRIED, Boolean.FALSE) == Boolean.FALSE;
                    boolean hasRt = ex.getRequest().getCookies().containsKey(refreshCookieName);

                    // 조건 불충족 시 그대로 통과
                    if (!(unauthorized && notRetried && hasRt)) return super.writeWith(body);

                    // 중복 재시도 방지 플래그 설정
                    ex.getAttributes().put(ATTR_RETRIED, true);
                    log.debug("[AutoRefresh] 401 on {}, try refresh once", path);

                    // /refresh 호출 → 성공 시 Set-Cookie 반영 및 AT 주입 → 체인 재호출(재시도)
                    return callRefresh(ex).flatMap(outcome -> {
                        if (outcome == null || CmmUtil.nvl(outcome.at()).isBlank()) {
                            log.debug("[AutoRefresh] refresh failed → keep 401");
                            return super.writeWith(body); // 실패 시 원 401 유지
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

    // =============================================================================================
    // 내부 결과 레코드 — /refresh 성공 시 AT와 Set-Cookie를 함께 전달
    // =============================================================================================
    private record RefreshOutcome(String at, List<String> setCookies) {}

    // =============================================================================================
    // GlobalFilter / Ordered 구현
    // =============================================================================================

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // (A) 요청단 선제 리프레시:
        //     AT(Authorization/쿠키)가 없고 RT가 있으면 먼저 /refresh 시도 → 성공 시 AT 주입 후 원 요청 진행
        if (!isSelfRefreshCall(path) && needPreRefresh(exchange)) {
            return callRefresh(exchange).flatMap(outcome -> {
                if (outcome != null && !CmmUtil.nvl(outcome.at()).isBlank()) {
                    applySetCookies(exchange.getResponse(), outcome.setCookies());
                    ServerWebExchange resumed = mutateWithNewAT(exchange, outcome.at());
                    log.debug("[AutoRefresh] pre-refresh ok for {}", path);
                    return chain.filter(resumed);
                }
                log.debug("[AutoRefresh] pre-refresh skipped/failed for {}", path);
                // 선제 리프레시 실패 시에도, 응답단에서 401을 보고 한 번 더 시도(on401RetryOnce)합니다.
                return on401RetryOnce(exchange, chain);
            });
        }

        // (B) 응답단 보강:
        //     평상시에는 그냥 체인 진행. 만약 응답이 401이면 on401RetryOnce에서 가로채 1회 재시도.
        return on401RetryOnce(exchange, chain);
    }

    /**
     * 필터 실행 순서: 숫자가 낮을수록 먼저 실행
     *  - -200은 보통의 로깅/추적 필터(-1, 0대)보다 앞에서 동작시키고자 할 때 사용.
     *  - 환경에 따라 Spring Security 관련 필터/라우팅 필터와의 순서를 고려하여 조정하세요.
     */
    @Override
    public int getOrder() {
        return -200;
    }
}
