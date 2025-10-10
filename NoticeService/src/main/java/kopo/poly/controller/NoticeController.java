package kopo.poly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.MsgDTO;
import kopo.poly.dto.NoticeDTO;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.INoticeService;
import kopo.poly.service.IUserAPIService;
import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * NoticeController는 공지사항 관련 HTTP 요청을 처리하는 REST API 컨트롤러입니다.
 * - 공개 API: 누구나 접근 가능한 공지사항 조회 기능 제공
 * - 보호 API: 인증된 사용자만 접근 가능한 등록/수정/삭제 기능 제공
 * - 내부 유틸: 인증 토큰 검증 및 사용자 정보 추출 기능 제공
 * 각 메서드와 주요 로직에 대학생이 이해하기 쉽도록 주석을 추가했습니다.
 * 추가 라우팅 및 퍼블릭 경로가 필요하다면, 관련 메서드를 이 위치에 추가할 수 있습니다.
 */
@Tag(name = "공지사항 서비스", description = "공지사항 구현을 위한 API")
@Slf4j
@RequestMapping("/notice/v1")
@RequiredArgsConstructor
@RestController
public class NoticeController {

    private static final String HEADER_PREFIX = "Bearer "; // JWT 토큰 헤더 접두어

    private final INoticeService noticeService; // 공지사항 비즈니스 로직 서비스
    private final IUserAPIService userAPIService; // 사용자 정보 조회 서비스

    /**
     * 인증 토큰이 Bearer 타입인지 확인합니다.
     *
     * @param authorization 인증 토큰
     * @return Bearer 타입 여부
     */
    private boolean isBearer(String authorization) {
        log.info("{}.isBearer Start! authorization={}", getClass().getName(), authorization); // Bearer 타입 확인 로그
        return authorization != null && authorization.startsWith(HEADER_PREFIX);
    }

    /**
     * 인증 토큰에서 userId를 추출합니다.
     * UserService에 토큰을 전달해 사용자 정보를 받아옵니다.
     *
     * @param authorization 인증 토큰
     * @return 추출된 userId
     */
    private String extractUserIdFromAuthorization(String authorization) {
        log.info("{}.extractUserIdFromAuthorization Start!", getClass().getName()); // 추출 시작 로그

        CommonResponse<UserInfoDTO> resp = userAPIService.getUserInfo(authorization); // 사용자 정보 조회

        if (resp == null || resp.getStatus() != 200 || resp.getData() == null) {
            throw new IllegalStateException("토큰 검증 실패: 사용자 정보를 가져오지 못했습니다.");
        }

        String userId = CmmUtil.nvl(resp.getData().userId()); // userId 추출

        if (userId.isEmpty()) {
            throw new IllegalStateException("토큰 검증 실패: userId가 비어있습니다.");
        }

        log.info("{}.extractUserIdFromAuthorization End!", getClass().getName()); // 추출 종료 로그
        return userId;
    }

    // ======================= 공개 API =======================

