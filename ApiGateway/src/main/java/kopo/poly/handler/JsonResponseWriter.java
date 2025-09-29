package kopo.poly.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JsonResponseWriter
 * ---------------------------------------------------------------------
 * 역할
 * - 리액티브(WebFlux) 환경에서 공통 JSON 응답 바디를 생성하여 클라이언트로 전송한다.
 * - 컨트롤러/핸들러에서 예외/실패를 표준 포맷으로 내려줄 때 사용.
 * <p>
 * 응답 포맷(권장 공통 스키마)
 * {
 * "status":    <HTTP 상태 코드 숫자>      // 예: 401, 403, 200 ...
 * "message":   "<상태 설명/추적 태그>",   // 예: "CLIENT_ERROR", "UNAUTHORIZED" 등
 * "data":      <도메인 페이로드 객체>,    // 실패 시 도메인 표준 오류 DTO(예: MsgDTO)
 * "path":      "<요청 경로>",             // 예: /notice/v1/noticeUpdate
 * "timestamp": "<ISO-8601 시각>"          // 예: 2025-09-22T12:34:56Z
 * }
 * <p>
 * 설계 포인트
 * - Content-Type을 application/json;charset=UTF-8 로 명시(클라이언트 파싱 안정성).
 * - 직렬화 실패(JsonProcessingException) 시 최소한의 Fallback JSON 문자열을 내려 안정성 확보.
 * - 응답 바디는 LinkedHashMap 으로 구성하여 키 순서 유지(가독성/디버깅 편의).
 * - write(...) 메서드는 논블로킹 I/O를 사용하고 Mono<Void>를 그대로 리턴하여 리액티브 체인을 유지.
 * <p>
 * 주의/운영 팁
 * - "이미 커밋된 응답"에 다시 쓰려고 하면(이중 쓰기) 예외가 발생할 수 있으므로,
 * 상위 레이어에서 write 호출이 한 번만 일어나도록 흐름을 설계(전역 예외 핸들러에서 일괄 처리 권장).
 * - 에러 응답 캐싱 방지 필요 시, Cache-Control/Pragma 헤더를 set 해도 좋다(옵션, 아래 주석 참조).
 * - 민감정보(스택트레이스/내부키 등)는 data에 포함시키지 말 것(로그에만 남기고 응답은 안전하게).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonResponseWriter {

    // Jackson ObjectMapper는 스레드-세이프(재사용 권장)
    private final ObjectMapper objectMapper;

    /**
     * 공통 JSON 응답 쓰기
     *
     * @param exchange 요청/응답 컨텍스트
     * @param status   HTTP 상태 코드(예: 401, 403, 200)
     * @param data     응답 바디의 "data" 필드(도메인 DTO/Map/원시값 등 직렬화 가능한 객체)
     * @param message  응답 바디의 "message" 필드(없거나 공백이면 status.series().name()으로 대체)
     * @return 논블로킹 응답 전송 Mono
     */
    public Mono<Void> write(ServerWebExchange exchange,
                            HttpStatus status,
                            Object data,
                            String message) {

        ServerHttpResponse res = exchange.getResponse();

        String origin = exchange.getRequest().getHeaders().getOrigin();

        if (origin != null && !origin.isBlank()) {
            res.getHeaders().set("Access-Control-Allow-Origin", origin);
            res.getHeaders().set("Access-Control-Allow-Credentials", "true");
            // 중복 판단/캐시를 위해 Vary 헤더 추가
            res.getHeaders().add("Vary", "Origin");
            res.getHeaders().add("Vary", "Access-Control-Request-Method");
            res.getHeaders().add("Vary", "Access-Control-Request-Headers");
        }

        // 기존 코드 그대로
        res.setStatusCode(status);
        res.getHeaders().set(
                HttpHeaders.CONTENT_TYPE,
                MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("message", (message != null && !message.isBlank()) ? message : status.series().name());
        body.put("data", data);
        body.put("path", exchange.getRequest().getPath().value());
        body.put("timestamp", Instant.now().toString());

        try {
            // 3) JSON 직렬화
            byte[] bytes = objectMapper.writeValueAsBytes(body);

            // 4) DataBuffer로 감싸 논블로킹 write
            DataBuffer buffer = res.bufferFactory().wrap(bytes);
            return res.writeWith(Mono.just(buffer));

        } catch (JsonProcessingException e) {
            // 직렬화 실패 시: 최소 응답으로 Fallback
            log.warn("JsonResponseWriter serialize error", e);

            String fb = String.format(
                    "{\"status\":%d,\"message\":\"%s\"}",
                    status.value(), status.name()
            );
            byte[] fallback = fb.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = res.bufferFactory().wrap(fallback);
            return res.writeWith(Mono.just(buffer));
        }
    }
}
