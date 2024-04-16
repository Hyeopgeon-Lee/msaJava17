package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import kopo.poly.repository.entity.UserInfoEntity;
import kopo.poly.util.CmmUtil;
import kopo.poly.util.EncryptUtil;
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

    public static UserInfoDTO from(UserInfoEntity entity) throws Exception {

        UserInfoDTO dto = UserInfoDTO.builder()
                .userId(entity.getUserId())
                .userName(entity.getUserName())
                .password(entity.getPassword())
                .email(EncryptUtil.decAES128CBC(CmmUtil.nvl(entity.getEmail())))
                .addr1(entity.getAddr1())
                .addr2(entity.getAddr2())
                .regId(entity.getRegId())
                .regDt(entity.getRegDt())
                .chgId(entity.getChgId())
                .chgDt(entity.getChgDt())
                .build();

        return dto;
    }
}

