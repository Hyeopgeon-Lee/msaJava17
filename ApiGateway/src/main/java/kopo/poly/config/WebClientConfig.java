package kopo.poly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClientConfig
 * -------------------------------------------------------------
 * 이 클래스는 Spring WebFlux에서 비동기 HTTP 통신을 위한 WebClient를 빈으로 등록하는 설정 클래스입니다.
 * 대학생이 쉽게 이해할 수 있도록 각 부분에 친절한 설명을 추가했습니다.
 *
 * 주요 역할:
 * - WebClient는 REST API 등 외부 서버와 HTTP 통신을 할 때 사용하는 비동기 클라이언트입니다.
 * - @Bean으로 등록하면 프로젝트 전체에서 주입받아 재사용할 수 있습니다.
 * - WebClient.Builder를 사용하면 커스텀 설정(타임아웃, 헤더 등)도 쉽게 추가할 수 있습니다.
 *
 * [WebClient 커스텀 설정 추가 위치]
 * -------------------------------------------------------------
 * WebClient에 기본 헤더, 타임아웃, 로깅 등 추가 설정이 필요하다면 builder에 옵션을 이어서 작성하면 됩니다.
 * 예시)
 * builder.defaultHeader("Authorization", "Bearer ...")
 *        .baseUrl("http://api.example.com")
 *        .build();
 */
@Configuration
public class WebClientConfig {

    /**
     * WebClient 빈 등록 메서드
     * -------------------------------------------------------------
     * - WebClient.Builder를 주입받아 build()로 WebClient 객체를 생성합니다.
     * - 필요시 builder에 옵션을 추가해 커스텀 WebClient를 만들 수 있습니다.
     *
     * @param builder WebClient 빌더 객체
     * @return WebClient (비동기 HTTP 클라이언트)
     */
    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        // [WebClient 커스텀 설정 추가 위치]
        // builder.defaultHeader("Authorization", "Bearer ...")
        //        .baseUrl("http://api.example.com")
        //        .build();
        return builder.build();
    }
}
