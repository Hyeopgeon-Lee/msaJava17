package kopo.poly.service;

import kopo.poly.dto.NoticeDTO;

import java.util.List;

/**
 * INoticeService는 공지사항 관련 비즈니스 로직을 정의하는 서비스 인터페이스입니다.
 * 각 메서드는 공지사항의 조회, 상세조회, 수정, 삭제, 등록 기능을 제공합니다.
 * 실제 구현체에서 데이터베이스와 연동하여 동작합니다.
 * 추가 라우팅이나 퍼블릭 경로가 필요하다면, 이 위치에 메서드를 추가할 수 있습니다.
 */
public interface INoticeService {

    /**
     * 모든 공지사항 목록을 조회합니다.
     *
     * @return 공지사항 리스트
     */
    List<NoticeDTO> getNoticeList();

    /**
     * 공지사항 상세 정보를 조회합니다.
     *
     * @param pDTO 조회할 공지사항 정보
     * @param type 조회수 증가 여부(true: 증가, false: 증가 안함)
     * @return 상세 공지사항 정보
     * @throws Exception 예외 발생 시 처리
     */
    NoticeDTO getNoticeInfo(NoticeDTO pDTO, boolean type) throws Exception;

    /**
     * 공지사항 정보를 수정합니다.
     *
     * @param pDTO 수정할 공지사항 정보
     * @throws Exception 예외 발생 시 처리
     */
    void updateNoticeInfo(NoticeDTO pDTO) throws Exception;

    /**
     * 공지사항 정보를 삭제합니다.
     *
     * @param pDTO 삭제할 공지사항 정보
     */
    void deleteNoticeInfo(NoticeDTO pDTO);

    /**
     * 공지사항 정보를 등록합니다.
     *
     * @param pDTO 등록할 공지사항 정보
     */
    void insertNoticeInfo(NoticeDTO pDTO);

}
