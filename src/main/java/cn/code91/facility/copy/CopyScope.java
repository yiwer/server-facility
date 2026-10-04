package cn.code91.facility.copy;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Per-call work accounting, shared only by synchronous nested library calls. */
final class CopyScope implements AutoCloseable {
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private final State state;
    private final Object source;

    private CopyScope(State state, Object source) { this.state = state; this.source = source; }

    static CopyScope open(Object source) {
        checkInterrupted();
        State state = CURRENT.get();
        if (state == null) state = new State();
        if (state.active.size() >= 32) throw new CopyUtil.CopyException("copy depth exceeds 32; use explicit mapping");
        if (!state.active.add(source)) throw new CopyUtil.CopyException("copy cycle is unsupported; use explicit mapping");
        CURRENT.set(state);
        return new CopyScope(state, source);
    }

    void checkSize(int size) {
        checkInterrupted();
        if (size < 0 || size > state.remaining) throw new CopyUtil.CopyException("copy work budget exceeds 10000");
    }

    void take(int size) {
        checkSize(size);
        state.remaining -= size;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CopyUtil.CopyException("copy interrupted");
    }

    @Override public void close() {
        state.active.remove(source);
        if (state.active.isEmpty()) CURRENT.remove();
    }

    private static final class State {
        final Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());
        int remaining = 10_000;
    }
}
