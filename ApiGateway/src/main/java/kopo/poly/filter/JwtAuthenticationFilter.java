package kopo.poly.filter;

import kopo.poly.dto.TokenDTO;
import kopo.poly.jwt.JwtStatus;
import kopo.poly.jwt.JwtTokenProvider;
import kopo.poly.jwt.JwtTokenType;
import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    public static final String HEADER_PREFIX = "Bearer ";

    @Value("${jwt.token.access.valid.time}")
    private long accessTokenValidTime;

    @Value("${jwt.token.access.name}")
    private String accessTokenName;

    // JWT Token 객체
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 쿠기에 저장된 JWT 토근 삭제할 구조 정의
     *
     * @param tokenName 토큰 이름
     * @return 쿠기 구조
     */
    private ResponseCookie deleteTokenCookie(String tokenName) {

        log.info(this.getClass().getName() + ".deleteTokenCookie Start!");

        log.info("tokenName : " + tokenName);

        ResponseCookie cookie = ResponseCookie.from(tokenName, "")
                .maxAge(0)
                .build();

        log.info(this.getClass().getName() + ".deleteTokenCookie Start!");

        return cookie;
    }

    /**
     * 쿠기에 저장할 JWT 구조 정의
     *
     * @param tokenName      토큰 이름
     * @param tokenValidTime 토큰 유효시간
     * @param token          저장할 토큰
     * @return 쿠기 구조
     */
    private ResponseCookie createTokenCookie(String tokenName, long tokenValidTime, String token) {

        log.info(this.getClass().getName() + ".createTokenCookie Start!");

        log.info("tokenName : " + tokenName);
        log.info("token : " + token);

        ResponseCookie cookie = ResponseCookie.from(tokenName, token)
                .domain("localhost")
                .path("/")
//                .secure(true)
//                .sameSite("None")
                .maxAge(tokenValidTime) // JWT Refresh Token 만료시간 설정
                .httpOnly(true)
                .build();

        log.info(this.getClass().getName() + ".createTokenCookie Start!");

        return cookie;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        log.info(this.getClass().getName() + ".filter Start!");

        log.info("request :"+ request);
        log.info("request :"+ request.getPath());

        // 쿠키에서 Access Token 가져오기
        String accessToken = CmmUtil.nvl(jwtTokenProvider.resolveToken(request, JwtTokenType.ACCESS_TOKEN));

        log.info("accessToken : " + accessToken);

        // Access Token 유효기간 검증하기
        JwtStatus accessTokenStatus = jwtTokenProvider.validateToken(accessToken);

        log.info("accessTokenStatus : " + accessTokenStatus);

        // 유효기간 검증하기
        if (accessTokenStatus == JwtStatus.ACCESS) {

            // 토큰이 유효하면 토큰으로부터 유저 정보를 받아옵니다.
            // 받은 유저 정보 : hglee67 아이디의 권한을 SpringSecurity에 저장함
            Authentication authentication = jwtTokenProvider.getAuthentication(accessToken);

            // Access JWT 토큰을 Bearer 토큰으로 인증헤더에 넣기
//            exchange.getRequest().getHeaders().add(HttpHeaders.AUTHORIZATION, HEADER_PREFIX + accessToken);

            // SecurityContext 에 Authentication 객체를 저장합니다.
            return chain.filter(exchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } else if (accessTokenStatus == JwtStatus.EXPIRED ||
                accessTokenStatus == JwtStatus.DENIED) { // 만료 및 쿠키에서 삭제된 Access Token인 경우

            // Access Token이 만료되면, Refresh Token 유효한지 체크한
            // Refresh Token 확인하기
            String refreshToken = CmmUtil.nvl(jwtTokenProvider.resolveToken(request, JwtTokenType.REFRESH_TOKEN));

            // Refresh Token 유효기간 검증하기
            JwtStatus refreshTokenStatus = jwtTokenProvider.validateToken(refreshToken);

            log.info("refreshTokenStatus : " + refreshTokenStatus);

            // Refresh Token이 유효하면, Access Token 재발급
            if (refreshTokenStatus == JwtStatus.ACCESS) {

                // Refresh Token에 저장된 정보 가져오기
                TokenDTO rDTO = Optional.ofNullable(jwtTokenProvider.getTokenInfo(refreshToken))
                        .orElseGet(TokenDTO::new);

                String userId = CmmUtil.nvl(rDTO.getUserId()); // 회원 아이디
                String userRoles = CmmUtil.nvl(rDTO.getRole()); // 회원 권한

                log.info("refreshToken userId : " + userId);
                log.info("refreshToken userRoles : " + userRoles);

                // Access Token 재 발급
                String reAccessToken = jwtTokenProvider.createToken(userId, userRoles);

                // 만약, 기존 존재하는 Access Token있다면, 삭제
                response.addCookie(this.deleteTokenCookie(accessTokenName));

                // 재발급된 Access Token을 쿠키에 저장함
                response.addCookie(this.createTokenCookie(accessTokenName, accessTokenValidTime, reAccessToken));

                // Access JWT 토큰을 Bearer 토큰으로 인증헤더에 넣기
//                exchange.getRequest().getHeaders().add(HttpHeaders.AUTHORIZATION, HEADER_PREFIX + accessToken);
                
                // 토큰이 유효하면 토큰으로부터 유저 정보를 받아옵니다.
                // 받은 유저 정보 : hglee67 아이디의 권한을 SpringSecurity에 저장함
                Authentication authentication = jwtTokenProvider.getAuthentication(reAccessToken);

                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

            } else if (refreshTokenStatus == JwtStatus.EXPIRED) {
                log.info("Refresh Token 만료 - 스프링 시큐리티가 로그인 페이지로 이동 시킴");

            } else {
                log.info("Refresh Token 오류 - 스프링 시큐리티가 로그인 페이지로 이동 시킴");

            }

        }

        log.info(this.getClass().getName() + ".filter End!");

        return chain.filter(exchange);
    }
}
