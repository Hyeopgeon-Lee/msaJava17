package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class TokenDTO {

    private String userId; // 회원아이디
    private String role; // 토큰에 저장되는 권한

}
