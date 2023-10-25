package kopo.poly.jwt;

/**
 * JWT 토큰의 상태 값을 정의
 */
public enum JwtStatus {
    ACCESS, // 유효한 토큰
    DENIED, // 유효하지 않은 토근
    EXPIRED // 만료된 토큰(재발행 등 활용)
}

