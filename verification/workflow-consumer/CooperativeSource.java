package workflow.fixture;

import java.io.InputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Single-reader fixture: yields a fixed prefix, then blocks until interruption or owned close. */
public final class CooperativeSource extends InputStream {
    private final byte[] prefix;
    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);
    private volatile int delivered;
    private volatile boolean closed;

    public CooperativeSource(byte[] prefix) { this.prefix = Objects.requireNonNull(prefix).clone(); }

    @Override public int read() throws IOException {
        byte[] one = new byte[1];
        return read(one, 0, 1) < 0 ? -1 : Byte.toUnsignedInt(one[0]);
    }

    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) return 0;
        if (closed) throw new IOException("Fixture source is closed");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Fixture source interrupted");
        if (delivered < prefix.length) {
            int count = Math.min(length, prefix.length - delivered);
            System.arraycopy(prefix, delivered, bytes, offset, count);
            delivered += count;
            return count;
        }
        blocked.countDown();
        try {
            released.await();
            throw new IOException("Fixture source closed while reading");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Fixture source interrupted");
        }
    }

    public boolean awaitBlocked(long timeout, TimeUnit unit) throws InterruptedException {
        return blocked.await(timeout, unit);
    }

    public int deliveredBytes() { return delivered; }
    public boolean isClosed() { return closed; }

    @Override public void close() {
        closed = true;
        released.countDown();
    }
}
