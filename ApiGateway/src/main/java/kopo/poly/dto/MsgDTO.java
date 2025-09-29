package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;


/**
 * MsgDTO
 * -------------------------------------------------------------
 * API 응답에서 결과 코드와 메시지를 전달할 때 사용하는 데이터 전송 객체입니다.
 * 대학생이 쉽게 이해할 수 있도록 각 필드와 어노테이션에 대해 자세한 주석을 추가했습니다.
 * <p>
 * 주요 역할:
 * - result: 처리 결과를 나타내는 정수 코드 (예: 1=성공, 0=실패, -1=에러 등)
 * - msg: 처리 결과에 대한 설명 메시지 (예: "성공", "권한 없음" 등)
 * <p>
 * 어노테이션 설명:
 * - @Builder: 빌더 패턴을 지원하여 객체 생성 시 가독성과 편의성을 높여줍니다.
 * - @JsonInclude(JsonInclude.Include.NON_DEFAULT):
 */

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record MsgDTO(int result // 결과 코드
        , String msg // 결과 메시지
) {

}
