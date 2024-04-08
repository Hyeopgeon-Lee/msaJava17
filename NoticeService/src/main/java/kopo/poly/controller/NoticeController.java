package kopo.poly.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.MsgDTO;
import kopo.poly.dto.NoticeDTO;
import kopo.poly.dto.TokenDTO;
import kopo.poly.service.INoticeService;
import kopo.poly.service.ITokenAPIService;
import kopo.poly.util.CmmUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = {"http://localhost:13000", "http://localhost:14000"},
        allowCredentials = "true",
        allowedHeaders = {"Content-Type"},
        methods = {RequestMethod.POST, RequestMethod.GET},
        originPatterns = {"notice/**"}
)
@Tag(name = "공지사항 서비스", description = "공지사항 구현을 위한 API")
@Slf4j
@RequestMapping(value = "/notice/v1")
@RequiredArgsConstructor
@RestController
public class NoticeController {

    // @RequiredArgsConstructor 를 통해 메모리에 올라간 서비스 객체를 Controller에서 사용할 수 있게 주입함
    private final INoticeService noticeService;

    private final ITokenAPIService tokenAPIService;

    private final String HEADER_PREFIX = "Bearer "; // Bearer 토큰 사용을 위한 선언 값

    @Operation(summary = "공지사항 리스트 API", description = "공지사항 리스트 정보 제공하는 API",
            responses = {@ApiResponse(responseCode = "200", description = "OK"), @ApiResponse(responseCode = "404", description = "Page Not Found!"),})
    @PostMapping(value = "noticeList")
    public ResponseEntity<CommonResponse> noticeList() {

        // 로그 찍기(추후 찍은 로그를 통해 이 함수에 접근했는지 파악하기 용이하다.)
        log.info(this.getClass().getName() + ".noticeList Start!");

        // 공지사항 리스트 조회하기
        // Java 8부터 제공되는 Optional 활용하여 NPE(Null Pointer Exception) 처리
        List<NoticeDTO> rList = Optional.ofNullable(noticeService.getNoticeList()).orElseGet(ArrayList::new);

        // 로그 찍기(추후 찍은 로그를 통해 이 함수 호출이 끝났는지 파악하기 용이하다.)
        log.info(this.getClass().getName() + ".noticeList End!");

        return ResponseEntity.ok(
                CommonResponse.of(HttpStatus.OK, HttpStatus.OK.series().name(), rList));
    }

