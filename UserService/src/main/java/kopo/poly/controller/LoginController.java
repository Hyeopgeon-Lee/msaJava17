package kopo.poly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kopo.poly.auth.AuthInfo;
import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.MsgDTO;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.IJwtTokenService;
import kopo.poly.service.IRefreshTokenRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "로그인 관련 API", description = "로그인/로그인 사용자 조회")
@Slf4j
@RequestMapping("/login/v1")
@RequiredArgsConstructor
@RestController
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final IJwtTokenService jwtTokenService;
    private final IRefreshTokenRedisService refreshTokenRedisService;

    @Value("${jwt.token.refresh.name:jwtRefreshToken}")
    private String rtCookieName;

    @Operation(
            summary = "로그인 처리",
            description = """
                    사용자 인증 후 AccessToken(AT)과 Refresh 핸들(RT-handle)을 HttpOnly 쿠키로 발급합니다.
                    - 게이트웨이가 이 Set-Cookie를 그대로 브라우저에 전달합니다.
                    - 이후 일반 API 호출 시 게이트웨이가 ACCESS_TOKEN 쿠키를 읽어 Authorization 헤더로 변환합니다.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @PostMapping("/loginProc")
    public ResponseEntity<CommonResponse<MsgDTO>> loginProc(@RequestBody UserInfoDTO pDTO,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) {
        log.info("{}.loginProc Start!", getClass().getName());
        log.info("pDTO {}", pDTO);

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(pDTO.userId(), pDTO.password())
        );

        AuthInfo principal = (AuthInfo) auth.getPrincipal();
        UserInfoDTO u = principal.userInfoDTO();

        // ✅ UA 바인딩 발급
        String ua = request.getHeader("User-Agent");
        jwtTokenService.issueTokens(u, ua, response);

        MsgDTO dto = MsgDTO.builder().result(1).msg("로그인 성공").build();
        log.info("{}.loginProc End!", getClass().getName());
        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", dto));
    }

    // LoginController.java - loginInfo 수정본
    @Operation(
            summary = "현재 로그인 사용자 조회",
            description = """
                    로그인 여부를 확인하는 엔드포인트입니다.
                    - 로그인된 경우: JWT 클레임에서 userId/username/roles를 추출해 반환
                    - 미로그인인 경우: 200 OK와 함께 빈 사용자 정보(userId="", userName="", roles="") 반환
                    """
            // Swagger에서 자물쇠(보안 요구) 표시를 빼고 싶다면 아래 SecurityRequirement 주석처리
            // ,security = {@SecurityRequirement(name = "bearerAuth")}
    )
    @GetMapping("/loginInfo")
    public ResponseEntity<CommonResponse<Object>> loginInfo(@AuthenticationPrincipal Jwt jwt) {

        log.info("{}.loginInfo Start! jwtPresent={}", getClass().getName(), (jwt != null));

        UserInfoDTO dto;
        if (jwt == null) {
            // 미인증: 빈 유저 정보로 200 OK
            dto = UserInfoDTO.builder()
                    .userId("")
                    .userName("")
                    .roles("")
                    .build();
        } else {
            // 인증됨: JWT에서 정보 추출
            String userId = jwt.getSubject();                 // sub
            String userName = jwt.getClaim("username");       // JwtTokenService에서 넣은 클레임
            List<String> roles = jwt.getClaim("roles");       // List<String>
            String rolesCsv = (roles == null) ? "" : String.join(",", roles);

            dto = UserInfoDTO.builder()
                    .userId(userId)
                    .userName(userName)
                    .roles(rolesCsv)
                    .build();
        }

        log.info("{}.loginInfo End!", getClass().getName());
        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", dto));
    }


    @Operation(
            summary = "AT/RT 재발급(리프레시)",
            description = "HttpOnly 쿠키에 담긴 RT-핸들을 검증하여 새 AccessToken과 새 RT-핸들을 다시 쿠키로 내려줍니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "재발급 성공"),
                    @ApiResponse(responseCode = "401", description = "리프레시 토큰 없음/무효")
            }
    )
    @PostMapping("/refresh")
    public ResponseEntity<CommonResponse<MsgDTO>> refresh(HttpServletRequest request, HttpServletResponse response) {
        log.info("{}.refresh Start!", getClass().getName());

        // 1) 쿠키에서 RT-핸들 추출
        String handle = null;
        if (request.getCookies() != null) {
            for (var c : request.getCookies()) {
                if (rtCookieName.equals(c.getName())) {
                    handle = c.getValue();
                    break;
                }
            }
        }

        // ✅ 로그 포맷 수정
        log.info("rtCookieName={} / handle={}", rtCookieName, handle);

        if (handle == null || handle.isBlank()) {
            MsgDTO err = MsgDTO.builder().result(500).msg("리프레시 토큰이 없습니다.").build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonResponse.of(HttpStatus.UNAUTHORIZED, "CLIENT_ERROR", err));
        }

        // 2) RT-핸들 검증(UA 바인딩)
        String ua = request.getHeader("User-Agent");
        var rec = refreshTokenRedisService.validate(handle, ua); // null이면 무효/만료/UA불일치

        // ✅ 로그 포맷 수정
        log.info("rec={} / ua={}", rec, ua);

        if (rec == null) {
            MsgDTO err = MsgDTO.builder().result(320).msg("유효하지 않은 리프레시 토큰입니다.").build();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CommonResponse.of(HttpStatus.UNAUTHORIZED, "CLIENT_ERROR", err));
        }

        // 3) 사용자 정보 구성
        UserInfoDTO user = UserInfoDTO.builder()
                .userId(rec.userId())
                .userName(rec.userName())
                .roles(String.join(",", rec.roles()))
                .build();

        // 4) 새 AT/RT-핸들을 쿠키로 발급
        // ✅ UA 바인딩으로 재발급
        jwtTokenService.issueTokens(user, ua, response);

        // 5) 기존 핸들은 폐기(세션 회전)
        refreshTokenRedisService.revokeHandle(handle);

        MsgDTO ok = MsgDTO.builder().result(1).msg("토큰이 재발급되었습니다.").build();
        log.info("{}.refresh End!", getClass().getName());
        return ResponseEntity.ok(CommonResponse.of(HttpStatus.OK, "OK", ok));
    }
}
