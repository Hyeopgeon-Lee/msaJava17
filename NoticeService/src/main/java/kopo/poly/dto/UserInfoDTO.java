package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
/**
 * UserInfoDTO는 회원 정보를 담는 데이터 전송 객체입니다.
 * 각 필드는 회원의 주요 정보와 관리 이력을 저장합니다.
 */
public record UserInfoDTO(
        String userId,   // 회원 아이디(로그인 시 사용)
        String userName, // 회원 이름(실명)
        String password, // 회원 비밀번호(암호화 저장 권장)
        String email,    // 회원 이메일 주소
        String addr1,    // 회원 기본 주소
        String addr2,    // 회원 상세 주소
        String regId,    // 등록자 아이디(관리자 등록 시 사용)
        String regDt,    // 등록 일시(YYYY-MM-DD HH:mm:ss)
        String chgId,    // 수정자 아이디
        String chgDt,    // 수정 일시(YYYY-MM-DD HH:mm:ss)
        String roles     // 회원 권한 정보(예: ROLE_USER, ROLE_ADMIN)
) {
}
