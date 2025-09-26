package kopo.poly.util;

/**
 * CmmUtil
 * -------------------------------------------------------------
 * 자주 사용하는 문자열 관련 유틸리티 메서드를 모아둔 클래스입니다.
 */
public class CmmUtil {
    /**
     * nvl: null 또는 빈 문자열을 안전하게 원하는 값으로 바꿔줍니다.
     * 예시: nvl(null, "기본값") → "기본값"
     * nvl("", "대체값") → "대체값"
     * nvl("abc", "대체값") → "abc"
     *
     * @param str     검사할 문자열
     * @param chg_str str이 null 또는 빈 문자열일 때 대신 쓸 값
     * @return null/빈문자면 chg_str, 아니면 원래 값
     */
    public static String nvl(String str, String chg_str) {
        // 삼항 연산자를 사용해 코드 간결화
        return (str == null || str.isEmpty()) ? chg_str : str;
    }

    /**
     * nvl: 위의 nvl을 더 간단하게 쓸 수 있게 오버로딩
     * 예시: nvl(null) → ""
     * nvl("abc") → "abc"
     *
     * @param str 검사할 문자열
     * @return null/빈문자면 "", 아니면 원래 값
     */
    public static String nvl(String str) {
        return nvl(str, "");
    }

}
