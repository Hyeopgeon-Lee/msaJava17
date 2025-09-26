package kopo.poly.handler;

import kopo.poly.dto.MsgDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * AccessDeniedHandler
 * --------------------------------------------------------------------
 * 역할
 * - 인가(Authorization) 단계에서 권한 부족으로 요청이 거부될 때(= 403 FORBIDDEN),
 * 예외를 JSON 바디와 함께 표준화된 형식으로 내려주는 리액티브 핸들러.
 * <p>
 * 언제 호출되나?
 * - Spring Security가 인증은 됐으나(Principal 존재) 접근 권한이 부족할 때
 * AccessDeniedException을 발생시키며, 그 처리를 이 핸들러가 담당한다.
 * (예: hasRole("USER") 가 필요한 엔드포인트에 ROLE_GUEST로 접근)
 * <p>
 * 401과의 구분
 * - 401 UNAUTHORIZED: "미인증" (토큰 없음/만료/검증 실패 등) → AuthenticationEntryPoint가 처리
 * - 403 FORBIDDEN  : "무권한" (인증은 됐지만 권한(role/authority) 부족) → 본 핸들러가 처리
 * <p>
 * 응답 포맷
 * - HTTP Status: 403 (FORBIDDEN)
 * - Body(JSON): { "result": 600, "msg": ErrorMsg.ERR600.getValue() }
 * * result=600은 도메인 표준 에러코드(권한 부족)를 의미(프로젝트 컨벤션)
 * * 구체 사유(권한명, 정책 등)는 보안상 노출하지 않고, 사용자 친화적 메시지만 제공
 * <p>
 * 기타
 * - 실제 쓰기(write) 동작은 JsonResponseWriter가 담당(상태코드/헤더/바디 일괄 처리)
 * - 리액티브(논블로킹) 흐름을 깨지 않도록 Mono<Void>를 그대로 리턴
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessDeniedHandler implements ServerAccessDeniedHandler {

    // 공통 JSON 응답 작성 유틸(상태코드/Content-Type/바디 직렬화 등 일관 처리)
    private final JsonResponseWriter jsonResponseWriter;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {

        // (선택) 운영 시 원인 파악을 위한 경로/요청식별자 로그를 남기고 싶다면 아래와 같이 사용
        // log.warn("[403] Access denied. path={}, reason={}",
        //          exchange.getRequest().getPath(), ex.getMessage());

        // 표준 에러 응답 바디 구성
        MsgDTO data = MsgDTO.builder()
                .result(600)                         // 도메인 표준 코드: 권한 부족
                .msg(ErrorMsg.ERR600.getValue())     // 사용자 노출용 메시지(민감정보 배제)
                .build();

        // JsonResponseWriter:
        // - HTTP 403 상태코드 세팅
        // - Content-Type: application/json
        // - 바디에 data 직렬화
        // - 마지막 인자는 로그/추적용 태그(예: "CLIENT_ERROR")
        return jsonResponseWriter.write(
                exchange,
                HttpStatus.FORBIDDEN,
                data,
                HttpStatus.FORBIDDEN.series().name() // "CLIENT_ERROR"
        );
    }
}
