package kopo.poly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.MsgDTO;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.IRefreshTokenRedisService;
import kopo.poly.service.IUserInfoService;
import kopo.poly.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 로그인 사용자 전용 API
 * <p>
 * 구성
 * 1) POST  /user/v1/userInfo
 * - 게이트웨이가 Authorization: Bearer <AT> 헤더를 전달하면,
 * 리소스 서버(JWT)가 인증을 마친 뒤 @AuthenticationPrincipal Jwt 로 주입된다.
 * - JWT의 클레임(sub=userId, username, roles)을 사용하고,
 * 서비스(IUserInfoService)로 DB 상세정보를 보강한다(없으면 클레임 값으로 대체).
 * <p>
 * 2) POST /user/v1/logout/current   (인증 불필요)
 * - "현재 기기"만 로그아웃: 요청 쿠키에 담긴 REFRESH 핸들(세션ID)을 Redis에서 폐기하고,
 * ACCESS/REFRESH 쿠키를 삭제한다.
 * - 게이트웨이는 요청 쿠키 전달/응답의 Set-Cookie(삭제) 포워딩을 보장해야 한다.
 * <p>
 * 3) POST /user/v1/logout/all       (AT 인증 필요)
 * - "모든 기기"에서 로그아웃: 현재 사용자(sub)의 모든 리프레시 세션을 Redis에서 폐기하고,
 * ACCESS/REFRESH 쿠키를 삭제한다.
 * <p>
 * 전제
 * - 게이트웨이는 ACCESS_TOKEN 쿠키를 Authorization 헤더로 변환하여 일반 API에 전달.
 * - REFRESH_TOKEN 쿠키(핸들)는 /auth/refresh 또는 로그아웃 계열에서 그대로 전달(pass-through).
 */
@Tag(name = "로그인된 사용자 API", description = "로그인된 사용자가 호출하는 API")
@Slf4j
@RequestMapping("/user/v1")
@RequiredArgsConstructor
@RestController
public class UserInfoController {

    private final IUserInfoService userInfoService;
    private final IRefreshTokenRedisService refreshService;

    @Value("${jwt.token.access.name}")
    private String accessCookieName;   // 예: ACCESS_TOKEN

    @Value("${jwt.token.refresh.name}")
    private String refreshCookieName;  // 예: REFRESH_TOKEN (값은 RT-핸들/세션ID)

    @Value("${app.cookies.secure:false}")
    private boolean cookieSecure;      // 운영 HTTPS면 true 권장

    // -----------------------------
    // 1) 로그인 사용자 정보 조회
    // -----------------------------
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
            // SecurityConfig에서 인증이 필요한 엔드포인트이므로 일반적으로 여기로 오지 않지만,
            // 방어적으로 401을 반환한다.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonResponse.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            UserInfoDTO.builder().build()));
        }

        // JWT 클레임 → 최소 식별자
        String userId = jwt.getSubject();                 // sub
        String userName = jwt.getClaim("username");       // 선택
        List<String> roles = jwt.getClaim("roles");       // 선택

        // DB 상세 조회 (없으면 클레임 기반으로 기본값 구성)
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

    // -----------------------------
    // 2) 현재 기기 로그아웃 (인증 불필요)
    // -----------------------------
    @Operation(
            summary = "현재 기기에서 로그아웃",
            description = """
                    요청 쿠키의 REFRESH 핸들(세션ID)을 Redis에서 폐기하고,
                    ACCESS/REFRESH 쿠키를 삭제합니다.
                    """,
            responses = {@ApiResponse(responseCode = "200", description = "OK")}
    )
    @PostMapping("logout/current") // 최종 경로: /user/v1/logout/current
    public ResponseEntity<CommonResponse<MsgDTO>> logoutCurrent(HttpServletRequest req, HttpServletResponse res) {

        // 게이트웨이가 요청 쿠키를 그대로 전달해야 함
        String handle = CookieUtil.readCookie(req, refreshCookieName);
        if (handle != null && !handle.isBlank()) {
            try {
                // 현재 기기의 리프레시 세션만 폐기
                refreshService.revokeHandle(handle);
            } catch (Exception e) {
                log.warn("refresh handle revoke failed", e);
            }
        }

        // AT/RT 쿠키 삭제 (Path="/")
        CookieUtil.deleteCookie(res, accessCookieName, cookieSecure);
        CookieUtil.deleteCookie(res, refreshCookieName, cookieSecure);

        MsgDTO dto = MsgDTO.builder()
                .result(1)
                .msg("현재 기기에서 로그아웃되었습니다.")
                .build();

        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", dto));
    }

    // -----------------------------
    // 3) 모든 기기 로그아웃 (AT 인증 필요)
    // -----------------------------
    @Operation(
            summary = "모든 기기에서 로그아웃",
            description = """
                    현재 사용자에 대한 모든 리프레시 세션을 Redis에서 폐기하고,
                    ACCESS/REFRESH 쿠키를 삭제합니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")}, // AT 필요
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "401", description = "인증되지 않음")
            }
    )
    @PostMapping("logout/all") // 최종 경로: /user/v1/logout/all
    public ResponseEntity<CommonResponse<MsgDTO>> logoutAll(@AuthenticationPrincipal Jwt jwt,
                                                            HttpServletResponse res) {

        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonResponse.of(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                            MsgDTO.builder().result(0).msg("인증 필요").build()));
        }

        String userId = jwt.getSubject(); // sub
        try {
            refreshService.revokeAllByUser(userId);
        } catch (Exception e) {
            log.warn("revoke all fail", e);
        }

        CookieUtil.deleteCookie(res, accessCookieName, cookieSecure);
        CookieUtil.deleteCookie(res, refreshCookieName, cookieSecure);

        MsgDTO dto = MsgDTO.builder().result(1).msg("모든 기기에서 로그아웃되었습니다.").build();
        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", dto));
    }
}
