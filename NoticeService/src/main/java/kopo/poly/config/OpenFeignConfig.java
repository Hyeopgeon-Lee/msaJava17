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

@Configuration
public class OpenFeignConfig {

    private static final String HEADER_PREFIX = "Bearer ";

    @Value("${jwt.token.access.name}")
    private String accessCookieName;   // 예: jwtAccessToken

    @Bean
    public RequestInterceptor authRelayInterceptor() {
        return (RequestTemplate tpl) -> {
            // 0) 이미 다른 경로(메서드 파라미터 등)로 Authorization이 세팅되어 있으면 그대로 둠
            if (tpl.headers() != null && tpl.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                return;
            }

            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return;

            HttpServletRequest req = attrs.getRequest();

            // 1) 들어온 요청에 Authorization 헤더가 있으면 그대로 릴레이
            String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(auth)) {
                tpl.header(HttpHeaders.AUTHORIZATION, auth);
                return;
            }


            // 2) 없으면 쿠키의 AccessToken을 Authorization으로 승격
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (accessCookieName.equals(c.getName()) && StringUtils.hasText(c.getValue())) {
                        tpl.header(HttpHeaders.AUTHORIZATION, HEADER_PREFIX + c.getValue());
                        return;
                    }
                }
            }
        };
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
