package kopo.poly.service.impl;

import jakarta.transaction.Transactional;
import kopo.poly.dto.NoticeDTO;
import kopo.poly.repository.NoticeRepository;
import kopo.poly.repository.entity.NoticeEntity;
import kopo.poly.service.INoticeService;
import kopo.poly.util.CmmUtil;
import kopo.poly.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Slf4j
@RequiredArgsConstructor
@Service
public class NoticeService implements INoticeService {

    // RequiredArgsConstructor 어노테이션으로 생성자를 자동 생성함
    // noticeRepository 변수에 이미 메모리에 올라간 NoticeRepository 객체를 넣어줌
    // 예전에는 autowired 어노테이션를 통해 설정했었지만, 이젠 생성자를 통해 객체 주입함
    private final NoticeRepository noticeRepository;

    @Override
    public List<NoticeDTO> getNoticeList() {

        // 서비스 시작 로그
        log.info("{} getNoticeList Start!", this.getClass().getName());

        // 공지사항 전체 리스트 조회하기
        List<NoticeEntity> rList = noticeRepository.getNoticeList();

        // 엔티티의 값들을 DTO에 맞게 넣어주기
        List<NoticeDTO> nList = NoticeDTO.from(rList);

        // 서비스 종료 로그
        log.info("{} getNoticeList End!", this.getClass().getName());

        return nList;
    }

    @Transactional
    @Override
    public NoticeDTO getNoticeInfo(NoticeDTO pDTO, boolean type) {

        // 서비스 시작 로그
        log.info("{} getNoticeInfo Start!", this.getClass().getName());

        if (type) {
            // 조회수 증가하기
            int res = noticeRepository.updateReadCnt(pDTO.noticeSeq());

            // 조회수 증가 성공여부 체크
            log.info("조회수 증가 결과: {}", res);
        }

        // 공지사항 상세내역 가져오기
        NoticeEntity rEntity = noticeRepository.findByNoticeSeq(pDTO.noticeSeq());

        // 엔티티의 값들을 DTO에 맞게 넣어주기
        NoticeDTO rDTO = NoticeDTO.from(rEntity);

        // 서비스 종료 로그
        log.info("{} getNoticeInfo End!", this.getClass().getName());

        return rDTO;
    }

    @Transactional
    @Override
    public void updateNoticeInfo(NoticeDTO pDTO) {

        // 서비스 시작 로그
        log.info("{} updateNoticeInfo Start!", this.getClass().getName());

        Long noticeSeq = pDTO.noticeSeq();

        // 입력받은 DTO 정보 로그
        log.info("pDTO: {}", pDTO);

        // 현재 공지사항 조회수 가져오기
        NoticeEntity entity = noticeRepository.findById(noticeSeq)
                .orElseThrow(() -> new NoSuchElementException("공지 없음: " + noticeSeq));

        // 수정할 값들을 빌더를 통해 엔티티에 저장하기
        entity.change(
                CmmUtil.nvl(pDTO.title()),
                CmmUtil.nvl(pDTO.noticeYn()),
                CmmUtil.nvl(pDTO.contents())
        );


        // 서비스 종료 로그
        log.info("{} updateNoticeInfo End!", this.getClass().getName());

    }

    @Override
    public void deleteNoticeInfo(NoticeDTO pDTO) {

        // 서비스 시작 로그
        log.info("{} deleteNoticeInfo Start!", this.getClass().getName());

        Long noticeSeq = pDTO.noticeSeq();

        // 삭제할 공지사항 번호 로그
        log.info("noticeSeq: {}", noticeSeq);

        // 데이터 삭제하기
        noticeRepository.deleteById(noticeSeq);

        // 서비스 종료 로그
        log.info("{} deleteNoticeInfo End!", this.getClass().getName());
    }

    @Override
    public void insertNoticeInfo(NoticeDTO pDTO) {

        // 서비스 시작 로그
        log.info("{} insertNoticeInfo Start!", this.getClass().getName());

        String title = CmmUtil.nvl(pDTO.title());
        String noticeYn = CmmUtil.nvl(pDTO.noticeYn());
        String contents = CmmUtil.nvl(pDTO.contents());
        String userId = CmmUtil.nvl(pDTO.userId());

        // 입력값 로그
        log.info("title: {}", title);
        log.info("noticeYn: {}", noticeYn);
        log.info("contents: {}", contents);
        log.info("userId: {}", userId);

        // 공지사항 저장을 위해서는 PK 값은 빌더에 추가하지 않는다.
        // JPA에 자동 증가 설정을 해놨음
        NoticeEntity pEntity = NoticeEntity.builder()
                .title(title).noticeYn(noticeYn).contents(contents).userId(userId).readCnt(0L)
                .regId(userId).regDt(DateUtil.getDateTime("yyyy-MM-dd hh:mm:ss"))
                .chgId(userId).chgDt(DateUtil.getDateTime("yyyy-MM-dd hh:mm:ss"))
                .build();

        // 공지사항 저장하기
        noticeRepository.save(pEntity);

        // 서비스 종료 로그
        log.info("{} insertNoticeInfo End!", this.getClass().getName());

    }
}
