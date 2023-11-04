package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record UserInfoDTO(
        String userId,
        String userName,
        String password,
        String email,
        String addr1,
        String addr2,
        String regId,
        String regDt,
        String chgId,
        String chgDt,
        String roles) {
}

