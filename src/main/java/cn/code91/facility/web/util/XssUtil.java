package cn.code91.facility.web.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import java.util.Objects;

/**
 * Explicit HTML body-fragment cleaning using jsoup Safelist. Never applied automatically to requests.
 * Input is limited to 262144 UTF-16 units before parsing. Null HTML passes through;
 * policy arguments are required. This is not an encoder for JavaScript, CSS, URL,
 * JSON or HTML-attribute contexts. The application owns output-context encoding.
 *
 * @since 2026-05-11 (Hatchery facility -> stele-facility migration)
 */
public final class XssUtil {

    private static final int MAX_HTML_LENGTH = 262144;

    private XssUtil() {}

    public static String clean(String html) { return clean(html, XssLevel.BASIC); }

    public static String clean(String html, XssLevel level) {
        Objects.requireNonNull(level, "level");
        requireBoundedInput(html);
        if (html == null) return null;
        if (html.isEmpty()) return html;
        return Jsoup.clean(html, level.safelist());
    }

    public static String clean(String html, Safelist safelist) {
        Objects.requireNonNull(safelist, "safelist");
        requireBoundedInput(html);
        if (html == null) return null;
        if (html.isEmpty()) return html;
        return Jsoup.clean(html, safelist);
    }

    /** Safelist validity only; null/empty means no content to validate, not a trust credential. */
    public static boolean isSafe(String html, XssLevel level) {
        Objects.requireNonNull(level, "level");
        requireBoundedInput(html);
        if (html == null || html.isEmpty()) return true;
        return Jsoup.isValid(html, level.safelist());
    }

    private static void requireBoundedInput(String html) {
        if (html != null && html.length() > MAX_HTML_LENGTH) {
            throw new IllegalArgumentException("HTML fragment exceeds 262144 UTF-16 units");
        }
    }
}
