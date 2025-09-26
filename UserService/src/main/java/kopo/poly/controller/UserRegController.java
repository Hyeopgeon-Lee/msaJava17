package kopo.poly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import kopo.poly.auth.UserRole;
import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.MsgDTO;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.IUserInfoService;
import kopo.poly.util.CmmUtil;
import kopo.poly.util.EncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원가입 API", description = "회원가입 요청을 처리합니다.")
@Slf4j
@RequestMapping("/reg/v1")
@RequiredArgsConstructor
@RestController
public class UserRegController {

    private final IUserInfoService userInfoService;
    private final PasswordEncoder passwordEncoder;

    @Operation(
            summary = "회원가입",
            description = """
                    JSON 바디로 회원 정보를 전달받아 저장합니다.
                    - 비밀번호는 PasswordEncoder(BCrypt)로 해시 저장
                    - 이메일은 EncryptUtil(AES/CBC)로 암호화 저장(기존 호환)
                    - 기본 권한: USER
                    요청 예시:
                    {
                      "userId": "test01",
                      "userName": "홍길동",
                      "password": "pass1234!",
                      "email": "test01@example.com",
                      "addr1": "서울시 ....",
                      "addr2": "101동 1001호"
                    }
                    """,
            responses = {@ApiResponse(responseCode = "200", description = "OK")}
    )
    @PostMapping("insertUserInfo")
    public ResponseEntity<CommonResponse<MsgDTO>> insertUserInfo(@RequestBody UserInfoDTO pDTO) {

        log.info("UserRegController.insertUserInfo Start");

        int result;
        String msg;

        try {
            // ---- 1) 입력 정규화 & 필수값 검증 ----
            final String userId = CmmUtil.nvl(pDTO.userId()).trim();
            final String userName = CmmUtil.nvl(pDTO.userName()).trim();
            final String rawPw = CmmUtil.nvl(pDTO.password());
            final String emailRaw = CmmUtil.nvl(pDTO.email()).trim();
            final String addr1 = CmmUtil.nvl(pDTO.addr1()).trim();
            final String addr2 = CmmUtil.nvl(pDTO.addr2()).trim();

            // ---- 2) 민감정보는 로그에 남기지 않음 ----
            log.info("userId: {}", userId);
            log.info("userName: {}", userName);
            log.info("addr1/addr2: {}/{}", addr1, addr2);

            // ---- 3) 저장 DTO 구성 (비번 해시, 이메일 암호화, 기본 권한) ----
            UserInfoDTO toSave = UserInfoDTO.builder()
                    .userId(userId)
                    .userName(userName)
                    .password(passwordEncoder.encode(rawPw)) // BCrypt 해시
                    .email(EncryptUtil.encAES128CBC(emailRaw)) // AES/CBC 암호화(기존 호환)
                    .addr1(addr1)
                    .addr2(addr2)
                    .roles(UserRole.USER.getValue()) // 기본 권한
                    .build();

            // ---- 4) 회원가입 실행 ----
            result = userInfoService.insertUserInfo(toSave);

            if (result == 1) {
                msg = "회원가입되었습니다.";
            } else if (result == 2) {
                msg = "이미 가입된 아이디입니다.";
            } else {
                msg = "오류로 인해 회원가입이 실패하였습니다.";
            }

        } catch (Exception e) {
            log.error("insertUserInfo error", e);
            result = 0;
            msg = "서버 오류로 회원가입에 실패했습니다.";
        }

        log.info("UserRegController.insertUserInfo End");

        return ResponseEntity.ok(
                CommonResponse.of(HttpStatus.OK, "OK", MsgDTO.builder().result(result).msg(msg).build())
        );
    }
}
