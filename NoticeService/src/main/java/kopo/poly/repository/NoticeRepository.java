package kopo.poly.repository;

import kopo.poly.repository.entity.NoticeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NoticeRepository extends JpaRepository<NoticeEntity, Long> {

    /**
     * 모든 공지글 목록을 조회합니다.
     * NoticeEntity와 연관된 UserInfo를 함께 조회하여 성능을 높입니다.
     * 공지글 여부(Y/N)와 공지글 번호로 내림차순 정렬합니다.
     */
    @Query("SELECT A FROM NoticeEntity  A JOIN FETCH A.userInfo ORDER BY A.noticeYn desc , A.noticeSeq DESC")
    List<NoticeEntity> getNoticeList();

    /**
     * 공지글 번호로 단일 공지글을 조회합니다.
     *
     * @param noticeSeq 공지글 고유 번호
     * @return NoticeEntity 객체
     */
    NoticeEntity findByNoticeSeq(Long noticeSeq);

    /**
     * 공지글의 조회수를 1 증가시킵니다.
     *
     * @param noticeSeq 조회수를 증가시킬 공지글 번호
     * @return 업데이트된 행의 개수(성공 시 1)
     */
    @Modifying(clearAutomatically = true)
    @Query(value =
            "UPDATE NOTICE A SET A.READ_CNT = IFNULL(A.READ_CNT, 0) + 1 " +
                    "WHERE A.NOTICE_SEQ = :noticeSeq",
            nativeQuery = true)
    int updateReadCnt(@Param("noticeSeq") Long noticeSeq);

}
