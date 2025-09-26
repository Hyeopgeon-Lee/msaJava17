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

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenRedisService implements IRefreshTokenRedisService {

    private final StringRedisTemplate redis;
    private final ObjectMapper om = new ObjectMapper();

    private String key(String handle) {
        return "rtsid:" + handle;
    }

    // =========================
    // Issue (발급)
    // =========================

    /**
     * UA 없이 간단 발급
     */
    public String issueHandle(UserInfoDTO user, long ttlSec) {
        return issueHandle(user, ttlSec, null);
    }

    /**
     * UA 포함 발급(도난 방지 강화)
     */
    public String issueHandle(UserInfoDTO user, long ttlSec, String userAgent) {
        String handle = UUID.randomUUID().toString().replace("-", "");
        String uaHash = EncryptUtil.encHashSHA256(Objects.toString(userAgent, ""));
        List<String> roles = splitRoles(user.roles());

        RtSession rec = new RtSession(
                user.userId(),
                user.userName(),
                roles,
                uaHash,
                Instant.now().toString()
        );

        try {
            redis.opsForValue().set(
                    key(handle),
                    om.writeValueAsString(rec),
                    Duration.ofSeconds(ttlSec)
            );
        } catch (Exception e) {
            throw new IllegalStateException("RT 세션 저장 실패", e);
        }
        return handle;
    }

    // =========================
    // Validate (검증)
    // =========================

    public RtSession validate(String handle) {
        return validate(handle, null);
    }

    /**
     * 핸들 검증(UA 매칭 포함). 불일치 시 세션 제거 후 null 반환.
     */
    public RtSession validate(String handle, String userAgent) {

        log.info("{}.validate Start!", getClass().getName());

        try {
            String raw = redis.opsForValue().get(key(handle));

            log.info("raw : {}", raw);
            log.info("handle : {}", handle);
            log.info("userAgent : {}", userAgent);

            if (raw == null) return null;

            RtSession rec = om.readValue(raw, RtSession.class);
            if (userAgent != null) {
                String nowUaHash = EncryptUtil.encHashSHA256(userAgent);
                if (!Objects.equals(rec.uaHash(), nowUaHash)) {
                    redis.delete(key(handle)); // 탈취 의심 시 제거
                    return null;
                }
            }
            return rec;
        } catch (Exception e) {
            return null;
        }
    }

    // =========================
    // Rotate (회전)
    // =========================

    public String rotate(String oldHandle, long ttlSec) {
        return rotate(oldHandle, ttlSec, null);
    }

    public String rotate(String oldHandle, long ttlSec, String userAgent) {
        RtSession rec = validate(oldHandle, userAgent);
        if (rec == null) {
            throw new IllegalStateException("유효하지 않은 RT 세션(만료/위변조/UA불일치)");
        }
        // 기존 세션 제거
        redis.delete(key(oldHandle));

        // 기존 클레임 그대로 새 세션 발급
        UserInfoDTO user = UserInfoDTO.builder()
                .userId(rec.userId())
                .userName(rec.userName())
                .roles(String.join(",", rec.roles()))
                .build();

        return issueHandle(user, ttlSec, userAgent);
    }

    // =========================
    // Get / Revoke
    // =========================

    public RtSession get(String handle) {
        try {
            String raw = redis.opsForValue().get(key(handle));
            if (raw == null) return null;
            return om.readValue(raw, RtSession.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void revokeHandle(String handle) {
        redis.delete(key(handle));
    }

    /**
     * 사용자 전체 세션 폐기(로그아웃-올킬)
     * - 학습/로컬용: KEYS 스캔 (운영은 인덱스 유지 권장)
     */
    public void revokeAllByUser(String userId) {
        Set<String> keys = redis.keys("rtsid:*");
        if (keys == null || keys.isEmpty()) return;

        for (String k : keys) {
            try {
                String raw = redis.opsForValue().get(k);
                if (raw == null) continue;
                RtSession rec = om.readValue(raw, RtSession.class);
                if (userId.equals(rec.userId())) {
                    redis.delete(k);
                }
            } catch (Exception ignore) {
            }
        }
    }

    // =========================
    // Helpers
    // =========================

    private static List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) return List.of("USER");
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
