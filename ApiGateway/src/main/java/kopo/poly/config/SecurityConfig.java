package kopo.poly.config;

import kopo.poly.handler.AccessDeniedHandler;
import kopo.poly.handler.LoginServerAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Base64;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // 401 인증 실패 시 처리할 핸들러 (예: 로그인 필요 안내)
    private final LoginServerAuthenticationEntryPoint authenticationEntryPoint;
    // 403 권한 거부 시 처리할 핸들러 (예: 권한 없음 안내)
    private final AccessDeniedHandler accessDeniedHandler;

    // Access Token이 담긴 쿠키 이름 (설정 파일에서 읽어옴)
    @Value("${jwt.token.access.name}")
    private String accessCookieName; // 예) jwtAccessToken

    /**
     * 인증 없이 접근 가능한 경로 목록입니다.
     * - 로그인, 회원가입, 공지 조회, Swagger, Actuator 등
     * - 이 경로들은 인증/인가 필터를 건너뜁니다.
     * - 경로 패턴은 Spring의 PathPattern 문법을 따릅니다.
     *
     * [퍼블릭 경로 추가 위치]
     * -------------------------------------------------------------
     * 새로운 퍼블릭 경로를 추가하려면 아래 배열에 경로 패턴을 이어서 작성하면 됩니다.
     * 예시)
     * "/example/public/**",
     */
    private static final String[] PUBLIC_PATHS = {
            "/login/**",
            "/reg/**",
            "/notice/v1/noticeList",
            "/notice/v1/noticeInfo",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**"
            // [여기에 추가]
    };

    // 경로 패턴 매칭을 위한 파서 객체
    private static final PathPatternParser PP = new PathPatternParser();

    /**
     * 현재 요청 경로가 퍼블릭 경로에 해당하는지 검사합니다.
     * - 요청 경로(path)가 PUBLIC_PATHS 배열의 패턴과 일치하면 true 반환
     * - 예: /login/abc, /notice/v1/noticeList 등
     */
    private static boolean isPublicPath(String path) {
        PathContainer pc = PathContainer.parsePath(path);
        for (String p : PUBLIC_PATHS) {
            PathPattern pattern = PP.parse(p);
            if (pattern.matches(pc)) return true;
        }
        return false;
    }

    // ---- 권한 매핑 ----
    /**
     * JWT 토큰의 roles 클레임을 Spring Security 권한 객체로 변환합니다.
     * - roles가 배열이면 그대로, 문자열이면 쉼표/공백으로 분리
     * - 각 역할 앞에 ROLE_을 붙여서 SimpleGrantedAuthority로 변환
     */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthConverter() {
        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object claim = jwt.getClaim("roles");
            List<String> roles;
            if (claim instanceof Collection<?> c) {
                roles = c.stream().map(String::valueOf).toList();
            } else if (claim instanceof String s) {
                roles = Arrays.stream(s.split("[,\\s]+")).filter(v -> !v.isBlank()).toList();
            } else {
                roles = List.of();
            }
            return roles.stream()
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .map(r -> r.startsWith("ROLE_") ? r : "ROLE_" + r)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        });
        return new ReactiveJwtAuthenticationConverterAdapter(delegate);
    }

    /**
     * JWT 디코더 빈 생성
     * - 설정 파일에서 base64 인코딩된 시크릿 키를 읽어와 디코더에 주입
     * - HmacSHA256 알고리즘 사용
     */
    @Bean
    public ReactiveJwtDecoder jwtDecoder(@Value("${jwt.secret.key}") String secretBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusReactiveJwtDecoder.withSecretKey(key).build();
    }

    /**
     * Spring Security의 핵심 필터 체인 설정
     * -------------------------------------------------------------
     * - CSRF, 폼로그인, HTTP Basic 모두 비활성화
     * - 인증/인가 예외 핸들러 지정
     * - 리소스 서버(JWT) 인증 방식 지정
     * - 경로별 권한 규칙 지정
     *
     * [추가 라우팅 및 권한 규칙 작성 위치]
     * -------------------------------------------------------------
     * 새로운 API 경로에 대한 권한 규칙을 추가하려면 아래 authorizeExchange() 내부에 .pathMatchers()를 이어서 작성하면 됩니다.
     * 예시)
     * .pathMatchers("/example/v1/**").permitAll() // 누구나 접근 가능
     * .pathMatchers("/admin/**").hasRole("ADMIN") // ADMIN 권한 필요
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder decoder) {

        var headerConverter = new ServerBearerTokenAuthenticationConverter();
        var conv = jwtAuthConverter();

        return http
                // CSRF, 폼로그인, HTTP Basic 인증 비활성화
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // 인증 상태 저장하지 않음 (Stateless)
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // 인증/권한 예외 핸들러 지정
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // JWT 기반 리소스 서버 인증 설정
                .oauth2ResourceServer(oauth -> oauth
                        // 퍼블릭 경로는 인증 시도 자체를 스킵(401 방지)
                        .bearerTokenConverter(exchange -> {
                            String path = exchange.getRequest().getPath().value();
                            if (isPublicPath(path)) return Mono.empty();

                            // 쿠키에 토큰 있으면 우선 사용, 없으면 헤더에서 추출
                            HttpCookie cookie = exchange.getRequest().getCookies().getFirst(accessCookieName);
                            if (cookie != null && StringUtils.hasText(cookie.getValue())) {
                                return Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
                            }
                            return headerConverter.convert(exchange);
                        })
                        .jwt(j -> j.jwtDecoder(decoder).jwtAuthenticationConverter(conv)))
                // 경로별 권한 규칙 지정
                .authorizeExchange(authz -> authz
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll() // CORS preflight 허용
                        .pathMatchers(PUBLIC_PATHS).permitAll()               // 퍼블릭 경로 허용
                        .pathMatchers("/user/v1/**").hasRole("USER")        // 회원 API는 USER 권한 필요
                        .pathMatchers("/notice/v1/noticeInsert",
                                "/notice/v1/noticeUpdate",
                                "/notice/v1/noticeDelete").hasRole("USER") // 공지 등록/수정/삭제는 USER 권한 필요
                        // [여기에 추가]
                        .anyExchange().authenticated())                      // 그 외는 인증 필요
                .build();
    }
}
