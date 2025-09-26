package kopo.poly.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * CookieToAuthHeaderFilter
 * ---------------------------------------------------------------------
 * 이 필터의 역할과 동작 원리를 대학생 수준에서 쉽게 설명합니다.
 *
 * 목적:
 * - 클라이언트(브라우저 등)가 Access Token(AT)을 "쿠키"로만 보낼 때,
 *   서버(게이트웨이)가 해당 쿠키 값을 읽어서 "Authorization: Bearer <AT>" 헤더로 변환해줍니다.
 * - 이렇게 하면 Spring Security가 토큰 인증을 정상적으로 처리할 수 있습니다.
 *
 * 왜 필요한가?
 * - HttpOnly 쿠키에 토큰이 있으면 자바스크립트로 헤더를 직접 만들 수 없습니다.
 * - 서버가 대신 쿠키 값을 읽어서 헤더로 만들어주면 인증이 잘 동작합니다.
 *
 * 동작 방식:
 * 1) 요청에 이미 Authorization 헤더가 있으면 아무 것도 하지 않고 그대로 넘깁니다.
 *    (모바일앱, 서버-투-서버 등은 직접 헤더를 보낼 수 있으므로 방해하지 않음)
 * 2) Authorization 헤더가 없으면, 설정한 이름의 쿠키를 찾아서 값이 있으면 헤더를 만들어 추가합니다.
 * 3) 쿠키도 헤더도 없으면 그냥 다음 필터로 넘깁니다. (실제 인증 실패 처리는 Security가 함)
 *
 * 보안/설계 참고:
 * - 이 필터는 토큰의 유효성 검사나 만료 체크를 하지 않습니다.
 * - Refresh Token은 다루지 않고, Access Token만 처리합니다.
 * - 쿠키가 CORS/SameSite 정책 때문에 안 붙으면 이 필터가 헤더를 만들 수 없습니다.
 *
 * 테스트 팁:
 * - Postman에서 Authorization 헤더를 직접 넣거나, 쿠키에 AT를 추가해도 동작합니다.
 *
 * 스레드 안전성:
 * - 상태를 저장하지 않는 불변(Stateless) 필터라서 리액티브 환경에서 안전합니다.
 */
@Component
@Order(-101) // Security 인증 필터보다 먼저 실행되도록 순서 지정
public class CookieToAuthHeaderFilter implements WebFilter {

    /**
     * Access Token이 담긴 쿠키 이름을 설정 파일에서 읽어옵니다.
     * 예시: application.yml에 jwt.token.access.name: jwtAccessToken
     */
    @Value("${jwt.token.access.name}")
    private String accessCookieName;

    /**
     * 필터의 핵심 로직입니다.
     * @param exchange 현재 HTTP 요청/응답 정보를 담고 있음
     * @param chain 다음 필터로 넘기는 역할
     * @return Mono<Void> (비동기 처리)
     */
    @Override
    public @NonNull Mono<Void> filter(@NonNull ServerWebExchange exchange, @NonNull WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();

        // [1] 이미 Authorization 헤더가 있으면, 아무 것도 하지 않고 그대로 넘깁니다.
        //     → 클라이언트가 직접 헤더를 보낸 경우를 존중합니다.
        String authorization = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)) {
            return chain.filter(exchange);
        }

        // [2] Authorization 헤더가 없으면, Access Token 쿠키를 찾아서 헤더로 변환합니다.
        //     → 브라우저가 HttpOnly 쿠키로만 토큰을 보낼 때 사용됩니다.
        HttpCookie cookie = req.getCookies().getFirst(accessCookieName);
        if (cookie != null && StringUtils.hasText(cookie.getValue())) {
            // 쿠키 값이 있으면 Authorization 헤더를 만들어서 요청에 추가합니다.
            ServerHttpRequest mutated = req.mutate()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + cookie.getValue())
                    .build();
            // 변경된 요청을 다음 필터로 넘깁니다.
            return chain.filter(exchange.mutate().request(mutated).build());
        }

        // [3] 쿠키도 헤더도 없으면, 아무 것도 하지 않고 다음 필터로 넘깁니다.
        //     → 인증 실패 처리는 Security가 일관되게 처리합니다.
        return chain.filter(exchange);
    }
}
