package kopo.poly.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 전역 보안 설정
 * <p>
 * 핵심 개념
 * - 이 서비스는 "JWT 리소스 서버"로 동작한다. (세션/서버 상태 X)
 * - 게이트웨이가 ACCESS_TOKEN 쿠키를 Authorization 헤더로 변환하여 전달한다.
 * - RT(리프레시)는 쿠키의 핸들(세션ID)로만 사용하며, UserService는 이를 직접 읽거나
 * 전용 엔드포인트(로그아웃/재발급 등)에서만 사용한다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 비밀번호 해시 함수(BCrypt).
     * - 회원가입/비밀번호 변경 시 해싱에 사용.
     * - 로그인 시엔 스프링 시큐리티가 입력값을 BCrypt로 해시하여 저장된 해시와 비교한다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager
     * - UsernamePasswordAuthenticationToken 인증 처리를 위해 필요.
     * - AuthenticationConfiguration 가 생성해주는 매니저를 그대로 노출.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }

    /**
     * HTTP 보안 체인
     * <p>
     * - cors(): 위 CORS 설정을 Security 필터 체인에 연동
     * - csrf().disable(): 브라우저 폼 기반이 아닌 REST API + JWT 조합이라 CSRF 방어 불필요
     * - sessionManagement(STATELESS): 세션을 생성/사용하지 않는 완전 무상태 아키텍처
     * - authorizeHttpRequests():
     * · 인증 없이 접근 가능한 경로(로그인/리프레시/문서/헬스체크/현재 기기 로그아웃)를 permitAll
     * · 그 외 모든 요청은 인증 필요
     * - oauth2ResourceServer().jwt(): Authorization: Bearer <JWT> 헤더를 검증하는 리소스 서버로 동작
     * <p>
     * 참고(프리플라이트):
     * - CORS 프리플라이트 OPTIONS 전체 허용이 필요하면
     * .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() 를 허용 목록에 추가 가능.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(AbstractHttpConfigurer::disable) // ★ 끔
                // REST API + JWT 조합에서 CSRF 토큰은 사용하지 않음
                .csrf(AbstractHttpConfigurer::disable)
                // 서버 세션 미사용(완전 무상태)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 인가 규칙
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 로그인/리프레시/문서/헬스체크 + "현재 기기 로그아웃"은 인증 없이 허용
                        //  - /auth/** : 로그인, 토큰 재발급 등
                        //  - /user/v1/logout/current : 쿠키(REFRESH 핸들)만으로 현재 기기 로그아웃
                        //  - Swagger/OpenAPI/Actuator : 개발/운영 점검 및 문서 접근
                        .requestMatchers(
                                "/reg/**",
                                "/login/**",
                                "/user/v1/logout/current",
                                "/actuator/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-resources/**"
                        ).permitAll()
                        // 그 외 모든 엔드포인트는 인증 필요(게이트웨이가 Authorization 헤더를 전달)
                        .anyRequest().authenticated()
                )
                // JWT 리소스 서버 설정(Authorization: Bearer <JWT> 검증)
                // Spring Security 6.1+ 에서 jwt()가 deprecated 경고가 보일 수 있으나
                // 커스터마이징이 필요 없으면 아래 기본 설정으로 충분하다.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }
}
