package kopo.poly.util;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 날짜 및 시간 처리 유틸리티 클래스
 */
public class DateUtil {

    /**
     * 현재 시스템 시간을 주어진 포맷 형식으로 반환합니다.
     *
     * @param format 날짜 출력 형식 (예: "yyyy-MM-dd HH:mm:ss")
     * @return 현재 날짜/시간 문자열
     */
    public static String getDateTime(String format) {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(now);
    }

    /**
     * 현재 날짜를 기본 형식 (yyyy.MM.dd) 으로 반환합니다.
     *
     * @return 현재 날짜 문자열 (예: "2025.03.26")
     */
    public static String getDateTime() {
        return getDateTime("yyyy.MM.dd");
    }

    /**
     * Unix 시간(초 단위)을 기본 형식으로 변환합니다.
     *
     * @param time Unix 타임스탬프 (초)
     * @return 변환된 날짜/시간 문자열 ("yyyy-MM-dd HH:mm:ss")
     */
    public static String getLongDateTime(Integer time) {
        return getLongDateTime(time, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Unix 시간(초 단위)을 기본 형식으로 변환합니다.
     *
     * @param time Unix 타임스탬프 (Object 타입 허용)
     * @return 변환된 날짜/시간 문자열 ("yyyy-MM-dd HH:mm:ss")
     */
    public static String getLongDateTime(Object time) {
        return getLongDateTime((Integer) time, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * Unix 시간(초 단위)을 주어진 형식으로 변환합니다.
     *
     * @param time   Unix 타임스탬프 (Object 타입)
     * @param format 출력할 날짜/시간 형식
     * @return 포맷된 날짜/시간 문자열
     */
    public static String getLongDateTime(Object time, String format) {
        return getLongDateTime((Integer) time, format);
    }

    /**
     * Unix 시간(초 단위)을 주어진 형식으로 변환합니다.
     *
     * @param time   Unix 타임스탬프 (초)
     * @param format 출력할 날짜/시간 형식
     * @return 포맷된 날짜/시간 문자열
     */
    public static String getLongDateTime(Integer time, String format) {
        Instant instant = Instant.ofEpochSecond(time);
        return DateTimeFormatter.ofPattern(format)
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
}