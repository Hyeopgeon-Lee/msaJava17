package kopo.poly.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RouteConfig
 * -------------------------------------------------------------
 * 이 클래스는 Spring Cloud Gateway의 라우팅 설정을 담당합니다.

 * <p>
 * 주요 역할:
 * - 클라이언트의 요청 경로에 따라 뒷단 서비스(공지, 회원, 로그인 등)로 트래픽을 분배합니다.
 * - 각 서비스별로 필요한 헤더(쿠키, 인증 등)를 제거하거나 유지할 수 있습니다.
 * <p>
 * 라우팅이란?
 * - 사용자가 /notice/v1/** 경로로 요청하면 공지 서비스로,
 *   /user/** 경로로 요청하면 회원 서비스로 연결해주는 역할입니다.
 * - 마치 우체국에서 편지를 주소별로 분류해 각 지역으로 보내는 것과 비슷합니다.
 */
@Slf4j
@Configuration
public class RouteConfig {

    // =========================
    // 서비스별 접속 정보 (설정 파일에서 읽어옴)
    // =========================

    // 공지(Notice) 서버 정보
    @Value("${api.server.notice.protocol:http}")
    private String noticeProtocol; // 공지 서비스의 프로토콜 (예: http)
    @Value("${api.server.notice.url:localhost}")
    private String noticeHost;     // 공지 서비스의 호스트 주소
    @Value("${api.server.notice.port:9002}")
    private String noticePort;     // 공지 서비스의 포트 번호

    // 회원(User) 서버 정보
    @Value("${api.server.user.protocol:http}")
    private String userProtocol;   // 회원 서비스의 프로토콜 (예: http)
    @Value("${api.server.user.url:localhost}")
    private String userHost;       // 회원 서비스의 호스트 주소
    @Value("${api.server.user.port:9001}")
    private String userPort;       // 회원 서비스의 포트 번호

    /**
     * routeLocator
     * -------------------------------------------------------------
     * 실제 라우팅 규칙을 정의하는 메서드입니다.
     * 각 경로별로 어떤 서비스로 연결할지, 어떤 헤더를 제거할지 지정합니다.
     *
     * @param builder 라우트 빌더 객체
     * @return RouteLocator (라우팅 규칙 모음)
     */
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        // 공지/회원 서비스의 실제 접속 주소를 만듭니다.
        final String noticeUri = String.format("%s://%s:%s", noticeProtocol, noticeHost, noticePort); // 예: http://localhost:9002
        final String userUri = String.format("%s://%s:%s", userProtocol, userHost, userPort);         // 예: http://localhost:9001

        // 라우팅 규칙을 정의합니다.
        return builder.routes()

                // [공지 서비스] /notice/v1/** 경로로 들어오면 공지 서버로 연결
                // - 쿠키는 제거(로그인 필요 없음)
                .route("notice-service", r -> r.path("/notice/v1/**")
                        .filters(f -> f.removeRequestHeader("Cookie"))
                        .uri(noticeUri))

                // [회원가입] /reg/** 경로는 퍼블릭(누구나 접근 가능)
                // - 인증 헤더와 쿠키 모두 제거
                .route("reg-service", r -> r.path("/reg/**")
                        .filters(f -> f.removeRequestHeader("Authorization")
                                .removeRequestHeader("Cookie"))
                        .uri(userUri))

                // [로그인] /login/** 경로는 회원 서버로 연결
                // - 쿠키와 Origin 헤더는 유지(로그인 처리에 필요)
                .route("login-service", r -> r.path("/login/**").uri(userUri))

                // [회원 보호 API] /user/** 경로는 회원 서버로 연결
                // - 쿠키와 Origin 헤더는 유지(인증 필요)
                .route("user-service", r -> r.path("/user/**").uri(userUri))

                /*
                 * [추가 라우팅 규칙 작성 위치]
                 * -------------------------------------------------------------
                 * 새로운 서비스나 경로에 대한 라우팅을 추가하려면 아래에 .route()를 이어서 작성하면 됩니다.
                 * 예시)
                 * .route("example-service", r -> r.path("/example/**").uri("http://localhost:9003"))
                 */

                .build();
    }
}
