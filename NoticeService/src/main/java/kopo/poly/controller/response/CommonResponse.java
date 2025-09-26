package kopo.poly.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommonResponse<T> {

    /**
     * HTTP 상태코드 숫자값 (e.g., 200, 400)
     */
    private final int status;

    /**
     * 사람이 읽을 메시지 (e.g., OK, VALIDATION_ERROR, etc.)
     */
    private final String message;

    /**
     * 실제 페이로드
     */
    private final T data;

    /**
     * 요청 경로 (디버깅/로깅 편의)
     */
    private final String path;

    /**
     * 응답 시각
     */
    private final Instant timestamp;

    /* ------------------------------------------------------------------------------------
   내부 유틸: 현재 요청 경로
   ------------------------------------------------------------------------------------ */
    private static String currentPath() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            HttpServletRequest req = attrs.getRequest();
            return req.getRequestURI();
        } catch (Exception ignore) {
            return null;
        }
    }

    @Builder
    private CommonResponse(int status, String message, T data, String path, Instant timestamp) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.path = path;
        this.timestamp = timestamp;
    }

    /* ------------------------------------------------------------------------------------
       기본 팩토리: 기존 컨트롤러와 호환 (HttpStatus + message + data)
       ------------------------------------------------------------------------------------ */
    public static <T> CommonResponse<T> of(HttpStatus httpStatus, String message, T data) {
        return CommonResponse.<T>builder()
                .status(httpStatus.value())
                .message(message)
                .data(data)
                .path(currentPath())
                .timestamp(Instant.now())
                .build();
    }

    /* ------------------------------------------------------------------------------------
       편의 메서드: 컨트롤러에서 더 간결하게 사용
       ------------------------------------------------------------------------------------ */
    public static <T> ResponseEntity<CommonResponse<T>> ok(T data) {
        return ResponseEntity.ok(
                of(HttpStatus.OK, HttpStatus.OK.series().name(), data)
        );
    }

    public static <T> ResponseEntity<CommonResponse<T>> status(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(of(status, message, data));
    }

    public static <T> ResponseEntity<CommonResponse<T>> badRequest(String message, T data) {
        return status(HttpStatus.BAD_REQUEST, message, data);
    }

    public static <T> ResponseEntity<CommonResponse<T>> error(HttpStatus status, String message) {
        return status(status, message, null);
    }

    /* ------------------------------------------------------------------------------------
       BindingResult → 에러 문자열 리스트로 변환 (제네릭 안전)
       ------------------------------------------------------------------------------------ */
    public static ResponseEntity<CommonResponse<List<String>>> getErrors(BindingResult bindingResult) {
        List<String> errors = bindingResult.getAllErrors().stream()
                .map(e -> (e.getObjectName() != null ? e.getObjectName() + ": " : "") +
                        (e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid"))
                .collect(Collectors.toList());

        return ResponseEntity.badRequest().body(
                of(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.series().name(), errors)
        );
    }

}
