package cn.code91.facility.context;

import org.springframework.context.support.GenericApplicationContext;

import java.util.ArrayList;
import java.util.List;

/** Owns only the application contexts created by one test; no global state reset. */
public final class SpringContextHolderTestSupport implements AutoCloseable {
    private final List<GenericApplicationContext> contexts = new ArrayList<>();

    public <T extends GenericApplicationContext> T refresh(T context) {
        contexts.add(context);
        context.registerBean(SpringContextHolder.class);
        context.refresh();
        return context;
    }

    @Override
    public void close() {
        for (int i = contexts.size() - 1; i >= 0; i--) {
            contexts.get(i).close();
        }
        contexts.clear();
    }
}
