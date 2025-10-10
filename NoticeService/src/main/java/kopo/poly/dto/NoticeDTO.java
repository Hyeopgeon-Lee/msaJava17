package kopo.poly.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import kopo.poly.repository.entity.NoticeEntity;
import lombok.Builder;

import java.util.List;

import static java.util.stream.Collectors.toList;

@Builder
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record NoticeDTO(

        Long noticeSeq, // 공지글의 고유 번호(기본키)
        String title, // 공지글 제목
        String noticeYn, // 공지글 여부(Y:공지글, N:일반글)
        String contents, // 공지글 내용
        String userId, // 작성자 아이디
        Long readCnt, // 조회수
        String regId, // 등록자 아이디
        String regDt, // 등록일시
        String chgId, // 수정자 아이디
        String chgDt, // 수정일시
        String userName, // 등록자 이름
        String readCntYn // 조회수 증가 여부

) {
    /**
     * @param pDTO   복사할 DTO 객체
     * @param userId 추가 저장할 회원아이디
     * @return 회원아이디가 추가된 {@link NoticeDTO } 객체
     */
    // 기존 NoticeDTO 객체에 회원 아이디를 추가하여 새로운 객체를 반환합니다.
    public static NoticeDTO addUserId(NoticeDTO pDTO, String userId) {

        NoticeDTO dto = NoticeDTO.builder()
                .noticeSeq(pDTO.noticeSeq())
                .title(pDTO.title())
                .noticeYn(pDTO.noticeYn())
                .contents(pDTO.contents())
                .userId(userId)
                .readCnt(pDTO.readCnt())
                .regId(pDTO.regId())
                .regDt(pDTO.regDt())
                .chgId(pDTO.chgId())
                .chgDt(pDTO.chgDt())
                .build();

        return dto;
    }

    // NoticeEntity 객체를 NoticeDTO 객체로 변환합니다.
    public static NoticeDTO from(NoticeEntity entity) {

        NoticeDTO dto = NoticeDTO.builder()
                .noticeSeq(entity.getNoticeSeq())
                .title(entity.getTitle())
                .noticeYn(entity.getNoticeYn())
                .contents(entity.getContents())
                .userId(entity.getUserId())
                .userName(entity.getUserInfo().getUserName()) // 등록자 이름은 UserInfo에서 가져옵니다.
                .readCnt(entity.getReadCnt())
                .regId(entity.getRegId())
                .regDt(entity.getRegDt())
                .chgId(entity.getChgId())
                .chgDt(entity.getChgDt())
                .build();

        return dto;
    }

    // NoticeEntity 리스트를 NoticeDTO 리스트로 변환합니다.
    public static List<NoticeDTO> from(List<NoticeEntity> entity) {
        return entity.stream()
                .map(NoticeDTO::from)
                .collect(toList());
    }

}
