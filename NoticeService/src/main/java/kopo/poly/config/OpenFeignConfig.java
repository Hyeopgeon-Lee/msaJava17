package kopo.poly.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class OpenFeignConfig {

    /**
     * 인증 헤더(Authorization)를 자동으로 릴레이하는 인터셉터 등록
     * - 기존 Authorization 헤더가 있으면 그대로 사용
     * - 없으면 현재 요청에서 Authorization 헤더를 찾아서 추가
     * - 쿠키 기반 인증 헤더 승격은 필요시 아래 위치에 추가
     */
    @Bean
    public RequestInterceptor authRelayInterceptor() {
        return (RequestTemplate tpl) -> {
            // 이미 Authorization 헤더가 있으면 아무것도 하지 않음
            if (tpl.headers() != null && tpl.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                return;
            }
            // 현재 요청의 ServletRequestAttributes를 가져옴
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return; // 요청이 없으면 아무것도 하지 않음

            HttpServletRequest req = attrs.getRequest();

            // 들어온 요청에 Authorization 헤더가 있으면 그대로 릴레이
            String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(auth)) {
                tpl.header(HttpHeaders.AUTHORIZATION, auth);
            }
        };
    }

    /**
     * Feign 클라이언트의 로그 레벨을 FULL로 설정
     * - 모든 요청/응답의 상세 내용을 로그로 남김
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
