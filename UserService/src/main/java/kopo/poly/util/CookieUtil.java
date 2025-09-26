package kopo.poly.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class CookieUtil {

    private CookieUtil() {
    }

    /* -------------------- Add (HttpOnly) -------------------- */

    public static void addHttpOnlyCookie(HttpServletResponse res,
                                         String name,
                                         String value,
                                         boolean secure) {
        addHttpOnlyCookie(res, name, value, secure, -1, "Lax", null, "/");
    }

    public static void addHttpOnlyCookie(HttpServletResponse res,
                                         String name,
                                         String value,
                                         boolean secure,
                                         long maxAgeSec) {
        addHttpOnlyCookie(res, name, value, secure, maxAgeSec, "Lax", null, "/");
    }

    public static void addHttpOnlyCookie(HttpServletResponse res,
                                         String name,
                                         String value,
                                         boolean secure,
                                         long maxAgeSec,
                                         String sameSite,
                                         String domain,
                                         String path) {
        boolean finalSecure = secure || "none".equalsIgnoreCase(sameSite); // SameSite=None이면 Secure 강제

        // ✔ 타입 교정: ResponseCookie.ResponseCookieBuilder (또는 var)
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(finalSecure)
                .path(path == null || path.isBlank() ? "/" : path)
                .sameSite(sameSite == null || sameSite.isBlank() ? "Lax" : sameSite);

        if (domain != null && !domain.isBlank()) b.domain(domain);
        if (maxAgeSec >= 0) b.maxAge(maxAgeSec); // 음수면 Max-Age 미설정(세션 쿠키)

        res.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    /* -------------------- Delete -------------------- */

    public static void deleteCookie(HttpServletResponse res,
                                    String name,
                                    boolean secure) {
        deleteCookie(res, name, secure, null, "/");
    }

    public static void deleteCookie(HttpServletResponse res,
                                    String name,
                                    boolean secure,
                                    String domain,
                                    String path) {

        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .path(path == null || path.isBlank() ? "/" : path)
                .sameSite("Lax")
                .maxAge(0); // 즉시 만료

        if (domain != null && !domain.isBlank()) b.domain(domain);

        res.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    /* -------------------- Read -------------------- */

    public static String readCookie(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
