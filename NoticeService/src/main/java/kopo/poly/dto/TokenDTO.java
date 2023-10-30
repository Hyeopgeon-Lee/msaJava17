package kopo.poly.dto;

import lombok.Builder;

@Builder
public record TokenDTO(String userId, // 회원아이디
                       String role // 토큰에 저장되는 권한)

) {
}
