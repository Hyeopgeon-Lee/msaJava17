package kopo.poly.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import kopo.poly.dto.RtSession;
import kopo.poly.dto.UserInfoDTO;
import kopo.poly.service.IRefreshTokenRedisService;
import kopo.poly.util.EncryptUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * RefreshTokenRedisService
 * - Redis를 이용해 리프레시 토큰(세션 핸들)을 관리하는 서비스 구현체입니다.
 * - 사용자 인증 및 세션 관리, 도난 방지(User-Agent 해시), 세션 폐기 등 핵심 기능을 제공합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRedisService implements IRefreshTokenRedisService {

    // Redis에 접근하기 위한 템플릿 객체
    private final StringRedisTemplate redis;
    // JSON 직렬화/역직렬화를 위한 ObjectMapper
    private final ObjectMapper om = new ObjectMapper();

    /**
     * Redis 키 생성 함수
     * - 세션 핸들(handle)을 받아 Redis에 저장할 때 사용할 키를 만듭니다.
     * - 예시: rtsid:핸들값
     */
    private String key(String handle) {
        return "rtsid:" + handle;
    }

    /**
     * 권한 문자열을 리스트로 변환
     * - roles가 null 또는 빈 문자열이면 기본값 "USER"를 반환합니다.
     * - 쉼표로 구분된 권한 문자열을 List<String>으로 변환합니다.
     */
    private List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) return List.of("USER");
        return Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    // =========================
    // Issue (핸들 발급)
    // =========================

    /**
     * 세션 핸들 발급 (User-Agent 포함)
     * - 사용자 정보와 만료 시간, User-Agent를 받아 세션 핸들을 생성합니다.
     * - User-Agent 해시를 저장하여 도난 방지 기능을 강화합니다.
     * - Redis에 RtSession 객체를 JSON으로 저장합니다.
     *
     * @param user      사용자 정보 DTO
     * @param ttlSec    세션 만료 시간(초)
     * @param userAgent User-Agent 문자열(브라우저 정보 등)
     * @return 새로 발급된 세션 핸들 값
     */
    @Override
    public String issueHandle(UserInfoDTO user, long ttlSec, String userAgent) {
        String handle = UUID.randomUUID().toString().replace("-", ""); // 랜덤 핸들 생성
        String uaHash = EncryptUtil.encHashSHA256(Objects.toString(userAgent, "")); // User-Agent 해시
        List<String> roles = splitRoles(user.roles()); // 권한 목록 변환

        // RtSession 객체를 빌더 패턴으로 생성
        RtSession rec = RtSession.builder()
                .userId(user.userId())
                .userName(user.userName())
                .roles(roles)
                .uaHash(uaHash)
                .issuedAt(Instant.now().toString())
                .build();

        try {
            // Redis에 세션 정보 저장 (JSON 직렬화)
            redis.opsForValue().set(key(handle), om.writeValueAsString(rec), Duration.ofSeconds(ttlSec));
        } catch (Exception e) {
            throw new IllegalStateException("RT 세션 저장 실패", e);
        }
        return handle;
    }

    // =========================
    // Validate (핸들 검증)
    // =========================

    /**
     * 세션 핸들 검증 (User-Agent 매칭)
     * - 핸들 값과 User-Agent를 받아 Redis에서 세션 정보를 조회합니다.
     * - User-Agent 해시가 일치하지 않으면 세션을 삭제하고 null 반환(도난 방지)
     *
     * @param handle    세션 핸들 값
     * @param userAgent User-Agent 문자열
     * @return 검증된 RtSession 객체 또는 null
     */
    @Override
    public RtSession validate(String handle, String userAgent) {
        log.info("{}.validate Start!", getClass().getName());
        try {
            String raw = redis.opsForValue().get(key(handle)); // Redis에서 세션 정보 조회
            log.info("validate info | raw: {} | handle: {} | userAgent: {}", raw, handle, userAgent);

            if (raw == null) return null; // 세션 정보 없음

            RtSession rec = om.readValue(raw, RtSession.class); // JSON 역직렬화
            if (userAgent != null) {
                String nowUaHash = EncryptUtil.encHashSHA256(userAgent);
                if (!Objects.equals(rec.uaHash(), nowUaHash)) {
                    redis.delete(key(handle)); // User-Agent 불일치 시 세션 삭제
                    return null;
                }
            }
            return rec; // 검증 성공 시 세션 정보 반환
        } catch (Exception e) {
            return null;
        }
    }

    // =========================
    // Revoke (세션 폐기)
    // =========================

    /**
     * 단일 세션 핸들 폐기 (로그아웃)
     * - 특정 핸들 값에 해당하는 세션을 Redis에서 삭제합니다.
     *
     * @param handle 세션 핸들 값
     */
    @Override
    public void revokeHandle(String handle) {
        redis.delete(key(handle));
    }

    /**
     * 사용자 전체 세션 폐기 (모든 기기 로그아웃)
     * - 해당 사용자 ID로 등록된 모든 세션을 Redis에서 삭제합니다.
     * - 운영 환경에서는 인덱스 관리가 필요하며, 여기서는 전체 키를 스캔합니다.
     *
     * @param userId 사용자 ID
     */
    @Override
    public void revokeAllByUser(String userId) {
        Set<String> keys = redis.keys("rtsid:*"); // 모든 세션 키 조회
        if (keys == null || keys.isEmpty()) return;
        for (String k : keys) {
            try {
                String raw = redis.opsForValue().get(k);
                if (raw == null) continue;
                RtSession rec = om.readValue(raw, RtSession.class);
                if (userId.equals(rec.userId())) {
                    redis.delete(k); // 해당 사용자 세션 삭제
                }
            } catch (Exception ignore) {
            }
        }
    }
}
