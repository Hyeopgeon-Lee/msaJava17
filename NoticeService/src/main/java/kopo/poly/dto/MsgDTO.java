package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record MsgDTO(
        int result, // 처리 결과 코드 (예: 성공=1, 실패=0)
        String msg  // 처리 결과에 대한 설명 메시지
) {
    // MsgDTO는 API 응답 시 결과 코드와 메시지를 전달하는 용도로 사용됩니다.
}
