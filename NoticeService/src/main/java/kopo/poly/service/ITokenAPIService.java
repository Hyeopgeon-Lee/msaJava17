package kopo.poly.service;

import kopo.poly.dto.TokenDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;


@FeignClient(name = "ITokenAPIService", url = "http://localhost:13000")
public interface ITokenAPIService {

    @PostMapping(value = "/user/getTokenInfo")
    TokenDTO getTokenInfo(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken);
}
