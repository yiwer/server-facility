package cn.code91.facility.id.support;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ClockBackwardsExceptionTest {

    @Test
    void exposesDelta() {
        ClockBackwardsException ex = new ClockBackwardsException(42L);
        assertThat(ex.getDeltaMillis()).isEqualTo(42L);
        assertThat(ex.getMessage()).contains("42");
    }
}
