package kopo.poly.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class RoutConfig {

    /**
     * Gateway로 접근되는 모든 요청에 대해 URL 요청 분리하기
     */
    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes().route(r -> r.path("/notice/**") // 공지사항
                .uri("lb://NOTICE-SERVICE:12000") // 연결될 서버 주소
        ).route(r -> r.path("/user/**") // 회원정보 확인
                .uri("lb://USER-SERVICE:11000") // 연결될 서버 주소

        ).route(r -> r.path("/login/**") // 로그인 => 로그인이 필요하지 않는 서비스를 별로 URL로 분리
                .uri("lb://USER-SERVICE:11000") // 연결될 서버 주소

        ).route(r -> r.path("/reg/**") // 회원가입 => 로그인이 필요하지 않는 서비스를 별로 URL로 분리
                .uri("lb://USER-SERVICE:11000") // 연결될 서버 주소
        ).build();
    }
}

