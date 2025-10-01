// SecurityConfig.java - Spring Security 전역 보안 설정 파일
// 이 파일은 UserService의 인증 및 인가 정책을 정의합니다.
// 추가 라우팅 및 퍼블릭 경로를 적용할 위치도 명확히 표시합니다.
package kopo.poly.config;

import lombok.RequiredArgsConstructor; // 생성자 자동 생성(lombok)
import org.springframework.context.annotation.Bean; // Bean 등록 어노테이션
import org.springframework.context.annotation.Configuration; // 설정 클래스임을 명시
import org.springframework.http.HttpMethod; // HTTP 메서드 상수
import org.springframework.security.authentication.AuthenticationManager; // 인증 매니저
import org.springframework.security.config.Customizer; // 시큐리티 커스터마이저
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration; // 인증 설정
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity; // 메서드 단위 보안 활성화
import org.springframework.security.config.annotation.web.builders.HttpSecurity; // HTTP 보안 빌더
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity; // 웹 보안 활성화
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // 시큐리티 설정 커스터마이저
import org.springframework.security.config.http.SessionCreationPolicy; // 세션 정책
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder; // 비밀번호 암호화(BCrypt)
import org.springframework.security.crypto.password.PasswordEncoder; // 비밀번호 인코더 인터페이스
import org.springframework.security.web.SecurityFilterChain; // 시큐리티 필터 체인

/**
 * Spring Security 전역 보안 설정 클래스
 * <p>
 * 핵심 개념:
 * - 이 서비스는 JWT 기반 리소스 서버로 동작하며, 세션을 사용하지 않는 무상태(stateless) 구조입니다.
 * - 게이트웨이에서 ACCESS_TOKEN 쿠키를 Authorization 헤더로 변환하여 전달합니다.
 * - 리프레시 토큰은 쿠키의 핸들(세션ID)로만 사용하며, UserService는 직접 읽지 않고 전용 엔드포인트에서만 사용합니다.
 */
@Configuration // 스프링 설정 클래스임을 명시
@EnableWebSecurity // 웹 보안 활성화
@EnableMethodSecurity // 메서드 단위 보안 활성화
@RequiredArgsConstructor // final 필드 생성자 자동 생성
public class SecurityConfig {

    /**
     * 비밀번호 암호화 함수(BCrypt)
     * - 회원가입/비밀번호 변경 시 비밀번호를 안전하게 암호화합니다.
     * - 로그인 시 입력값을 BCrypt로 해시하여 저장된 해시와 비교합니다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager
     * - UsernamePasswordAuthenticationToken 인증 처리를 위해 필요합니다.
     * - AuthenticationConfiguration이 생성해주는 매니저를 그대로 노출합니다.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * HTTP 보안 필터 체인 설정
     * <p>
     * 주요 설정:
     * - cors(AbstractHttpConfigurer::disable): Security에서 CORS 정책을 적용하지 않음. 즉, 시큐리티 필터 체인에서 CORS 처리를 비활성화함.
     *   만약 CORS를 적용하려면 .cors(Customizer.withDefaults())로 변경해야 함.
     *   현재는 CORS 처리를 별도의 WebMvc 설정 등에서 직접 관리하거나, CORS가 필요 없는 환경임을 의미함.
     * - csrf().disable(): REST API + JWT 조합에서는 CSRF 방어가 불필요하므로 비활성화.
     * - sessionManagement(STATELESS): 세션을 생성/사용하지 않는 완전 무상태 아키텍처로 설정.
     * - authorizeHttpRequests(): 인증 없이 접근 가능한 경로를 permitAll로 허용, 그 외 모든 요청은 인증 필요.
     * - oauth2ResourceServer().jwt(): Authorization: Bearer <JWT> 헤더를 검증하는 리소스 서버로 동작.
     * <p>
     * [추가 라우팅 및 퍼블릭 경로 적용 위치]
     * - 아래 .requestMatchers()에 새로운 엔드포인트(예: /public/**, /extra/** 등)를 추가하면 인증 없이 접근 가능.
     *   예시: .requestMatchers("/public/**").permitAll()
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // cors(AbstractHttpConfigurer::disable): Security에서 CORS 정책을 적용하지 않음
                .cors(AbstractHttpConfigurer::disable)
                // REST API + JWT 조합에서 CSRF 토큰은 사용하지 않습니다.
                .csrf(AbstractHttpConfigurer::disable)
                // 서버 세션을 사용하지 않는 완전 무상태 구조로 설정합니다.
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 인가(Authorization) 규칙을 정의합니다.
                .authorizeHttpRequests(reg -> reg
                        // CORS 프리플라이트 OPTIONS 요청 전체 허용
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 인증 없이 접근 가능한 엔드포인트 목록
                        // 아래에 새로운 퍼블릭 경로를 추가할 수 있습니다.
                        // 예시: .requestMatchers("/public/**").permitAll()
                        .requestMatchers(
                                "/reg/**", // 회원가입 등
                                "/login/**", // 로그인 등
                                "/user/v1/logout/current", // 현재 기기 로그아웃
                                "/actuator/**", // 헬스체크 및 운영 점검
                                "/swagger-ui/**", // Swagger UI
                                "/swagger-ui.html", // Swagger UI HTML
                                "/v3/api-docs/**", // OpenAPI 문서
                                "/swagger-resources/**" // Swagger 리소스
                                // [여기에 추가 라우팅 및 퍼블릭 경로를 추가하세요]
                        ).permitAll()
                        // 그 외 모든 엔드포인트는 인증이 필요합니다.
                        .anyRequest().authenticated()
                )
                // JWT 리소스 서버 설정(Authorization: Bearer <JWT> 검증)
                // Spring Security 6.1+에서 jwt()가 deprecated 경고가 보일 수 있으나
                // 커스터마이징이 필요 없으면 아래 기본 설정으로 충분합니다.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
