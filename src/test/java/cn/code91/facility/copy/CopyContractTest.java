package cn.code91.facility.copy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CopyContractTest {
    static final class FinalData {
        static int constructions;
        final String name;
        FinalData() { name = "value-" + ++constructions; }
    }

    static final class Recursive implements CopyTrait<Recursive> {
        static int calls;
        Recursive next;
        @Override public Recursive copy() {
            if (++calls > 32) throw new AssertionError("test stopped unbounded recursion");
            return CopyUtil.autoCopy(this);
        }
    }

    static final class Chain implements CopyTrait<Chain> {
        Chain next;
        @Override public Chain copy() { return CopyUtil.autoCopy(this); }
    }

    private static Chain chain(int depth) {
        Chain result = null;
        for (int i = 0; i < depth; i++) {
            Chain node = new Chain(); node.next = result; result = node;
        }
        return result;
    }

    @Test
    void nestedReflectionHasAnExplicitDepthBudget() {
        for (int depth : new int[]{31, 32}) {
            Chain copy = CopyUtil.autoCopy(chain(depth));
            int count = 0;
            for (Chain node = copy; node != null; node = node.next) count++;
            assertThat(count).isEqualTo(depth);
        }
        assertThatThrownBy(() -> CopyUtil.autoCopy(chain(33)))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("depth");
        assertThat(CopyUtil.autoCopy(chain(1))).isNotNull();
    }

    @Test
    void recursiveCopyTraitCycleIsRejectedAndLaterIndependentCopiesStillWork() {
        Recursive source = new Recursive();
        source.next = source;
        Recursive.calls = 0;
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("cycle");
        source.next = null;
        assertThat(CopyUtil.autoCopy(source)).isNotSameAs(source);
    }

    @Test
    void finalFieldsAreRejectedBeforeConstructingOrMutatingATarget() {
        FinalData source = new FinalData();
        int constructions = FinalData.constructions;
        assertThatThrownBy(() -> CopyUtil.autoCopy(source))
                .isInstanceOf(CopyUtil.CopyException.class)
                .hasMessageContaining("final");
        assertThat(FinalData.constructions).isEqualTo(constructions);
        assertThat(source.name).isEqualTo("value-" + constructions);
    }
}
