package kopo.poly.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "USER_INFO")
@DynamicInsert
@DynamicUpdate
@Builder
@Cacheable
@Entity
/**
 * UserInfoEntity는 회원 정보를 데이터베이스에 저장하기 위한 JPA 엔티티 클래스입니다.
 * 각 필드는 회원의 주요 정보와 관리 이력을 나타냅니다.
 * - @Entity: JPA가 관리하는 엔티티임을 표시합니다.
 * - @Table: USER_INFO 테이블과 매핑됩니다.
 * - @Id: 기본키(회원 아이디)
 * - @Column: 각 필드가 DB 컬럼과 어떻게 매핑되는지 설정합니다.
 * - @NonNull: 해당 값이 반드시 필요함을 명시합니다.
 * - @DynamicInsert/@DynamicUpdate: 변경된 값만 DB에 반영하여 성능을 높입니다.
 * - @Cacheable: 엔티티 캐싱을 허용합니다.
 */
public class UserInfoEntity implements Serializable {

    @Id
    @Column(name = "user_id")
    private String userId; // 회원 아이디(기본키)

    @NonNull
    @Column(name = "user_name", length = 500, nullable = false)
    private String userName; // 회원 이름(실명)

    @NonNull
    @Column(name = "password", length = 1, nullable = false)
    private String password; // 회원 비밀번호(암호화 저장 권장)

    @NonNull
    @Column(name = "email", nullable = false)
    private String email; // 회원 이메일 주소

    @NonNull
    @Column(name = "addr1", nullable = false)
    private String addr1; // 회원 기본 주소

    @Column(name = "addr2", nullable = false)
    private String addr2; // 회원 상세 주소

    @Column(name = "reg_id", updatable = false)
    private String regId; // 등록자 아이디(관리자 등록 시 사용)

    @Column(name = "reg_dt", updatable = false)
    private String regDt; // 등록 일시(YYYY-MM-DD HH:mm:ss)

    @Column(name = "chg_id")
    private String chgId; // 수정자 아이디

    @Column(name = "chg_dt")
    private String chgDt; // 수정 일시(YYYY-MM-DD HH:mm:ss)

    @Column(name = "roles") //권한 데이터는 ,를 구분자로 여러 개(예 : 관리자, 일반사용자) 정의 가능함
    private String roles; // 회원 권한 정보(예: ROLE_USER, ROLE_ADMIN)

}