    @Operation(summary = "공지사항 상세보기 결과제공 API", description = "공지사항 상세보기 결과 및 조회수 증가 API",
            parameters = {@Parameter(name = "nSeq", description = "공지사항 글번호"),
                    @Parameter(name = "readCntYn", description = "조회수 증가여부")},
            responses = {@ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Page Not Found!"),})
    @PostMapping(value = "noticeInfo")
    public ResponseEntity<CommonResponse> noticeInfo(@Valid @RequestBody NoticeDTO pDTO, BindingResult bindingResult) throws Exception {

        log.info(this.getClass().getName() + ".noticeInfo Start!");

        if (bindingResult.hasErrors()) { // Spring Validation 맞춰 잘 바인딩되었는지 체크
            return CommonResponse.getErrors(bindingResult); // 유효성 검증 결과에 따른 에러 메시지 전달

        }

        boolean readCnt = pDTO.readCntYn().equals("Y"); // 공지사항 증가여부를 boolean 값으로 변경

        // 반드시, 값을 받았으면, 꼭 로그를 찍어서 값이 제대로 들어오는지 파악해야함 반드시 작성할 것
        log.info("pDTO : " + pDTO);

        // Java 8부터 제공되는 Optional 활용하여 NPE(Null Pointer Exception) 처리
        NoticeDTO rDTO = Optional.ofNullable(noticeService.getNoticeInfo(pDTO, readCnt))
                .orElseGet(() -> NoticeDTO.builder().build());

        log.info(this.getClass().getName() + ".noticeInfo End!");

        return ResponseEntity.ok(
                CommonResponse.of(HttpStatus.OK, HttpStatus.OK.series().name(), rDTO));
    }

    @Operation(summary = "공지사항 등록 API", description = "공지사항 등록 및 등록결과를 제공하는 API",
            responses = {@ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Page Not Found!"),})
    @PostMapping(value = "noticeInsert")
    public ResponseEntity<CommonResponse> noticeInsert(@RequestBody NoticeDTO pDTO, BindingResult bindingResult,
                                                       @CookieValue(value = "${jwt.token.access.name}") String token) {

        log.info(this.getClass().getName() + ".noticeInsert Start!");

        if (bindingResult.hasErrors()) { // Spring Validation 맞춰 잘 바인딩되었는지 체크
            return CommonResponse.getErrors(bindingResult); // 유효성 검증 결과에 따른 에러 메시지 전달

        }

        String msg = ""; // 메시지 내용
        int res = 0; // 성공 여부
        MsgDTO dto; // 결과 메시지 구조

        try {
            // UserService로부터 Token 값 받아오기
            TokenDTO tDTO = tokenAPIService.getTokenInfo(HEADER_PREFIX + token);
            log.info("TokenDTO : " + tDTO); // Token 값 출력하기

            //JWT Access 토큰으로부터 회원아이디 가져오기
            String userId = CmmUtil.nvl(tDTO.userId());

            // 반드시, 값을 받았으면, 꼭 로그를 찍어서 값이 제대로 들어오는지 파악해야함 반드시 작성할 것
            log.info("userId : " + userId);
            log.info("pDTO : " + pDTO);

            // 데이터 저장하기 위해 DTO에 저장하기
            NoticeDTO nDTO = NoticeDTO.addUserId(pDTO, userId);

            log.info("nDTO : " + nDTO);

            // 게시글 등록하기위한 비즈니스 로직을 호출
            noticeService.insertNoticeInfo(nDTO);

            // 저장이 완료되면 사용자에게 보여줄 메시지
            msg = "등록되었습니다.";
            res = 1;

        } catch (Exception e) {

            // 저장이 실패되면 사용자에게 보여줄 메시지
            msg = "실패하였습니다. : " + e.getMessage();
            res = 0;
            log.info(e.toString());

        } finally {
            // 결과 메시지 전달하기
            dto = MsgDTO.builder().result(res).msg(msg).build();

            log.info(this.getClass().getName() + ".noticeInsert End!");
        }

        return ResponseEntity.ok(
                CommonResponse.of(HttpStatus.OK, HttpStatus.OK.series().name(), dto));
    }

    @Operation(summary = "공지사항 수정 API", description = "공지사항 수정 및 수정결과를 제공하는 API",
            responses = {@ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Page Not Found!"),})
    @PostMapping(value = "noticeUpdate")
    public ResponseEntity<CommonResponse> noticeUpdate(@Valid @RequestBody NoticeDTO pDTO, BindingResult bindingResult,
                                                       @CookieValue(value = "${jwt.token.access.name}") String token) {

        log.info(this.getClass().getName() + ".noticeUpdate Start!");

        if (bindingResult.hasErrors()) { // Spring Validation 맞춰 잘 바인딩되었는지 체크
            return CommonResponse.getErrors(bindingResult); // 유효성 검증 결과에 따른 에러 메시지 전달

        }

        String msg = ""; // 메시지 내용
        int res = 0; // 성공 여부
        MsgDTO dto; // 결과 메시지 구조

        try {
            // UserService로부터 Token 값 받아오기
            TokenDTO tDTO = tokenAPIService.getTokenInfo(HEADER_PREFIX + token);
            log.info("TokenDTO : " + tDTO); // Token 값 출력하기

            //JWT Access 토큰으로부터 회원아이디 가져오기
            String userId = CmmUtil.nvl(tDTO.userId());

            // 반드시, 값을 받았으면, 꼭 로그를 찍어서 값이 제대로 들어오는지 파악해야함 반드시 작성할 것
            log.info("userId : " + userId);
            log.info("pDTO : " + pDTO);

            // 데이터 저장하기 위해 DTO에 저장하기
            NoticeDTO nDTO = NoticeDTO.addUserId(pDTO, userId);

            log.info("nDTO : " + nDTO);

            // 게시글 수정하기 DB
            noticeService.updateNoticeInfo(nDTO);

            msg = "수정되었습니다.";
            res = 1;

        } catch (Exception e) {
            msg = "실패하였습니다. : " + e.getMessage();
            res = 0;
            log.info(e.toString());

        } finally {

            // 결과 메시지 전달하기
            dto = MsgDTO.builder().result(res).msg(msg).build();

            log.info(this.getClass().getName() + ".noticeUpdate End!");

        }

        return ResponseEntity.ok(
                CommonResponse.of(HttpStatus.OK, HttpStatus.OK.series().name(), dto));
    }

    @Operation(summary = "공지사항 삭제 API", description = "공지사항 삭제 및 삭제결과를 제공하는 API",
            responses = {@ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "404", description = "Page Not Found!"),})
    @PostMapping(value = "noticeDelete")
    public ResponseEntity<CommonResponse> noticeDelete(@Valid @RequestBody NoticeDTO pDTO, BindingResult bindingResult) {

        log.info(this.getClass().getName() + ".noticeDelete Start!");

        if (bindingResult.hasErrors()) { // Spring Validation 맞춰 잘 바인딩되었는지 체크
            return CommonResponse.getErrors(bindingResult); // 유효성 검증 결과에 따른 에러 메시지 전달

        }

        String msg = ""; // 메시지 내용
        int res = 0; // 성공 여부
        MsgDTO dto = null; // 결과 메시지 구조

        try {
            // 반드시, 값을 받았으면, 꼭 로그를 찍어서 값이 제대로 들어오는지 파악해야함 반드시 작성할 것
            log.info("pDTO : " + pDTO);

            // 게시글 삭제하기 DB
            noticeService.deleteNoticeInfo(pDTO);

            msg = "삭제되었습니다.";
            res = 1;

        } catch (Exception e) {
            msg = "실패하였습니다. : " + e.getMessage();
            res = 0;
            log.info(e.toString());

        } finally {
            // 결과 메시지 전달하기
            dto = MsgDTO.builder().result(res).msg(msg).build();

            log.info(this.getClass().getName() + ".noticeDelete End!");

        }

        return ResponseEntity.ok(
                CommonResponse.of(HttpStatus.OK, HttpStatus.OK.series().name(), dto));
    }

}
