package cn.code91.facility.web.filter;

import cn.code91.facility.web.session.SessionUserHolder;
import org.slf4j.*;
import org.slf4j.helpers.*;
import org.slf4j.spi.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** External logging SPI fault fixture, installed only in an isolated JVM via slf4j.provider. */
public final class FaultingMdcProvider implements SLF4JServiceProvider {
    static final RuntimeException PRIMARY = new IllegalStateException("MDC-primary");
    static final RuntimeException SECONDARY = new IllegalStateException("MDC-rollback");
    private final ILoggerFactory loggers = new NOPLoggerFactory();
    private final IMarkerFactory markers = new BasicMarkerFactory();
    private final MDCAdapter mdc = new FaultAdapter();
    @Override public ILoggerFactory getLoggerFactory() { return loggers; }
    @Override public IMarkerFactory getMarkerFactory() { return markers; }
    @Override public MDCAdapter getMDCAdapter() { return mdc; }
    @Override public String getRequestedApiVersion() { return "2.0.99"; }
    @Override public void initialize() {}

    private static final class FaultAdapter implements MDCAdapter {
        private final BasicMDCAdapter delegate = new BasicMDCAdapter();
        private final AtomicBoolean primary = new AtomicBoolean(true), rollback = new AtomicBoolean();
        private final String mode = System.getProperty("facility.test.mdcFault");
        private boolean worker() {
            return Thread.currentThread().getName().startsWith(mode.equals("servlet") ? "http-nio-" : "request-contract-");
        }
        @Override public String get(String key) {
            if (worker() && key.equals("traceId") && mode.equals("get") && SessionUserHolder.getUser(Object.class).isPresent()
                    && primary.compareAndSet(true, false)) throw PRIMARY;
            return delegate.get(key);
        }
        @Override public void put(String key, String value) {
            delegate.put(key, value);
            if (!worker() || !key.equals("traceId")) return;
            if (mode.equals("post") && SessionUserHolder.getUser(Object.class).isPresent()) { rollback.set(true); return; }
            if (!mode.equals("get") && !mode.equals("post") && SessionUserHolder.getUser(Object.class).isPresent() && primary.compareAndSet(true, false)) {
                rollback.set(mode.equals("rollback") || mode.equals("servlet")); throw PRIMARY;
            }
            if (SessionUserHolder.getUser(Object.class).isEmpty() && rollback.compareAndSet(true, false)) throw SECONDARY;
        }
        @Override public void remove(String key) {
            delegate.remove(key);
            if (worker() && mode.equals("servlet") && rollback.compareAndSet(true, false)) throw SECONDARY;
        }
        @Override public void clear() { delegate.clear(); }
        @Override public Map<String, String> getCopyOfContextMap() { return delegate.getCopyOfContextMap(); }
        @Override public void setContextMap(Map<String, String> map) { delegate.setContextMap(map); }
        @Override public void pushByKey(String key, String value) { delegate.pushByKey(key, value); }
        @Override public String popByKey(String key) { return delegate.popByKey(key); }
        @Override public Deque<String> getCopyOfDequeByKey(String key) { return delegate.getCopyOfDequeByKey(key); }
        @Override public void clearDequeByKey(String key) { delegate.clearDequeByKey(key); }
    }
}
