package cn.code91.facility.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Result.swap() - null-Ok 守卫 (RV2-03)")
class ResultSwapTest {

    @Test @DisplayName("Ok(v).swap() = Err(v)；Err(e).swap() = Ok(e)")
    void swapHappyPath() {
        Result<String, Integer> ok = Result.ok("v");
        assertThat(ok.swap().isErr()).isTrue();
        assertThat(ok.swap().getErr()).isEqualTo("v");

        Result<String, Integer> err = Result.err(7);
        assertThat(err.swap().isOk()).isTrue();
        assertThat(err.swap().get()).isEqualTo(7);
    }

    @Test @DisplayName("empty()/Ok(null).swap() 抛清晰 ISE，而非 NPE")
    void swapNullOkThrowsClearIse() {
        assertThatThrownBy(() -> Result.<String, Integer>empty().swap())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("null-valued Ok");
    }
}
