package cn.code91.facility.web.util;

import org.junit.jupiter.api.Test;
import org.jsoup.safety.Safelist;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlPolicyTest {
    @Test
    void boundaryUnicodeAndExplicitCustomPolicyKeepTheirDocumentedMeaning() {
        for (int size : new int[]{262143, 262144}) {
            String raw = "汉".repeat(size);
            assertThat(XssUtil.clean(raw)).isEqualTo(raw);
            assertThat(XssUtil.isSafe(raw, XssLevel.NONE)).isTrue();
        }
        assertThat(XssUtil.clean(null, Safelist.none())).isNull();
        assertThat(XssUtil.clean("", Safelist.none())).isEmpty();
        assertThat(XssUtil.clean("<b>ok</b><i>text</i>", new Safelist().addTags("b")))
                .isEqualTo("<b>ok</b>text");
        assertThat(XssUtil.clean("<img src='https://example.com/a.png' onerror='PRIVATE()'>", XssLevel.BASIC_WITH_IMAGES))
                .isEqualTo("<img src=\"https://example.com/a.png\">");
        assertThat(XssUtil.clean("<svg><script>PRIVATE()</script></svg><b>ok</b>"))
                .isEqualTo("<b>ok</b>");
    }

    @Test
    void policyIsRequiredEvenWhenThereIsNoHtmlToClean() {
        assertThatThrownBy(() -> XssUtil.clean(null, (XssLevel) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> XssUtil.clean("", (Safelist) null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> XssUtil.isSafe(null, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void everyEntryPointRefusesHtmlBeyondTheDocumentedInputBudget() {
        String html = "x".repeat(262145);
        assertThatThrownBy(() -> XssUtil.clean(html)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> XssUtil.clean(html, XssLevel.NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> XssUtil.clean(html, Safelist.none())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> XssUtil.isSafe(html, XssLevel.NONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anHttpSchemeWithoutAHostIsRemovedFromTheHtmlFragment() {
        assertThat(XssUtil.clean("<a href='http://'>go</a>"))
                .isEqualTo("<a rel=\"nofollow\">go</a>");
    }
}
