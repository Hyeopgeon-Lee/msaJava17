package kopo.poly.service;

import jakarta.servlet.http.HttpServletResponse;
import kopo.poly.dto.UserInfoDTO;

/**
 * JWT 발급 및 쿠키 저장 서비스.
 * <p>
 * 설계 목적
 * - 컨트롤러는 구현체가 아닌 이 인터페이스에만 의존한다.
 * - Access 발급과 쿠키 저장, Refresh(=서버 전용 세션 핸들) 관리를 일관되게 유지한다.
 * <p>
 * 토큰/핸들 정책
 * - Access Token(JWT): 짧은 TTL, 리소스 서버 인증용. sub=userId, username, roles 등 클레임 포함.
 * - Refresh "Token": 실제 JWT가 아님. Redis에 저장되는 불투명 세션 핸들(opaque handle)을 의미.
 * - 클라이언트에는 이 핸들을 HttpOnly 쿠키로만 내려보냄(값 자체로는 쓸모 없음).
 * - /auth/refresh에서 이 핸들을 받아 Redis에서 검증/회전하여 새 AT와 새 핸들을 발급.
 * <p>
 * 쿠키 정책
 * - HttpOnly(+ SameSite=Lax 또는 None), Path="/". 운영 환경에서 Secure/Domain 조정.
 * <p>
 * 스레드-세이프티
 * - 구현체는 무상태(stateless) 권장. 키/TTL/쿠키명은 application.yaml로 주입.
 */
public interface IJwtTokenService {

    /**
     * Access Token(JWT) 생성.
     * - 로그인 성공 시 / /auth/refresh 재발급 시 사용.
     */
    String generateAccessToken(UserInfoDTO user);

    /**
     * Refresh "Token" 생성.
     * - 실제로는 Redis에 저장되는 **세션 핸들(opaque handle)** 을 새로 만들고 그 문자열을 반환한다.
     * - 로그인 시 최초 발급에 사용. (재발급 시에는 보통 회전 서비스에서 새 핸들을 만들고 반환)
     */
    String generateRefreshToken(UserInfoDTO user, String userAgent, HttpServletResponse res);

    /**
     * 두 값을 HttpOnly 쿠키로 저장.
     * - accessToken: JWT
     * - refreshToken: **핸들 문자열**(JWT 아님)
     */
    void writeTokensAsCookies(HttpServletResponse res, String accessToken, String refreshToken);

    /**
     * 편의 메서드(로그인 시에만 사용 권장):
     * - Access 발급 → Refresh 핸들 신규 발급 → 두 값을 쿠키로 저장.
     * - /auth/refresh에서는 회전 서비스로 새 핸들을 만든 뒤 writeTokensAsCookies(...)를 직접 호출할 것.
     */
    default void issueTokens(UserInfoDTO user, String userAgent, HttpServletResponse res) {
        String at = generateAccessToken(user);
        String rtHandle = generateRefreshToken(user, userAgent, res); // ← Redis 세션 핸들
        writeTokensAsCookies(res, at, rtHandle);
    }
}
