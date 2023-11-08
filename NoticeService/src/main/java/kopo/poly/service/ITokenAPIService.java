package kopo.poly.service;

import kopo.poly.dto.TokenDTO;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@RefreshScope
@FeignClient(name = "ITokenAPIService", url = "${api.gateway}")
public interface ITokenAPIService {

    @PostMapping(value = "/user/getTokenInfo")
    TokenDTO getTokenInfo(@RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken);
}
