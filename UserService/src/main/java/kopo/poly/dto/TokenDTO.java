package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record TokenDTO(String userId, // 회원아이디
                       String role // 토큰에 저장되는 권한)

) {
}

