package kopo.poly.util;

import java.util.Objects;

public class CmmUtil {

	public static String nvl(String str, String chg_str) {
		return (str == null || str.isEmpty()) ? chg_str : str;
	}

	public static String nvl(String str) {
		return nvl(str, "");
	}

	public static String checked(String str, String com_str) {
		return Objects.equals(str, com_str) ? " checked" : "";
	}

	public static String checked(String[] str, String com_str) {
		if (str == null) return ""; // null 방어

		for (String s : str) {
			if (Objects.equals(s, com_str)) {
				return " checked";
			}
		}
		return "";
	}

	public static String select(String str, String com_str) {
		return Objects.equals(str, com_str) ? " selected" : "";
	}
}