package kopo.poly.dto;

import lombok.Builder;

import java.util.List;

/**
 * Redis에 저장되는 리프레시 세션(핸들) 레코드.
 * AT 재발급에 필요한 클레임(userId, userName, roles)과
 * 보안 메타데이터(uaHash, issuedAt)를 포함한다.
 */
@Builder
public record RtSession(
        String userId,
        String userName,
        List<String> roles, // ["USER", "ADMIN"]
        String uaHash,
        String issuedAt
) {
}

