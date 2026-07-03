package cn.code91.facility.web.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PageBaseResponse - 分页响应(语义对齐 BaseResponse.ok,计划决策 6)")
class PageBaseResponseTest {

    private static PageBaseResponse<String> page() {
        return PageBaseResponse.of(List.of("a", "b"), 42L, 2, 10);
    }

    @Test
    @DisplayName("of:data 即传入列表")
    void of_setsDataList() {
        assertThat(page().getData()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("of:total/pageNum/pageSize 各字段")
    void of_setsPagingFields() {
        PageBaseResponse<String> p = page();
        assertThat(p.getTotal()).isEqualTo(42L);
        assertThat(p.getPageNum()).isEqualTo(2);
        assertThat(p.getPageSize()).isEqualTo(10);
    }

    @Test
    @DisplayName("of:code=200,isSuccess true")
    void of_code200_isSuccess() {
        PageBaseResponse<String> p = page();
        assertThat(p.getCode()).isEqualTo(200);
        assertThat(p.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("of:message 与 BaseResponse.ok 一致(success)")
    void of_messageIsSuccess() {
        assertThat(page().getMessage()).isEqualTo("success");
    }

    @Test
    @DisplayName("of:description 与 BaseResponse.ok 一致(空串)")
    void of_descriptionIsEmpty() {
        assertThat(page().getDescription()).isEmpty();
    }
}
