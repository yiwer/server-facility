package cn.code91.facility.web.argument;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageQuery.getOffset() - long 防溢出 (RV2-16)")
class PageQueryTest {

    @Test @DisplayName("常规 offset 正确")
    void normalOffset() {
        PageQuery q = new PageQuery();
        q.setPageNum(3);
        q.setPageSize(10);
        assertThat(q.getOffset()).isEqualTo(20L);
    }

    @Test @DisplayName("大 pageNum 不整型溢出为负")
    void largePageNumNoOverflow() {
        PageQuery q = new PageQuery();
        q.setPageNum(20_000_000);
        q.setPageSize(200);
        assertThat(q.getOffset()).isEqualTo(3_999_999_800L); // (20000000-1)*200，超 int 上限
    }
}
