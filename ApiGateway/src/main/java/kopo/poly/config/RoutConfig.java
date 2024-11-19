package kopo.poly.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
//    @Bean
//    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
//        return builder.routes().route(r -> r.path("/notice/v1/**") // 공지사항
//                .uri("lb://NOTICE-SERVICE:12000") // 연결될 서버 주소
//        ).route(r -> r.path("/user/v1/**") // 회원정보 확인
//                .uri("lb://USER-SERVICE:11000") // 연결될 서버 주소
//
//        ).route(r -> r.path("/login/v1/**") // 로그인 => 로그인이 필요하지 않는 서비스를 별로 URL로 분리
//                .uri("lb://USER-SERVICE:11000") // 연결될 서버 주소
//
//        ).route(r -> r.path("/reg/v1/**") // 회원가입 => 로그인이 필요하지 않는 서비스를 별로 URL로 분리
//                .uri("lb://USER-SERVICE:11000") // 연결될 서버 주소
//        ).build();
//    }

    @Value("${NOTICE_SERVICE_IP}")
    private String noticeServiceIP;

    @Value("${NOTICE_SERVICE_PORT}")
    private String noticeServicePort;

    @Value("${USER_SERVICE_IP}")
    private String userServiceIP;

    @Value("${USER_SERVICE_PORT}")
    private String userServicePort;

    /**
     * Kubernetes에 배포한 경우
     */
    @Bean
    public RouteLocator gatewayRoutes(RouteLocatorBuilder builder) {
        return builder.routes().route(r -> r.path("/notice/v1/**") // 공지사항
                .uri("http://" + noticeServiceIP + ":" + noticeServicePort) // 연결될 서버 주소

        ).route(r -> r.path("/user/v1/**") // 회원정보 확인
                .uri("http://" +userServiceIP + ":" + userServicePort) // 연결될 서버 주소

        ).route(r -> r.path("/login/v1/**") // 로그인 => 로그인이 필요하지 않는 서비스를 별로 URL로 분리
                .uri("http://" +userServiceIP + ":" + userServicePort) // 연결될 서버 주소

        ).route(r -> r.path("/reg/v1/**") // 회원가입 => 로그인이 필요하지 않는 서비스를 별로 URL로 분리
                .uri("http://" +userServiceIP + ":" + userServicePort) // 연결될 서버 주소

        ).build();
    }
}

