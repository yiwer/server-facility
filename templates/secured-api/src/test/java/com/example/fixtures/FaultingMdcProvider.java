package com.example.fixtures;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.*;
import org.slf4j.helpers.*;
import org.slf4j.spi.*;

/** External logging SPI installed only in an isolated process, never in the HTTP test JVM. */
public final class FaultingMdcProvider implements SLF4JServiceProvider {
    static final RuntimeException PRIMARY = new IllegalStateException("install failed");
    static final RuntimeException SECONDARY = new IllegalStateException("restore failed");
    private final MDCAdapter adapter = new FaultAdapter();
    @Override public ILoggerFactory getLoggerFactory() { return new NOPLoggerFactory(); }
    @Override public IMarkerFactory getMarkerFactory() { return new BasicMarkerFactory(); }
    @Override public MDCAdapter getMDCAdapter() { return adapter; }
    @Override public String getRequestedApiVersion() { return "2.0.99"; }
    @Override public void initialize() {}

    private static final class FaultAdapter implements MDCAdapter {
        final BasicMDCAdapter delegate = new BasicMDCAdapter();
        final AtomicBoolean primary = new AtomicBoolean(true), secondary = new AtomicBoolean();
        @Override public void put(String key, String value) {
            delegate.put(key, value);
            if (!Thread.currentThread().getName().equals("scope-worker") || !key.equals("traceId")) return;
            if (value.equals("request-A") && primary.compareAndSet(true, false)) {
                secondary.set(Boolean.getBoolean("probe.rollbackFailure")); throw PRIMARY;
            }
            if (value.equals("host-trace") && secondary.compareAndSet(true, false)) throw SECONDARY;
        }
        @Override public String get(String key) { return delegate.get(key); }
        @Override public void remove(String key) { delegate.remove(key); }
        @Override public void clear() { delegate.clear(); }
        @Override public Map<String, String> getCopyOfContextMap() { return delegate.getCopyOfContextMap(); }
        @Override public void setContextMap(Map<String, String> values) { delegate.setContextMap(values); }
        @Override public void pushByKey(String key, String value) { delegate.pushByKey(key, value); }
        @Override public String popByKey(String key) { return delegate.popByKey(key); }
        @Override public Deque<String> getCopyOfDequeByKey(String key) { return delegate.getCopyOfDequeByKey(key); }
        @Override public void clearDequeByKey(String key) { delegate.clearDequeByKey(key); }
    }
}
