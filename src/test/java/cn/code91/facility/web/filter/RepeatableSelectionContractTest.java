package cn.code91.facility.web.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.ErrorResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepeatableSelectionContractTest {
    @ParameterizedTest
    @CsvSource({"application/problem+json,true", "application/json,true", "application/json-unknown,false",
            "text/plain,true", "application/octet-stream,false", "APPLICATION/JSON,true"})
    void mediaTypesSelectRealFormatsWithoutPrefixLookalikes(String type, boolean repeatable) throws Exception {
        var request = new MockHttpServletRequest("POST", "/webhook");
        request.setContentType(type);
        request.setContent("body".getBytes(StandardCharsets.UTF_8));
        new RepeatableRequestFilter(new FacilityWebRepeatableRequestProperties()).doFilter(request,
                new MockHttpServletResponse(), (req, res) -> {
                    assertThat(req.getInputStream().readAllBytes()).isEqualTo("body".getBytes(StandardCharsets.UTF_8));
                    assertThat(req.getInputStream().readAllBytes()).hasSize(repeatable ? 4 : 0);
                });
    }

    @Test void malformedCharsetBecomesBadRequestBeforeReadingTheBody() {
        var request = new MockHttpServletRequest("POST", "/webhook") {
            @Override public String getContentType() { return "application/json; charset=not-a-real-charset"; }
        };
        assertThatThrownBy(() -> new RepeatableRequestFilter(new FacilityWebRepeatableRequestProperties())
                .doFilter(request, new MockHttpServletResponse(), (req, res) -> { throw new AssertionError("must reject"); }))
                .isInstanceOfSatisfying(ErrorResponseException.class, ex -> assertThat(ex.getStatusCode().value()).isEqualTo(400));
    }

    @Test void disabledIsTheOnlyWayToOptOutOfBudget() {
        var properties = new FacilityWebRepeatableRequestProperties();
        assertThat(properties.isEnabled()).isFalse();
        for (long invalid : new long[]{0, -1, Long.MIN_VALUE}) {
            properties.setMaxBodyBytes(invalid);
            assertThatThrownBy(() -> new RepeatableRequestFilter(properties)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RepeatableRequestWrapper(new MockHttpServletRequest(), invalid))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
