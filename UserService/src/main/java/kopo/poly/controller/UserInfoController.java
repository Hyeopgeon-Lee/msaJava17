package kopo.poly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.MsgDTO;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.IRefreshTokenRedisService;
import kopo.poly.service.IUserInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Tag(name = "로그인된 사용자 API", description = "로그인된 사용자가 호출하는 API")
@Slf4j
@RequestMapping("/user/v1")
@RequiredArgsConstructor
@RestController
public class UserInfoController {

    private final IUserInfoService userInfoService;
    private final IRefreshTokenRedisService refreshService;

    @Value("${jwt.token.access.name}")
    private String accessCookieName;   // 예: jwtAccessToken

    @Value("${jwt.token.refresh.name}")
    private String refreshCookieName;  // 예: jwtRefreshToken (값은 RT-핸들/세션ID)

    @Value("${app.cookies.secure}")
    private boolean cookieSecure;

    @Value("${app.cookies.same-site}")
    private String cookieSameSite;

    @Value("${app.cookies.domain}")
    private String cookieDomain;

    @Value("${app.cookies.http-only}")
    private boolean cookieHttpOnly;

    @Value("${app.cookies.path:}")
    private String cookiePath;

    // =========================================================
    // ✅ 내부 헬퍼 메서드: 쿠키 읽기 / 삭제
    // =========================================================

    /**
     * 요청에서 특정 이름의 쿠키 값을 찾아 반환 (없으면 null)
     */
    private String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    /**
     * 응답에 "삭제용 Set-Cookie" 를 추가 (발급할 때 스펙과 동일하게 맞춤)
     */
    private void clearCookie(HttpServletResponse res, String name) {

        boolean finalSecure = cookieSecure || "none".equalsIgnoreCase(cookieSameSite);

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, "")
                .httpOnly(cookieHttpOnly)
                .secure(finalSecure)
                .path((cookiePath == null || cookiePath.isBlank()) ? "/" : cookiePath)
                .sameSite((cookieSameSite == null || cookieSameSite.isBlank()) ? "Lax" : cookieSameSite)
                .maxAge(0); // 즉시 만료

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain); // 예: .k-bigdata.kr
        }

        res.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    // =========================================================
    // 1) 로그인 사용자 정보 조회
    // =========================================================
    @Operation(
            summary = "회원정보 상세보기",
            description = """
                    게이트웨이가 전달한 Authorization: Bearer <AT>로 인증된 사용자의 정보를 반환합니다.
                    - JWT의 sub(userId), username, roles 클레임을 사용하고,
                      필요하면 서비스 계층에서 DB 조회로 상세 정보를 구성합니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "401", description = "인증되지 않음")
            }
    )
    @PostMapping("userInfo")
    public ResponseEntity<CommonResponse<UserInfoDTO>> userInfo(@AuthenticationPrincipal Jwt jwt) throws Exception {
        log.info("{}.userInfo Start!", getClass().getName());

        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonResponse.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            UserInfoDTO.builder().build()));
        }

        String userId = jwt.getSubject();                 // sub
        String userName = jwt.getClaim("username");       // 선택
        List<String> roles = jwt.getClaim("roles");       // 선택

        UserInfoDTO param = UserInfoDTO.builder().userId(userId).build();
        UserInfoDTO body = Optional.ofNullable(userInfoService.getUserInfo(param))
                .orElseGet(() -> UserInfoDTO.builder()
                        .userId(userId)
                        .userName(userName == null ? "" : userName)
                        .roles(roles == null ? "" : String.join(",", roles))
                        .build());

        log.info("{}.userInfo End!", getClass().getName());
        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", body));
    }

    // =========================================================
    // 2) 현재 기기 로그아웃 (인증 불필요)
    // =========================================================
    @Operation(
            summary = "현재 기기에서 로그아웃",
            description = """
                    요청 쿠키의 REFRESH 핸들(세션ID)을 Redis에서 폐기하고,
                    ACCESS/REFRESH 쿠키를 삭제합니다.
                    """,
            responses = {@ApiResponse(responseCode = "200", description = "OK")}
    )
    @PostMapping("logout/current")
    public ResponseEntity<CommonResponse<MsgDTO>> logoutCurrent(HttpServletRequest req, HttpServletResponse res) {

        log.info("{}.logoutCurrent Start!", getClass().getName());

        // 컨트롤러 내부 readCookie 사용
        String handle = readCookie(req, refreshCookieName);
        if (handle != null && !handle.isBlank()) {
            try {
                refreshService.revokeHandle(handle); // 현재 기기의 RT 세션만 폐기
            } catch (Exception e) {
                log.warn("refresh handle revoke failed", e);
            }
        }

        // AT/RT 쿠키 삭제 (발급과 동일 스펙으로)
        clearCookie(res, accessCookieName);
        clearCookie(res, refreshCookieName);

        MsgDTO dto = MsgDTO.builder()
                .result(1)
                .msg("현재 기기에서 로그아웃되었습니다.")
                .build();

        log.info("{}.logoutCurrent End!", getClass().getName());
        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", dto));
    }

    // =========================================================
    // 3) 모든 기기 로그아웃 (AT 인증 필요)
    // =========================================================
    @Operation(
            summary = "모든 기기에서 로그아웃",
            description = """
                    현재 사용자에 대한 모든 리프레시 세션을 Redis에서 폐기하고,
                    ACCESS/REFRESH 쿠키를 삭제합니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "401", description = "인증되지 않음")
            }
    )
    @PostMapping("logout/all")
    public ResponseEntity<CommonResponse<MsgDTO>> logoutAll(@AuthenticationPrincipal Jwt jwt,
                                                            HttpServletResponse res) {

        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonResponse.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            MsgDTO.builder().result(0).msg("인증 필요").build()));
        }

        String userId = jwt.getSubject();
        try {
            refreshService.revokeAllByUser(userId);
        } catch (Exception e) {
            log.warn("revoke all fail", e);
        }

        // AT/RT 쿠키 삭제
        clearCookie(res, accessCookieName);
        clearCookie(res, refreshCookieName);

        MsgDTO dto = MsgDTO.builder().result(1).msg("모든 기기에서 로그아웃되었습니다.").build();
        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", dto));
    }
}
