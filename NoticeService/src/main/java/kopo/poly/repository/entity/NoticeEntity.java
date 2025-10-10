package kopo.poly.repository.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.cache.annotation.Cacheable;

/**
 * NoticeEntity 클래스는 공지글 정보를 데이터베이스에 저장하기 위한 JPA 엔티티입니다.
 * 각 필드는 공지글의 주요 정보와 관리 이력을 나타냅니다.
 * - @Entity: JPA가 관리하는 엔티티임을 표시합니다.
 * - @Table: NOTICE 테이블과 매핑됩니다.
 * - @Id: 공지글 고유 번호(기본키)
 * - @Column: 각 필드가 DB 컬럼과 어떻게 매핑되는지 설정합니다.
 * - @NonNull: 해당 값이 반드시 필요함을 명시합니다.
 * - @DynamicInsert/@DynamicUpdate: 변경된 값만 DB에 반영하여 성능을 높입니다.
 * - @Cacheable: 엔티티 캐싱을 허용합니다.
 * - @OneToOne: 작성자 정보(UserInfoEntity)와 연관관계 설정
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "NOTICE")
@DynamicInsert
@DynamicUpdate
@Builder
@Cacheable
@Entity
public class NoticeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_seq")
    private Long noticeSeq; // 공지글 고유 번호(기본키)

    @NonNull
    @Column(name = "title", length = 500, nullable = false)
    private String title; // 공지글 제목

    @NonNull
    @Column(name = "notice_yn", length = 1, nullable = false)
    private String noticeYn; // 공지글 여부(Y:공지글, N:일반글)

    @NonNull
    @Column(name = "contents", nullable = false)
    private String contents; // 공지글 내용

    @NonNull
    @Column(name = "user_id", nullable = false)
    private String userId; // 작성자 아이디

    @Column(name = "read_cnt", nullable = false)
    private Long readCnt; // 조회수

    @Column(name = "reg_id", updatable = false)
    private String regId; // 등록자 아이디

    @Column(name = "reg_dt", updatable = false)
    private String regDt; // 등록 일시

    @Column(name = "chg_id")
    private String chgId; // 수정자 아이디

    @Column(name = "chg_dt")
    private String chgDt; // 수정 일시

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private UserInfoEntity userInfo; // 작성자 정보(UserInfoEntity)

    /**
     * 공지글의 제목, 공지 여부, 내용을 변경합니다.
     *
     * @param title    변경할 제목
     * @param noticeYn 변경할 공지 여부
     * @param contents 변경할 내용
     */
    public void change(String title, String noticeYn, String contents) {
        this.title = title;
        this.noticeYn = noticeYn;
        this.contents = contents;
    }
}
