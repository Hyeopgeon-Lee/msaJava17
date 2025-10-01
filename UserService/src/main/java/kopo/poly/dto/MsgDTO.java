// MsgDTO.java - 메시지 결과를 전달하는 데이터 전송 객체(DTO)
// 이 파일은 사용자 서비스(UserService)에서 공통적으로 사용하는 메시지 응답 구조를 정의합니다.
package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude; // JSON 변환 시 사용되는 어노테이션을 import
import lombok.Builder; // 빌더 패턴을 적용하기 위한 lombok 라이브러리 import

@Builder // 빌더 패턴 적용: 객체 생성 시 가독성과 안정성을 높임
@JsonInclude(JsonInclude.Include.NON_DEFAULT) // JSON 직렬화 시 기본값이 아닌 필드만 포함
public record MsgDTO(
        int result // 결과 코드: 예) 0=성공, 1=실패 등. API 응답의 상태를 숫자로 표현
        , String msg // 결과 메시지: 예) "처리 성공", "오류 발생" 등. 사용자에게 전달할 메시지
) {
    // record는 자바 16부터 도입된 불변 객체(immutable object)입니다.
    // 생성자, getter, equals, hashCode, toString이 자동 생성되어 코드가 간결해집니다.
    // 이 구조는 데이터만 전달할 때 사용하며, 별도의 로직은 포함하지 않습니다.
}