    /**
     * 공지사항 리스트를 반환하는 API입니다.
     * 누구나 접근 가능하며, 공지사항 전체 목록을 조회합니다.
     *
     * @return 공지사항 리스트
     */
    @Operation(
            summary = "공지사항 리스트 API",
            description = "공지사항 리스트 정보를 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Page Not Found!")
            }
    )
    @PostMapping("noticeList")
    public ResponseEntity<CommonResponse<List<NoticeDTO>>> noticeList() {
        log.info("{}.noticeList Start!", getClass().getName()); // 서비스 시작 로그
        List<NoticeDTO> rList = Optional.ofNullable(noticeService.getNoticeList())
                .orElseGet(Collections::emptyList); // 공지사항 리스트 조회

        log.info("{}.noticeList End! size={}", getClass().getName(), rList.size()); // 서비스 종료 로그
        return CommonResponse.ok(rList);
    }

    /**
     * 공지사항 상세 정보를 반환하는 API입니다.
     * readCntYn=Y인 경우 조회수를 증가시킵니다.
     *
     * @param pDTO 요청 본문에 nSeq(글번호), readCntYn(Y/N) 포함
     * @return 상세 공지사항 정보
     */
    @Operation(
            summary = "공지사항 상세보기 API",
            description = """
                    공지사항 상세보기 결과를 반환하며, readCntYn=Y인 경우 조회수를 증가시킵니다.
                    요청 본문에 nSeq(글번호), readCntYn(Y/N)을 포함하세요.
                    """,
            parameters = {
                    @Parameter(name = "nSeq", description = "공지사항 글번호"),
                    @Parameter(name = "readCntYn", description = "조회수 증가여부(Y/N)")
            },
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Page Not Found!")
            }
    )
    @PostMapping("noticeInfo")
    public ResponseEntity<CommonResponse<NoticeDTO>> noticeInfo(@Valid @RequestBody NoticeDTO pDTO) throws Exception {
        log.info("{}.noticeInfo Start! pDTO={}", getClass().getName(), pDTO); // 서비스 시작 로그

        boolean readCnt = "Y".equalsIgnoreCase(CmmUtil.nvl(pDTO.readCntYn())); // 조회수 증가 여부 판단

        NoticeDTO rDTO = Optional.ofNullable(noticeService.getNoticeInfo(pDTO, readCnt))
                .orElseGet(() -> NoticeDTO.builder().build()); // 상세 정보 조회

        log.info("{}.noticeInfo End! nSeq={}", getClass().getName(), rDTO.noticeSeq()); // 서비스 종료 로그
        return CommonResponse.ok(rDTO);
    }

    // ======================= 보호 API =======================

    /**
     * 공지사항을 등록하는 API입니다.
     * 인증된 사용자만 접근 가능하며, 토큰에서 userId를 추출해 등록자 정보로 사용합니다.
     *
     * @param pDTO          등록할 공지사항 정보
     * @param authorization 인증 토큰
     * @return 등록 결과 메시지
     */
    @Operation(
            summary = "공지사항 등록 API",
            description = """
                    Authorization: Bearer <AT> 헤더 기반으로 사용자 식별자(userId)를 확인한 뒤,
                    본문에 전달된 공지사항을 등록합니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
            }
    )
    @PostMapping("noticeInsert")
    public ResponseEntity<CommonResponse<MsgDTO>> noticeInsert(
            @RequestBody NoticeDTO pDTO,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        log.info("{}.noticeInsert Start!", getClass().getName()); // 서비스 시작 로그

        if (!isBearer(authorization)) {
            return CommonResponse.error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"); // 인증 실패 시 반환
        }

        try {
            String userId = extractUserIdFromAuthorization(authorization); // 토큰에서 userId 추출

            NoticeDTO nDTO = NoticeDTO.addUserId(pDTO, userId); // 등록자 정보 추가

            log.info("noticeInsert by userId={}, payload={}", userId, nDTO); // 등록 정보 로그

            noticeService.insertNoticeInfo(nDTO); // 공지사항 등록

            log.info("{}.noticeInsert End! result=success", getClass().getName()); // 서비스 종료 로그
            return CommonResponse.ok(MsgDTO.builder().result(1).msg("등록되었습니다.").build());

        } catch (Exception e) {
            log.warn("noticeInsert failed: {}", e.getMessage(), e); // 예외 로그
            log.info("{}.noticeInsert End! result=fail", getClass().getName()); // 서비스 종료 로그

            return CommonResponse.ok(MsgDTO.builder().result(0).msg("실패하였습니다. : " + e.getMessage()).build());
        }
    }

    /**
     * 공지사항을 수정하는 API입니다.
     * 인증된 사용자만 접근 가능하며, 토큰에서 userId를 추출해 수정자 정보로 사용합니다.
     *
     * @param pDTO          수정할 공지사항 정보
     * @param authorization 인증 토큰
     * @return 수정 결과 메시지
     */
    @Operation(
            summary = "공지사항 수정 API",
            description = """
                    Authorization: Bearer <AT> 헤더 기반으로 사용자 식별자(userId)를 확인한 뒤,
                    본문에 전달된 공지사항을 수정합니다.
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
            }
    )
    @PostMapping("noticeUpdate")
    public ResponseEntity<CommonResponse<MsgDTO>> noticeUpdate(
            @Valid @RequestBody NoticeDTO pDTO,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        log.info("{}.noticeUpdate Start!", getClass().getName()); // 서비스 시작 로그

        if (!isBearer(authorization)) {
            return CommonResponse.error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"); // 인증 실패 시 반환

        }

        try {
            String userId = extractUserIdFromAuthorization(authorization); // 토큰에서 userId 추출
            NoticeDTO nDTO = NoticeDTO.addUserId(pDTO, userId); // 수정자 정보 추가
            log.info("noticeUpdate by userId={}, payload={}", userId, nDTO); // 수정 정보 로그
            noticeService.updateNoticeInfo(nDTO); // 공지사항 수정
            log.info("{}.noticeUpdate End! result=success", getClass().getName()); // 서비스 종료 로그
            return CommonResponse.ok(MsgDTO.builder().result(1).msg("수정되었습니다.").build());

        } catch (Exception e) {
            log.warn("noticeUpdate failed: {}", e.getMessage(), e); // 예외 로그
            log.info("{}.noticeUpdate End! result=fail", getClass().getName()); // 서비스 종료 로그
            return CommonResponse.ok(MsgDTO.builder().result(0).msg("실패하였습니다. : " + e.getMessage()).build());
        }
    }

    /**
     * 공지사항을 삭제하는 API입니다.
     * 인증된 사용자만 접근 가능하며, 토큰에서 userId를 추출해 삭제자 정보로 사용합니다.
     * 필요 시 작성자 일치/권한 검증 로직을 추가할 수 있습니다.
     *
     * @param pDTO          삭제할 공지사항 정보
     * @param authorization 인증 토큰
     * @return 삭제 결과 메시지
     */
    @Operation(
            summary = "공지사항 삭제 API",
            description = """
                    Authorization: Bearer <AT> 헤더 기반으로 사용자 식별자(userId)를 확인한 뒤,
                    요청된 공지사항을 삭제합니다.
                    (필요 시 작성자 일치/권한 검증 로직을 추가하세요.)
                    """,
            security = {@SecurityRequirement(name = "bearerAuth")},
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
            }
    )
    @PostMapping("noticeDelete")
    public ResponseEntity<CommonResponse<MsgDTO>> noticeDelete(
            @Valid @RequestBody NoticeDTO pDTO,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {

        log.info("{}.noticeDelete Start! pDTO={}", getClass().getName(), pDTO); // 서비스 시작 로그

        if (!isBearer(authorization)) {
            return CommonResponse.error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED"); // 인증 실패 시 반환

        }

        try {
            String userId = extractUserIdFromAuthorization(authorization); // 토큰에서 userId 추출
            log.info("noticeDelete by userId={}, noticeSeq={}", userId, pDTO.noticeSeq()); // 삭제 정보 로그
            noticeService.deleteNoticeInfo(pDTO); // 공지사항 삭제
            log.info("{}.noticeDelete End! result=success", getClass().getName()); // 서비스 종료 로그
            return CommonResponse.ok(MsgDTO.builder().result(1).msg("삭제되었습니다.").build());

        } catch (Exception e) {
            log.warn("noticeDelete failed: {}", e.getMessage(), e); // 예외 로그
            log.info("{}.noticeDelete End! result=fail", getClass().getName()); // 서비스 종료 로그
            return CommonResponse.ok(MsgDTO.builder().result(0).msg("실패하였습니다. : " + e.getMessage()).build());

        }
    }


}
