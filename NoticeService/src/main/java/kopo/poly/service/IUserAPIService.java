package kopo.poly.service;

import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.UserInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * IUserAPIService는 NoticeService에서 사용자 정보를 외부(UserService)로부터 조회하기 위한 Feign 클라이언트 인터페이스입니다.
 * - @FeignClient: 마이크로서비스 간 통신을 위해 API Gateway를 통해 UserService에 요청을 보냅니다.
 * - getUserInfo: 인증 토큰(bearerToken)을 헤더에 담아 사용자 정보를 받아옵니다.
 * 반환값은 CommonResponse<UserInfoDTO>로, 사용자 정보가 포함되어 있습니다.
 * 추가 라우팅이나 퍼블릭 경로가 필요하다면, 이 위치에 메서드를 추가할 수 있습니다.
 */
@FeignClient(name = "IUserAPIService", url = "${api.gateway-service}")
public interface IUserAPIService {

    /**
     * 인증 토큰을 이용해 사용자 정보를 조회합니다.
     *
     * @param bearerToken 인증용 JWT 토큰
     * @return 사용자 정보가 담긴 CommonResponse<UserInfoDTO>
     */
    @PostMapping("/user/v1/userInfo")
    CommonResponse<UserInfoDTO> getUserInfo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken
    );

}
