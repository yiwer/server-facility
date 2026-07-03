package cn.code91.facility.web.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全部期望串为 jsoup 1.18.3 实际输出(探针实证),含美化缩进与协议强制行为。
 */
@DisplayName("XssUtil - Jsoup Safelist 消毒(RV2-14 复核)")
class XssUtilTest {

    @Test
    @DisplayName("BASIC:剥 <script>(含内容)保 <b>")
    void basic_stripsScript_keepsBold() {
        assertThat(XssUtil.clean("<script>alert(1)</script><b>bold</b>", XssLevel.BASIC))
                .isEqualTo("<b>bold</b>");
    }

    @Test
    @DisplayName("NONE:剥全部标签,保留文本")
    void none_stripsAllTags_keepsText() {
        assertThat(XssUtil.clean("<b>bold</b> and <i>italic</i>", XssLevel.NONE))
                .isEqualTo("bold and italic");
    }

    @Test
    @DisplayName("层级差异:同一 <img> 输入,BASIC 全剥,BASIC_WITH_IMAGES 保留(绝对 URL src)")
    void basicWithImages_keepsAbsoluteImg_basicStripsIt() {
        String img = "<img src=\"http://example.com/x.png\">";
        assertThat(XssUtil.clean(img, XssLevel.BASIC)).isEmpty();
        assertThat(XssUtil.clean(img + "<script>bad()</script>", XssLevel.BASIC_WITH_IMAGES))
                .isEqualTo("<img src=\"http://example.com/x.png\">");
    }

    @Test
    @DisplayName("BASIC_WITH_IMAGES:相对路径 src 被协议强制剥除(标签保留为裸 <img>)")
    void basicWithImages_relativeSrcDropped() {
        // jsoup basicWithImages 对 img src 强制 http/https 协议:相对 URL 属性被清除
        assertThat(XssUtil.clean("<img src=\"x.png\"><script>bad()</script>", XssLevel.BASIC_WITH_IMAGES))
                .isEqualTo("<img>");
    }

    @Test
    @DisplayName("RELAXED:保 <table>(美化输出补 tbody)剥 <script>")
    void relaxed_keepsTable_stripsScript() {
        assertThat(XssUtil.clean("<script>x</script><table><tr><td>1</td></tr></table>", XssLevel.RELAXED))
                .isEqualTo("<table>\n <tbody>\n  <tr>\n   <td>1</td>\n  </tr>\n </tbody>\n</table>");
    }

    @Test
    @DisplayName("null → null,空串 → 空串(所有重载)")
    void nullAndEmpty_passThrough() {
        assertThat(XssUtil.clean(null)).isNull();
        assertThat(XssUtil.clean(null, XssLevel.NONE)).isNull();
        assertThat(XssUtil.clean("")).isEmpty();
        assertThat(XssUtil.clean("", XssLevel.RELAXED)).isEmpty();
    }

    @Test
    @DisplayName("isSafe:合法标签 true,script false;null/空串视为安全")
    void isSafe_trueFalse() {
        assertThat(XssUtil.isSafe("<b>ok</b>", XssLevel.BASIC)).isTrue();
        assertThat(XssUtil.isSafe("<script>x</script>", XssLevel.BASIC)).isFalse();
        assertThat(XssUtil.isSafe(null, XssLevel.BASIC)).isTrue();
        assertThat(XssUtil.isSafe("", XssLevel.NONE)).isTrue();
    }
}
