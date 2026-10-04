import cn.code91.facility.copy.CopyUtil;
import cn.code91.facility.copy.CopyTrait;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Ordinary-jar compatibility/resource consumer; uses public legacy APIs only. */
public class LegacyCopyConsumer {
    public static final class Value implements CopyTrait<Value> {
        int number;
        public Value(int number) { this.number = number; }
        @Override public Value copy() { return new Value(number); }
    }
    public static final class Bean {
        public Bean() {}
        List<String> shared;
        Value deep;
        String note = "constructor-default";
    }
    public static final class Payload {
        public Payload() {}
        Object borrowed;
    }
    public static void main(String[] args) throws Exception {
        compatibility();
        if (args.length > 0 && args[0].equals("legacy")) return;
        absent("org.springframework.context.ApplicationContext");
        absent("org.apache.poi.ss.usermodel.Workbook");
        seededCopies();
        resources();
    }

    static void compatibility() {
        Bean source = new Bean(); source.shared = new ArrayList<>(List.of("first", "second"));
        source.deep = new Value(19); source.note = null;
        Bean result = CopyUtil.autoCopy(source);
        check(result != source && result.shared == source.shared, "historical shallow alias");
        check(result.deep != source.deep && result.deep.number == 19, "historical CopyTrait delegation");
        check(result.note.equals("constructor-default"), "historical null keeps target default");
        check(CopyUtil.autoCopy(null) == null, "nullable signature");
        check(CopyUtil.copyList(List.of(new Value(7))).getFirst().number == 7, "old collection entrypoint");
        var options = CopyUtil.CopyOptions.builder().skipNullElements(true).throwOnNullCopy(false).warnOnNullCopy(false).build();
        check(CopyUtil.copyList(Arrays.asList(null, new Value(8)), options).size() == 1, "old options builder ABI");
        System.out.println("LEGACY_COPY_COMPAT_PASS fields/order/null/default/shallow/delegate/signatures");
    }

    static void seededCopies() {
        Random random = new Random(190042);
        for (int round = 0; round < 512; round++) {
            List<Value> source = new ArrayList<>();
            int count = random.nextInt(128);
            for (int i = 0; i < count; i++) source.add(new Value(random.nextInt(1000)));
            List<Value> copied = CopyUtil.copyList(source);
            check(copied.size() == source.size(), "seeded cardinality");
            for (int i = 0; i < source.size(); i++)
                check(copied.get(i) != source.get(i) && copied.get(i).number == source.get(i).number, "seeded order/value/independence");
        }
    }

    static void resources() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        check(runtime.maxMemory() <= 70L * 1024 * 1024, "run resource consumer with -Xmx64m");
        List<Value> full = Collections.nCopies(10000, new Value(19));
        for (int size : new int[]{1, 100, 10000}) check(CopyUtil.copyList(full.subList(0, size)).size() == size, "increasing accepted inputs");
        int threads = ManagementFactory.getThreadMXBean().getThreadCount();
        long before = retained();
        for (int round = 0; round < 2000; round++) {
            check(CopyUtil.copyList(full).size() == 10000, "repeated work budget reset");
            rejects(CopyUtil.CopyException.class, () -> CopyUtil.copyList(Collections.nCopies(10001, new Value(1))));
        }
        for (int round = 0; round < 200; round++) {
            Payload source = new Payload(); source.borrowed = new byte[1024 * 1024];
            check(CopyUtil.autoCopy(source).borrowed == source.borrowed, "DIRECT payload remains borrowed, not duplicated");
            Error error = new AssertionError("controlled callback");
            try {
                CopyUtil.copyList(List.of(source), value -> { throw error; });
                throw new AssertionError("callback Error lost");
            } catch (Error actual) { check(actual == error, "callback Error identity"); }
        }
        try {
            Thread.currentThread().interrupt();
            rejects(CopyUtil.CopyException.class, () -> CopyUtil.copyList(full));
            check(Thread.currentThread().isInterrupted(), "interrupt flag preserved");
        } finally { Thread.interrupted(); }
        check(CopyUtil.copyList(full).size() == 10000, "after failure/cancel scope reset");
        long after = retained();
        int finalThreads = ManagementFactory.getThreadMXBean().getThreadCount();
        check(after < before + 16L * 1024 * 1024, "retained inputs/work state must remain bounded");
        check(finalThreads <= threads + 1, "no accumulating worker threads");
        System.out.println("LEGACY_COPY_RESOURCE_PASS seed=190042 properties=512 rounds=2000 callback-errors=200 heap64m=true retained="
                + before + "->" + after + " threads=" + threads + "->" + finalThreads);
    }
    static long retained() throws Exception {
        System.gc(); Thread.sleep(50);
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
    static void absent(String name) throws Exception {
        try { Class.forName(name); throw new AssertionError("unexpected framework: " + name); }
        catch (ClassNotFoundException expected) { }
    }
    static void rejects(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); } catch (Throwable failure) { if (type.isInstance(failure)) return; throw new AssertionError(failure); }
        throw new AssertionError("expected rejection " + type.getSimpleName());
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
