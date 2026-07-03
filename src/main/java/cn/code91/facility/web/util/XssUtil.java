package cn.code91.facility.web.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML sanitization built on Jsoup Safelist (allowlist).
 * <p>
 * Replaces the previous blacklist-regex implementation, which was vulnerable to
 * HTML entity encoding, Unicode escape, and case-mixing bypasses.
 *
 * @since 2026-05-11 (Hatchery facility -> stele-facility migration)
 */
public final class XssUtil {

    private XssUtil() {}

    public static String clean(String html) { return clean(html, XssLevel.BASIC); }

    public static String clean(String html, XssLevel level) {
        if (html == null) return null;
        if (html.isEmpty()) return html;
        return Jsoup.clean(html, level.safelist());
    }

    public static String clean(String html, Safelist safelist) {
        if (html == null) return null;
        if (html.isEmpty()) return html;
        return Jsoup.clean(html, safelist);
    }

    public static boolean isSafe(String html, XssLevel level) {
        if (html == null || html.isEmpty()) return true;
        return Jsoup.isValid(html, level.safelist());
    }
}
