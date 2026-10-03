package cn.code91.facility.common;

import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.WrappedError;
import org.junit.jupiter.api.Test;

import java.util.AbstractList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** Regressions first driven through the isolated CoreConsumer; these also participate in JaCoCo. */
class CoreValueBoundaryTest {
    @Test
    void collectionCallbacksAreRequiredBeforeTestingForEmptyData() {
        assertThatNullPointerException().isThrownBy(() -> Collects.toMap(List.of(), null));
        assertThatNullPointerException().isThrownBy(() -> Collects.toMap(null, null, value -> value));
        assertThatNullPointerException().isThrownBy(() -> Collects.toMap(List.of(), value -> value, null));
        assertThatNullPointerException().isThrownBy(() -> Collects.safelyMappingAndJoin(null));
        assertThatNullPointerException().isThrownBy(() -> Collects.mapNonNull(null, null));
        assertThat(Collects.mapNonNull(List.of(), value -> { throw new AssertionError("must remain lazy"); })).isEmpty();
    }

    @Test
    void capacitySaturatesInsteadOfWrappingAtItsIntegerBoundary() {
        assertThat(Collects.calculateCapacity(1_610_612_734)).isEqualTo(Integer.MAX_VALUE);
        assertThat(Collects.calculateCapacity(1_610_612_735)).isEqualTo(Integer.MAX_VALUE);
        assertThat(Collects.calculateCapacity(Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void combinedListSizeOverflowIsRejectedBeforeMaterializingInputs() {
        List<String> virtual = new AbstractList<>() {
            @Override public int size() { return Integer.MAX_VALUE; }
            @Override public String get(int index) { throw new AssertionError("must not traverse"); }
            @Override public Object[] toArray() { throw new AssertionError("must not materialize"); }
        };
        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> Collects.safelyJoin(virtual, virtual, List.of("a", "b", "c")));
    }

    @Test
    void typedArgumentsRequireTheirTypeEvenWhenTheDataIsNull() {
        var error = WrappedError.ofWithArgs(FacilityErrorType.JSON_SERIALIZE_ERROR, (Object) null);
        assertThatNullPointerException().isThrownBy(() -> error.getArg(0, null));
        assertThat(error.getArg(0, String.class)).isNull();
    }
}
