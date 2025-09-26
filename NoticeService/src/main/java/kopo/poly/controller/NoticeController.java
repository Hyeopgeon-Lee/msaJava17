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

@Tag(name = "공지사항 서비스", description = "공지사항 구현을 위한 API")
@Slf4j
@RequestMapping("/notice/v1")
@RequiredArgsConstructor
@RestController
public class NoticeController {

    private static final String HEADER_PREFIX = "Bearer ";

    private final INoticeService noticeService;
    private final IUserAPIService userAPIService;

    // ======================= 공개 API =======================

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

        log.info("{}.noticeList Start!", getClass().getName());

        List<NoticeDTO> rList = Optional.ofNullable(noticeService.getNoticeList())
                .orElseGet(Collections::emptyList);

        log.info("{}.noticeList End! size={}", getClass().getName(), rList.size());
        return CommonResponse.ok(rList);
    }

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

        log.info("{}.noticeInfo Start! pDTO={}", getClass().getName(), pDTO);

        boolean readCnt = "Y".equalsIgnoreCase(CmmUtil.nvl(pDTO.readCntYn()));
        NoticeDTO rDTO = Optional.ofNullable(noticeService.getNoticeInfo(pDTO, readCnt))
                .orElseGet(() -> NoticeDTO.builder().build());

        log.info("{}.noticeInfo End! nSeq={}", getClass().getName(), rDTO.noticeSeq());
        return CommonResponse.ok(rDTO);
    }

    // ======================= 보호 API =======================

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
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        log.info("{}.noticeInsert Start!", getClass().getName());

        if (!isBearer(authorization)) {
            return CommonResponse.error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        try {
            String userId = extractUserIdFromAuthorization(authorization);
            NoticeDTO nDTO = NoticeDTO.addUserId(pDTO, userId);

            log.info("noticeInsert by userId={}, payload={}", userId, nDTO);
            noticeService.insertNoticeInfo(nDTO);

            log.info("{}.noticeInsert End! result=success", getClass().getName());
            return CommonResponse.ok(MsgDTO.builder().result(1).msg("등록되었습니다.").build());

        } catch (Exception e) {
            log.warn("noticeInsert failed: {}", e.getMessage(), e);
            log.info("{}.noticeInsert End! result=fail", getClass().getName());
            return CommonResponse.ok(MsgDTO.builder().result(0).msg("실패하였습니다. : " + e.getMessage()).build());
        }
    }

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
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        log.info("{}.noticeUpdate Start!", getClass().getName());

        if (!isBearer(authorization)) {
            return CommonResponse.error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        try {
            String userId = extractUserIdFromAuthorization(authorization);
            NoticeDTO nDTO = NoticeDTO.addUserId(pDTO, userId);

            log.info("noticeUpdate by userId={}, payload={}", userId, nDTO);
            noticeService.updateNoticeInfo(nDTO);

            log.info("{}.noticeUpdate End! result=success", getClass().getName());
            return CommonResponse.ok(MsgDTO.builder().result(1).msg("수정되었습니다.").build());

        } catch (Exception e) {
            log.warn("noticeUpdate failed: {}", e.getMessage(), e);
            log.info("{}.noticeUpdate End! result=fail", getClass().getName());
            return CommonResponse.ok(MsgDTO.builder().result(0).msg("실패하였습니다. : " + e.getMessage()).build());
        }
    }

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
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        log.info("{}.noticeDelete Start! pDTO={}", getClass().getName(), pDTO);

        if (!isBearer(authorization)) {
            return CommonResponse.error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        }

        try {
            String userId = extractUserIdFromAuthorization(authorization);
            log.info("noticeDelete by userId={}, noticeSeq={}", userId, pDTO.noticeSeq());

            noticeService.deleteNoticeInfo(pDTO);

            log.info("{}.noticeDelete End! result=success", getClass().getName());
            return CommonResponse.ok(MsgDTO.builder().result(1).msg("삭제되었습니다.").build());

        } catch (Exception e) {
            log.warn("noticeDelete failed: {}", e.getMessage(), e);
            log.info("{}.noticeDelete End! result=fail", getClass().getName());
            return CommonResponse.ok(MsgDTO.builder().result(0).msg("실패하였습니다. : " + e.getMessage()).build());
        }
    }

    // ======================= 내부 유틸 =======================

    private boolean isBearer(String authorization) {
        log.info("{}.isBearer Start! authorization={}", getClass().getName(), authorization);
        return authorization != null && authorization.startsWith(HEADER_PREFIX);
    }

    private String extractUserIdFromAuthorization(String authorization) {
        log.info("{}.extractUserIdFromAuthorization Start!", getClass().getName());

        CommonResponse<UserInfoDTO> resp = userAPIService.getUserInfo(authorization);

        if (resp == null || resp.getStatus() != 200 || resp.getData() == null) {
            throw new IllegalStateException("토큰 검증 실패: 사용자 정보를 가져오지 못했습니다.");
        }

        String userId = CmmUtil.nvl(resp.getData().userId());
        if (userId.isEmpty()) {
            throw new IllegalStateException("토큰 검증 실패: userId가 비어있습니다.");
        }

        log.info("{}.extractUserIdFromAuthorization End!", getClass().getName());
        return userId;
    }
}
