package kopo.poly.service;

import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.UserInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

// NoticeService
@FeignClient(name = "IUserAPIService", url = "${api.gateway-service}")
public interface IUserAPIService {

    @PostMapping("/user/v1/userInfo")
    CommonResponse<UserInfoDTO> getUserInfo(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken
    );
}
