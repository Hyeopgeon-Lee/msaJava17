package kopo.poly.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * OpenFeign 통신 시 인증 토큰을 자동으로 헤더에 추가해주는 설정 클래스입니다.
 * - 클라이언트 요청의 Authorization 헤더 또는 JWT 쿠키 값을 Feign 요청에 자동으로 전달합니다.
 * - Feign의 상세 로그 레벨도 설정합니다.
 */
@Configuration
public class OpenFeignConfig {

    // Authorization 헤더에 붙일 접두사(Bearer )
    private static final String HEADER_PREFIX = "Bearer ";

    // application.yml에서 JWT AccessToken 쿠키 이름을 주입받음
    @Value("${jwt.token.access.name}")
    private String accessCookieName;   // 예: jwtAccessToken

    /**
     * Feign 요청마다 실행되는 인터셉터를 등록합니다.
     * - 클라이언트의 인증 정보를 Feign 요청에 자동으로 전달합니다.
     * - 우선순위: 1) 기존 Authorization 헤더가 있으면 그대로 사용
     * 2) 없으면 JWT AccessToken 쿠키 값을 Authorization 헤더로 승격
     */
    @Bean
    public RequestInterceptor authRelayInterceptor() {
        return (RequestTemplate tpl) -> {
            // 0) 이미 다른 경로(메서드 파라미터 등)로 Authorization이 세팅되어 있으면 그대로 둡니다.
            if (tpl.headers() != null && tpl.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                return;
            }

            // 현재 요청의 ServletRequestAttributes를 가져옵니다.
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return; // 요청이 없으면 아무것도 하지 않음

            HttpServletRequest req = attrs.getRequest();

            // 1) 들어온 요청에 Authorization 헤더가 있으면 그대로 릴레이합니다.
            String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(auth)) {
                tpl.header(HttpHeaders.AUTHORIZATION, auth);
                return;
            }

            // 2) Authorization 헤더가 없으면, 쿠키에서 AccessToken을 찾아 Authorization 헤더로 승격합니다.
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    // 쿠키 이름이 accessCookieName과 일치하고 값이 있으면 Authorization 헤더로 추가
                    if (accessCookieName.equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                        tpl.header(HttpHeaders.AUTHORIZATION, HEADER_PREFIX + c.getValue());
                        return;
                    }
                }
            }
        };
    }

    /**
     * Feign 클라이언트의 로그 레벨을 FULL로 설정합니다.
     * - 모든 요청/응답의 상세 내용을 로그로 남깁니다.
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
