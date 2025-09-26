package kopo.poly.service;

import kopo.poly.dto.RtSession;      // ← RtSession 패키지를 실제 위치에 맞게 조정하세요
import kopo.poly.dto.UserInfoDTO;    // (예: kopo.poly.service.model.RtSession)

/**
 * Refresh 토큰을 JWT로 발급하지 않고
 * Redis에 저장되는 세션 핸들(opaque handle)로 관리하기 위한 계약(Interface).
 * <p>
 * - 로그인:  issueHandle(user, ttl[, userAgent]) → handle 반환 → 쿠키에 handle 저장
 * - 재발급:  rotate(oldHandle, ttl[, userAgent]) → newHandle 반환 → (필요 시) get(newHandle)로 AT 클레임 확보
 * - 로그아웃: revokeHandle(handle) 또는 revokeAllByUser(userId)
 */
public interface IRefreshTokenRedisService {

    /* ============== Issue(발급) ============== */

    /**
     * UA(User-Agent) 없이 간단 발급
     */
    String issueHandle(UserInfoDTO user, long ttlSec);

    /**
     * UA 포함 발급(도난 방지 보조 수단)
     */
    String issueHandle(UserInfoDTO user, long ttlSec, String userAgent);


    /* ============== Validate(검증) ============== */

    /**
     * 핸들 존재/만료만 확인(UA 검증 없음)
     */
    RtSession validate(String handle);

    /**
     * 핸들 + UA 매칭 검증(불일치 시 세션 제거 후 null 반환)
     */
    RtSession validate(String handle, String userAgent);


    /* ============== Rotate(회전) ============== */

    /**
     * UA 없이 회전(기존 세션 삭제 → 새 핸들 발급)
     */
    String rotate(String oldHandle, long ttlSec);

    /**
     * UA 매칭 포함 회전
     */
    String rotate(String oldHandle, long ttlSec, String userAgent);


    /* ============== 조회/폐기 ============== */

    /**
     * 핸들로 세션 조회(AT 생성용 클레임 로드)
     */
    RtSession get(String handle);

    /**
     * 단일 세션 폐기(현재 기기 로그아웃)
     */
    void revokeHandle(String handle);

    /**
     * 사용자 전체 세션 폐기(모든 기기 로그아웃)
     */
    void revokeAllByUser(String userId);
}
