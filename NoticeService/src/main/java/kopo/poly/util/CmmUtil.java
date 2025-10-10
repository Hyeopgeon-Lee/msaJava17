package kopo.poly.util;

/**
 * CmmUtil 클래스는 공통적으로 자주 사용하는 유틸리티 메서드를 제공합니다.
 * 현재는 문자열이 null 또는 빈 값일 때 기본값으로 대체하는 nvl 메서드를 제공합니다.
 */
public class CmmUtil {

    /**
     * 입력된 문자열이 null 또는 빈 값이면 지정한 기본값(chg_str)으로 대체합니다.
     *
     * @param str     검사할 문자열
     * @param chg_str str이 null 또는 빈 값일 때 반환할 기본값
     * @return str이 null 또는 빈 값이면 chg_str, 아니면 str
     */
    public static String nvl(String str, String chg_str) {
        String res;

        if (str == null) {
            res = chg_str;
        } else if (str.isEmpty()) {
            res = chg_str;
        } else {
            res = str;
        }
        return res;
    }

    /**
     * 입력된 문자열이 null 또는 빈 값이면 빈 문자열("")로 대체합니다.
     *
     * @param str 검사할 문자열
     * @return str이 null 또는 빈 값이면 "", 아니면 str
     */
    public static String nvl(String str) {
        return nvl(str, "");
    }

}
