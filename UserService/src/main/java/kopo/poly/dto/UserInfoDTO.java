// UserInfoDTO.java - 사용자 정보 데이터 전송 객체(DTO)
// 이 파일은 사용자(User)의 주요 정보를 담아 서비스 계층과 컨트롤러 계층 등에서 데이터를 주고받을 때 사용합니다.
package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude; // JSON 변환 시 사용되는 어노테이션 import
import kopo.poly.repository.entity.UserInfoEntity; // DB 엔티티 객체 import
import kopo.poly.util.CmmUtil; // 공통 유틸리티 클래스 import
import kopo.poly.util.EncryptUtil; // 암호화/복호화 유틸리티 클래스 import
import lombok.Builder; // 빌더 패턴 적용을 위한 lombok 라이브러리 import

@Builder // 빌더 패턴 적용: 객체 생성 시 가독성과 안정성을 높임
@JsonInclude(JsonInclude.Include.NON_DEFAULT) // JSON 직렬화 시 기본값이 아닌 필드만 포함
public record UserInfoDTO(
        String userId,   // 사용자 아이디(로그인 시 사용)
        String userName, // 사용자 이름(실명)
        String password, // 사용자 비밀번호(암호화되어 저장)
        String email,    // 사용자 이메일(복호화 후 저장)
        String addr1,    // 주소(기본 주소)
        String addr2,    // 주소(상세 주소)
        String regId,    // 등록자 아이디(관리자 또는 본인)
        String regDt,    // 등록 일시(YYYYMMDDHHMMSS)
        String chgId,    // 수정자 아이디
        String chgDt,    // 수정 일시(YYYYMMDDHHMMSS)
        String roles     // 사용자 권한 정보(예: ROLE_USER, ROLE_ADMIN)
) {
    // from() 메서드는 DB 엔티티(UserInfoEntity) 객체를 DTO로 변환합니다.
    // 예외(Exception)가 발생할 수 있으므로 throws Exception을 명시합니다.
    // 이메일은 암호화되어 저장되므로 복호화 후 DTO에 저장합니다.
    public static UserInfoDTO from(UserInfoEntity entity) throws Exception {
        // UserInfoDTO 객체를 빌더 패턴으로 생성합니다.
        // 각 필드는 엔티티에서 값을 가져와서 DTO에 할당합니다.
        // 이메일은 복호화(Decrypt) 과정을 거칩니다.
        UserInfoDTO dto = UserInfoDTO.builder()
                .userId(entity.getUserId()) // 사용자 아이디
                .userName(entity.getUserName()) // 사용자 이름
                .password(entity.getPassword()) // 비밀번호(암호화 상태)
                .email(EncryptUtil.decAES128CBC(CmmUtil.nvl(entity.getEmail()))) // 이메일 복호화
                .addr1(entity.getAddr1()) // 기본 주소
                .addr2(entity.getAddr2()) // 상세 주소
                .regId(entity.getRegId()) // 등록자 아이디
                .regDt(entity.getRegDt()) // 등록 일시
                .chgId(entity.getChgId()) // 수정자 아이디
                .chgDt(entity.getChgDt()) // 수정 일시
                // roles 필드는 필요에 따라 추가할 수 있습니다. (권한 정보)
                .build();
        // 변환된 DTO 객체 반환
        return dto;
    }
    // 추가 라우팅 및 퍼블릭 경로 설정이 필요한 경우, 이 DTO를 활용하여 사용자 정보를 전달할 수 있습니다.
    // 예시: UserController에서 사용자 정보 조회/수정/삭제 시 이 DTO를 반환하거나 파라미터로 사용합니다.
}
