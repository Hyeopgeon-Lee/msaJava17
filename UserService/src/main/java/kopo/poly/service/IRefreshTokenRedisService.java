package kopo.poly.service;

import kopo.poly.dto.RtSession;
import kopo.poly.dto.UserInfoDTO;

/**
 * Refresh 토큰을 Redis 세션 핸들로 관리하기 위한 최소 인터페이스
 * 실제로 사용하는 메서드만 남기고, 불필요한 함수는 모두 제거함
 */
public interface IRefreshTokenRedisService {

    /**
     * UA 포함 발급(도난 방지 보조 수단)
     */
    String issueHandle(UserInfoDTO user, long ttlSec, String userAgent);

    /**
     * 핸들 + UA 매칭 검증(불일치 시 세션 제거 후 null 반환)
     */
    RtSession validate(String handle, String userAgent);

    /**
     * 단일 세션 폐기(현재 기기 로그아웃)
     */
    void revokeHandle(String handle);

    /**
     * 사용자 전체 세션 폐기(모든 기기 로그아웃)
     */
    void revokeAllByUser(String userId);
}
