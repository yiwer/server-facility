import workflow.fixture.CooperativeSource;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Qualification of the external blocking input fixture, separate from application acceptance. */
class CooperativeSourceContract {
    public static void main(String[] args) throws Exception {
        for (boolean virtual : new boolean[]{false, true}) {
            byte[] prefix = "name,quantity\npartial,".getBytes(StandardCharsets.UTF_8);
            var source = new CooperativeSource(prefix);
            var observed = new AtomicReference<String>();
            var failure = new AtomicReference<Throwable>();
            Runnable work = () -> {
                try (source) {
                    check(Arrays.equals(source.readNBytes(prefix.length), prefix), "fixture changed the prefix bytes");
                    source.read();
                    observed.set("unexpected successful completion");
                } catch (InterruptedIOException expected) {
                    observed.set("interrupted:" + Thread.currentThread().isInterrupted());
                } catch (Throwable unexpected) { failure.set(unexpected); }
            };
            Thread worker = virtual ? Thread.ofVirtual().start(work) : Thread.ofPlatform().start(work);
            try {
                check(source.awaitBlocked(3, TimeUnit.SECONDS), "worker never entered the controlled read");
                check(worker.isAlive() && observed.get() == null, "source completed without interruption");
                check(source.deliveredBytes() == prefix.length && !source.isClosed(), "fixture did not reach the intended read boundary");
                worker.interrupt();
                worker.join(3000);
                check(!worker.isAlive(), "cooperative read did not leave within three seconds");
                check(failure.get() == null, "unexpected worker failure: " + failure.get());
                check("interrupted:true".equals(observed.get()), "fixture did not preserve the interrupt flag");
                check(source.isClosed(), "source resource remained open");
                check(source.deliveredBytes() == prefix.length, "source delivered post-interruption data");
                System.out.println("COOPERATIVE_SOURCE_PASS virtual=" + virtual + " prefixBytes=" + prefix.length);
            } finally {
                source.close();
                worker.interrupt();
                worker.join(3000);
                check(!worker.isAlive(), "fixture teardown left its worker alive");
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
