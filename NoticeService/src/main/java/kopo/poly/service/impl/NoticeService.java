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

/**
 * NoticeService는 공지사항 관련 비즈니스 로직을 처리하는 서비스 클래스입니다.
 * 데이터베이스와 연동하여 공지사항의 조회, 상세조회, 등록, 수정, 삭제 기능을 제공합니다.
 * 각 메서드에 기능별 주석을 추가하여 대학생이 쉽게 이해할 수 있도록 작성했습니다.
 * 추가 라우팅 및 퍼블릭 경로가 필요하다면, 관련 메서드를 이 위치에 추가할 수 있습니다.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class NoticeService implements INoticeService {

    // 생성자를 통해 NoticeRepository 객체를 주입받아 사용합니다.
    private final NoticeRepository noticeRepository;

    /**
     * 모든 공지사항 목록을 조회합니다.
     *
     * @return 공지사항 리스트
     */
    @Override
    public List<NoticeDTO> getNoticeList() {
        log.info("{} getNoticeList Start!", this.getClass().getName()); // 서비스 시작 로그
        List<NoticeEntity> rList = noticeRepository.getNoticeList(); // 공지사항 전체 리스트 조회
        List<NoticeDTO> nList = NoticeDTO.from(rList); // 엔티티를 DTO로 변환
        log.info("{} getNoticeList End!", this.getClass().getName()); // 서비스 종료 로그
        return nList;
    }

    /**
     * 공지사항 상세 정보를 조회합니다.
     *
     * @param pDTO 조회할 공지사항 정보
     * @param type 조회수 증가 여부(true: 증가, false: 증가 안함)
     * @return 상세 공지사항 정보
     */
    @Transactional
    @Override
    public NoticeDTO getNoticeInfo(NoticeDTO pDTO, boolean type) {
        log.info("{} getNoticeInfo Start!", this.getClass().getName()); // 서비스 시작 로그
        if (type) {
            int res = noticeRepository.updateReadCnt(pDTO.noticeSeq()); // 조회수 증가
            log.info("조회수 증가 결과: {}", res); // 조회수 증가 결과 로그
        }
        NoticeEntity rEntity = noticeRepository.findByNoticeSeq(pDTO.noticeSeq()); // 공지사항 상세내역 조회
        NoticeDTO rDTO = NoticeDTO.from(rEntity); // 엔티티를 DTO로 변환
        log.info("{} getNoticeInfo End!", this.getClass().getName()); // 서비스 종료 로그
        return rDTO;
    }

    /**
     * 공지사항 정보를 수정합니다.
     *
     * @param pDTO 수정할 공지사항 정보
     */
    @Transactional
    @Override
    public void updateNoticeInfo(NoticeDTO pDTO) {
        log.info("{} updateNoticeInfo Start!", this.getClass().getName()); // 서비스 시작 로그
        Long noticeSeq = pDTO.noticeSeq();
        log.info("pDTO: {}", pDTO); // 입력받은 DTO 정보 로그
        NoticeEntity entity = noticeRepository.findById(noticeSeq)
                .orElseThrow(() -> new NoSuchElementException("공지 없음: " + noticeSeq)); // 기존 공지사항 조회
        entity.change(
                CmmUtil.nvl(pDTO.title()),
                CmmUtil.nvl(pDTO.noticeYn()),
                CmmUtil.nvl(pDTO.contents())
        ); // 수정할 값 저장
        log.info("{} updateNoticeInfo End!", this.getClass().getName()); // 서비스 종료 로그
    }

    /**
     * 공지사항 정보를 삭제합니다.
     *
     * @param pDTO 삭제할 공지사항 정보
     */
    @Override
    public void deleteNoticeInfo(NoticeDTO pDTO) {
        log.info("{} deleteNoticeInfo Start!", this.getClass().getName()); // 서비스 시작 로그
        Long noticeSeq = pDTO.noticeSeq();
        log.info("noticeSeq: {}", noticeSeq); // 삭제할 공지사항 번호 로그
        noticeRepository.deleteById(noticeSeq); // 데이터 삭제
        log.info("{} deleteNoticeInfo End!", this.getClass().getName()); // 서비스 종료 로그
    }

    /**
     * 공지사항 정보를 등록합니다.
     *
     * @param pDTO 등록할 공지사항 정보
     */
    @Override
    public void insertNoticeInfo(NoticeDTO pDTO) {
        log.info("{} insertNoticeInfo Start!", this.getClass().getName()); // 서비스 시작 로그
        String title = CmmUtil.nvl(pDTO.title());
        String noticeYn = CmmUtil.nvl(pDTO.noticeYn());
        String contents = CmmUtil.nvl(pDTO.contents());
        String userId = CmmUtil.nvl(pDTO.userId());
        log.info("title: {}", title);
        log.info("noticeYn: {}", noticeYn);
        log.info("contents: {}", contents);
        log.info("userId: {}", userId); // 입력값 로그
        NoticeEntity pEntity = NoticeEntity.builder()
                .title(title).noticeYn(noticeYn).contents(contents).userId(userId).readCnt(0L)
                .regId(userId).regDt(DateUtil.getDateTime("yyyy-MM-dd hh:mm:ss"))
                .chgId(userId).chgDt(DateUtil.getDateTime("yyyy-MM-dd hh:mm:ss"))
                .build(); // 공지사항 저장용 엔티티 생성
        noticeRepository.save(pEntity); // 공지사항 저장
        log.info("{} insertNoticeInfo End!", this.getClass().getName()); // 서비스 종료 로그
    }

    // [추가 라우팅 및 퍼블릭 경로 설정 위치]
    // 라우팅이나 공개 경로가 필요하다면, 관련 메서드를 이 위치에 추가할 수 있습니다.
}
