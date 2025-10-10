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

    // HTTP 상태코드
    private final int status;
    // 응답 메시지
    private final String message;
    // 응답 데이터
    private final T data;
    // 요청 경로
    private final String path;
    // 응답 시각
    private final Instant timestamp;

    // 현재 요청 경로 반환
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

    // CommonResponse 객체 생성
    public static <T> CommonResponse<T> of(HttpStatus httpStatus, String message, T data) {
        return CommonResponse.<T>builder()
                .status(httpStatus.value())
                .message(message)
                .data(data)
                .path(currentPath())
                .timestamp(Instant.now())
                .build();
    }

    // 200 OK 응답
    public static <T> ResponseEntity<CommonResponse<T>> ok(T data) {
        return ResponseEntity.ok(
                of(HttpStatus.OK, HttpStatus.OK.series().name(), data)
        );
    }

    // 커스텀 상태 응답
    public static <T> ResponseEntity<CommonResponse<T>> status(HttpStatus status, String message, T data) {
        return ResponseEntity.status(status).body(of(status, message, data));
    }

    // 400 Bad Request 응답
    public static <T> ResponseEntity<CommonResponse<T>> badRequest(String message, T data) {
        return status(HttpStatus.BAD_REQUEST, message, data);
    }

    // 에러 응답
    public static <T> ResponseEntity<CommonResponse<T>> error(HttpStatus status, String message) {
        return status(status, message, null);
    }

    // 바인딩 에러 리스트 반환
    public static ResponseEntity<CommonResponse<List<String>>> getErrors(BindingResult bindingResult) {
        List<String> errors = bindingResult.getAllErrors().stream()
                .map(e -> e.getObjectName() + ": " + (e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid"))
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(
                of(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.series().name(), errors)
        );
    }
}
