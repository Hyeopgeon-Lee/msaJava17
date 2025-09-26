package kopo.poly.handler;

import kopo.poly.dto.MsgDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * LoginServerAuthenticationEntryPoint
 * --------------------------------------------------------------------
 * 역할
 * - Spring Security에서 "인증(Authentication)" 단계에서 실패했을 때 호출된다.
 * - 즉, 클라이언트가 인증 정보(토큰 등)를 전혀 주지 않았거나, 토큰이 만료/검증 실패한 경우
 * AuthenticationException 이 발생하며, 이를 본 핸들러가 받아서 응답을 내려준다.
 * <p>
 * 반환되는 응답
 * - HTTP Status: 401 Unauthorized
 * - Body(JSON): { "result": 100, "msg": "인증이 필요합니다." }
 * * result=100 → 도메인 표준 코드(프로젝트에서 401 상황을 구분하는 값)
 * * msg        → ErrorMsg.ERR100 의 값("인증이 필요합니다.")
 * <p>
 * 401 vs 403 구분
 * - 401 Unauthorized: "미인증" 상태 → 이 핸들러(LoginServerAuthenticationEntryPoint)가 처리
 * - 403 Forbidden: "무권한"(인증은 되었으나 권한 부족) → AccessDeniedHandler가 처리
 * <p>
 * 주의
 * - 민감 정보(실제 인증 실패 원인, 내부 예외 메시지)는 응답에 노출하지 않는다.
 * (예: "JWT signature does not match" 같은 메시지는 보안상 위험 → 로그로만 남기고 응답은 단순화)
 * - 여기서는 AuthenticationException 자체는 사용하지 않고, 표준 메시지/코드만 내려준다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    // JSON 응답을 일관된 포맷으로 작성해주는 유틸리티
    private final JsonResponseWriter jsonResponseWriter;

    /**
     * 인증 실패 시 호출.
     *
     * @param exchange WebFlux 서버 교환 객체(요청/응답 컨텍스트)
     * @param ex       AuthenticationException (실제 실패 원인: 토큰 만료, 부재, 파싱 실패 등)
     * @return 논블로킹 Mono<Void> (응답 전송 완료 시점)
     */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {

        // 도메인 표준 응답 DTO 구성
        MsgDTO data = MsgDTO.builder()
                .result(100)                         // 프로젝트 표준 코드: "인증 필요"
                .msg(ErrorMsg.ERR100.getValue())     // 사용자 친화적 메시지: "인증이 필요합니다."
                .build();

        // JSON 응답 작성
        // - HTTP 상태: 401 Unauthorized
        // - 메시지: "CLIENT_ERROR" (HttpStatus.UNAUTHORIZED.series().name())
        return jsonResponseWriter.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                data,
                HttpStatus.UNAUTHORIZED.series().name() // "CLIENT_ERROR"
        );
    }
}
