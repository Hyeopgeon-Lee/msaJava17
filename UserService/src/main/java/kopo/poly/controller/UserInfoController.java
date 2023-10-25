package kopo.poly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import kopo.poly.auth.JwtTokenProvider;
import kopo.poly.auth.JwtTokenType;
import kopo.poly.dto.TokenDTO;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.IUserInfoSsService;
import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;


@CrossOrigin(origins = {"http://localhost:13000", "http://localhost:14000"},
        allowedHeaders = {"POST, GET"},
        allowCredentials = "true")
@Tag(name = "로그인된 사용자들이 접근하는 서비스", description = "로그인된 사용자들이 접근하는 서비스 API")
@Slf4j
@RequestMapping(value = "/user")
@RequiredArgsConstructor
@RestController
public class UserInfoController {

    // JWT 객체
    private final JwtTokenProvider jwtTokenProvider;

    // 회원 서비스
    private final IUserInfoSsService userInfoService;

    /**
     * JWT Access Token으로부터 user_id 가져오기
     */
    private String getUserIdFromToken(HttpServletRequest request) {

        // 로그 찍기(추후 찍은 로그를 통해 이 함수에 접근했는지 파악하기 용이하다.)
        log.info(this.getClass().getName() + ".getUserIdFromToken Start!");

        //JWT Access 토큰 가져오기
        String jwtAccessToken = CmmUtil.nvl(jwtTokenProvider.resolveToken(request, JwtTokenType.ACCESS_TOKEN));
        log.info("jwtAccessToken : " + jwtAccessToken);

        TokenDTO dto = Optional.ofNullable(jwtTokenProvider.getTokenInfo(jwtAccessToken)).orElseGet(TokenDTO::new);

        return CmmUtil.nvl(dto.getUserId());

    }

    @Operation(summary = "회원정보 상세보기 API", description = "회원정보 상세보기 API",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Page Not Found!"),
            }
    )
    @PostMapping(value = "userInfo")
    public UserInfoDTO userInfo(HttpServletRequest request) throws Exception {

        log.info(this.getClass().getName() + ".userInfo Start!");

        // Access Token에 저장된 회원아이디 가져오기
        String userId = this.getUserIdFromToken(request);

        UserInfoDTO pDTO = new UserInfoDTO();
        pDTO.setUserId(userId);

        // 회원정보 조회하기
        UserInfoDTO rDTO = Optional.ofNullable(userInfoService.getUserInfo(pDTO)).orElseGet(UserInfoDTO::new);

        log.info(this.getClass().getName() + ".userInfo End!");

        return rDTO;

    }

}

