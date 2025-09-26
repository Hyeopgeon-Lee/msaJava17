package kopo.poly.controller.exception;

import kopo.poly.controller.response.CommonResponse;
import kopo.poly.dto.MsgDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 인증 과정에서 발생하는 예외를 통합 처리하는 전역 핸들러.
 * <p>
 * 사용 목적
 * - 컨트롤러 개별 try-catch 없이 공통 포맷(JSON)으로 실패 응답을 내려준다.
 * - 예외별로 사용자 친화적인 메시지를 제공하되, 과도한 정보 노출을 막는다.
 * <p>
 * 동작 시점
 * - AuthenticationManager.authenticate(...) 호출 중 발생하는 인증 관련 예외가
 * 컨트롤러 밖으로 전파될 때 가로채어 처리한다.
 * <p>
 * 응답 정책
 * - HTTP 상태코드: 401 Unauthorized
 * - Body: CommonResponse<MsgDTO> 포맷 사용
 * - 메시지: 예외 유형에 따라 한국어 메시지 매핑
 * <p>
 * 보안 유의사항
 * - 아이디 존재 여부, 잠금 여부 등의 내부 상태를 과도하게 노출하면 계정 추측 공격에 악용될 수 있다.
 * 필요 시 메시지를 단일화(예: "아이디 또는 비밀번호가 올바르지 않습니다.")하는 전략을 적용할 수 있다.
 */
@Slf4j
@RestControllerAdvice
public class AuthExceptionHandler {

    /**
     * 스프링 시큐리티 인증 단계에서 던져지는 모든 AuthenticationException 하위 예외를 처리한다.
     * <p>
     * 처리 대상 주요 예외
     * - BadCredentialsException : 아이디 또는 비밀번호 불일치 (기본 정책상 사용자 미존재도 이 예외로 은닉된다)
     * - LockedException         : 계정 잠금
     * - DisabledException       : 계정 비활성화
     * - 그 외 AuthenticationException: 일반적인 인증 실패
     * <p>
     * 로깅 정책
     * - 예외 클래스명만 기록하여 원인 파악에 도움을 주되, 민감한 정보는 남기지 않는다.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<CommonResponse<MsgDTO>> handleAuthentication(AuthenticationException ex) {
        // 예외 유형을 한 줄로 기록. 운영 환경에서는 필요 시 INFO로 낮추는 것도 고려
        log.warn("Authentication failed: {}", ex.getClass().getSimpleName());

        // 사용자에게 보여줄 메시지. 필요 시 메시지를 단일화하여 정보 노출을 최소화할 수 있다.
        String msg;
        if (ex instanceof BadCredentialsException) {
            msg = "아이디 또는 비밀번호가 올바르지 않습니다.";
        } else if (ex instanceof LockedException) {
            msg = "계정이 잠겨 있습니다.";
        } else if (ex instanceof DisabledException) {
            msg = "비활성화된 계정입니다.";
        } else {
            // 그 밖의 인증 실패 상황
            msg = "로그인에 실패했습니다.";
        }

        // 일반화된 응답 포맷으로 래핑
        MsgDTO dto = MsgDTO.builder()
                .result(0)     // 실패 표시
                .msg(msg)      // 사용자 노출 메시지
                .build();

        // 401 Unauthorized 로 응답
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CommonResponse.of(
                        HttpStatus.UNAUTHORIZED,
                        HttpStatus.UNAUTHORIZED.series().name(), // "CLIENT_ERROR"
                        dto));
    }
}
