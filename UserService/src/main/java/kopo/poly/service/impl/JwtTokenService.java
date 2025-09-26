package kopo.poly.service.impl;

import jakarta.servlet.http.HttpServletResponse;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.IJwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Access 토큰(JWT) 발급 + 쿠키 저장.
 * Refresh는 JWT가 아니라 Redis에 저장되는 "세션 핸들(opaque handle)"을 발급한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenService implements IJwtTokenService {

    // ====== 상수 ======
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access"; // refresh JWT는 더이상 만들지 않으므로 access만 사용

    // ====== 주입 객체 ======
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRedisService refreshTokenRedisService; // ★ RT 핸들 저장/회전 담당

    // ====== 설정 값 ======
    @Value("${jwt.token.creator}")
    private String issuer;
    @Value("${jwt.token.access.valid.time}")
    private long accessTtlSec;
    @Value("${jwt.token.refresh.valid.time}")
    private long refreshTtlSec;
    @Value("${jwt.token.access.name}")
    private String accessCookie;
    @Value("${jwt.token.refresh.name}")
    private String refreshCookie;

    // 운영/개발에 맞추어 조절하고 싶으면 프로퍼티로 뺄 수 있습니다.
    @Value("${app.cookies.secure}")
    private boolean cookieSecure;
    @Value("${app.cookies.same-site}")
    private String cookieSameSite;

    // ====== 내부 로직 ======
    private String encodeAccess(UserInfoDTO user, long ttlSec) {
        Instant now = Instant.now();
        List<String> roles = splitRoles(user.roles());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSec))
                .subject(user.userId())
                .claim(CLAIM_USERNAME, user.userName())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLES, roles)
                .build();

        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build(); // HS256
        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    private static List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) return List.of("USER");
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // ====== IJwtTokenService 구현 ======

    /**
     * Access Token(JWT) 발급
     */
    @Override
    public String generateAccessToken(UserInfoDTO user) {
        log.info("{}.generateAccessToken Start!", getClass().getName());
        return encodeAccess(user, accessTtlSec);
    }

    /**
     * Refresh "Token" 발급
     * - 실제 JWT를 만들지 않고, Redis에 저장되는 세션 핸들(opaque handle)을 새로 생성하여 반환한다.
     * - 컨트롤러/게이트웨이는 이 값을 기존 RT처럼 쿠키로만 주고받는다(값 자체는 의미 없음).
     */
    @Override
    public String generateRefreshToken(UserInfoDTO user, String userAgent, HttpServletResponse res) {
        log.info("{}.generateRefreshToken Start! ua='{}'", getClass().getName(), userAgent);

        // ★ UA 바인딩해서 RT 세션 핸들 발급
        String handle = refreshTokenRedisService.issueHandle(user, refreshTtlSec, userAgent);

        log.info("[RT] issued handle={} ttlSec={}", handle, refreshTtlSec);
        return handle;
    }


    /**
     * AT(JWT) + RT 핸들을 HttpOnly 쿠키로 설정
     */
    @Override
    public void writeTokensAsCookies(HttpServletResponse res, String accessToken, String refreshHandle) {
        log.info("{}.writeTokensAsCookies Start!", getClass().getName());

        ResponseCookie at = ResponseCookie.from(accessCookie, accessToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSameSite)
                .maxAge(accessTtlSec)
                .build();

        // ★ 쿠키에는 진짜 RT가 아닌 "핸들"을 넣습니다.
        ResponseCookie rt = ResponseCookie.from(refreshCookie, refreshHandle)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSameSite)
                .maxAge(refreshTtlSec)
                .build();

        res.addHeader("Set-Cookie", at.toString());
        res.addHeader("Set-Cookie", rt.toString());

        log.info("{}.writeTokensAsCookies End!", getClass().getName());
    }
}
