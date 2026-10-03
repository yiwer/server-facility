import cn.code91.facility.common.Collects;
import cn.code91.facility.error.WrappedError;
import cn.code91.facility.error.FacilityErrorType;
import cn.code91.facility.error.ErrorTypeInterface;
import cn.code91.facility.common.NullSafe;
import cn.code91.facility.result.Result;
import cn.code91.facility.structure.Tuple;
import cn.code91.facility.structure.Triple;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

/** Independent business/compatibility consumer; runtime classpath is the ordinary facility jar only. */
public class CoreConsumer {
    public static void main(String[] args) {
        noFramework();
        if (args.length == 0 || !args[0].equals("legacy")) {
            requiredCallbacks();
            capacityDoesNotWrap();
            oversizedJoinFailsBeforeAllocation();
            typedErrorArgumentsRequireAType();
        }
        resultAndAbsence();
        callbackFailures();
        domainScenario();
        collectionCompatibility();
        shallowOwnership();
        compositionProperties();
        System.out.println("CORE_CONSUMER_PASS seed=180041 iterations=512 framework=absent");
    }

    static void noFramework() {
        for (String type : List.of("org.springframework.context.ApplicationContext", "org.slf4j.Logger",
                "jakarta.annotation.Nullable", "lombok.Getter", "tools.jackson.databind.ObjectMapper")) {
            try { Class.forName(type); throw new AssertionError("Unexpected runtime dependency: " + type); }
            catch (ClassNotFoundException expected) { /* The ordinary jar is the sole runtime dependency. */ }
        }
    }

    static void resultAndAbsence() {
        var empty = Result.<String, String>empty();
        check(empty.equals(Result.ok(null)) && empty.isOk(), "empty is successful null");
        check(empty.orElse("fallback") == null, "default is selected by state, not null data");
        check(empty.map(value -> value == null ? "explicit-null" : value).get().equals("explicit-null"), "null reaches mapper");
        expect(IllegalStateException.class, empty::swap);
        check(Result.<String, String>err("absent").swap().get().equals("absent"), "error can swap to success");
        check(empty.toOptional().isEmpty() && empty.stream().count() == 0, "lossy Optional/Stream conversion");
        var absent = Result.fromOptional(Optional.empty(), () -> "not-found");
        check(absent.isErr() && absent.getErr().equals("not-found"), "absence needs an explicit error policy");
        check(Result.fromNullable(null, () -> "null-input").isErr(), "explicit nullable conversion");
        var calls = new AtomicInteger();
        check(empty.orElseGet(() -> { calls.incrementAndGet(); return "fallback"; }) == null, "fallback remains lazy");
        check(Result.<Integer, String>err("first").map(value -> { calls.incrementAndGet(); return value; })
                .flatMap(value -> { calls.incrementAndGet(); return Result.ok(value); }).getErr().equals("first"), "failure short circuits");
        check(empty.recover(error -> { calls.incrementAndGet(); return "recovered"; }) == empty, "success recovery is lazy");
        check(calls.get() == 0, "unselected callbacks are never called");
        check(Result.<String, String>err("x").recover(error -> null).isOk(), "null recovery is successful");
        expect(NullPointerException.class, () -> empty.flatMap(value -> null));
        expect(NullPointerException.class, () -> Result.err("x").recoverWith(error -> null));
        Iterable<Result<String, String>> shortCircuit = () -> new Iterator<>() {
            int reads;
            public boolean hasNext() { if (reads > 0) throw new AssertionError("read past first failure"); return true; }
            public Result<String, String> next() { reads++; return Result.err("stop"); }
        };
        check(Result.collectShortCircuit(shortCircuit).getErr().equals("stop"), "collection stops at first failure");
        var all = Stream.of(Result.<String, String>ok(null), Result.<String, String>ok("a"))
                .collect(Result.collectAll());
        check(all.get().equals(Arrays.asList(null, "a")), "collector retains successful null");
        var errors = Stream.of(Result.<String, String>err("first"), Result.<String, String>ok("a"),
                Result.<String, String>err("second")).parallel().collect(Result.collectAll());
        check(errors.getErr().equals(List.of("first", "second")), "parallel collector preserves error encounter order");
    }

    static void callbackFailures() {
        var success = Result.<Integer, String>ok(1);
        var failure = Result.<Integer, String>err("failure");
        for (Runnable invalid : List.<Runnable>of(
                () -> success.mapErr(null), () -> failure.map(null), () -> failure.flatMap(null),
                () -> success.orElseGet(null), () -> success.recover(null), () -> success.recoverWith(null),
                () -> failure.ensure(null, () -> "e"), () -> failure.ensure(value -> true, null),
                () -> success.bimap(value -> value, null), () -> failure.bimap(null, value -> value),
                () -> success.fold(value -> value, null), () -> failure.fold(null, value -> value),
                () -> failure.ifOk(null), () -> success.ifErr(null), () -> failure.peek(null), () -> success.peekErr(null),
                () -> success.match(value -> {}, null), () -> failure.match(null, value -> {}),
                () -> failure.andThen(null), () -> success.orElseSupplier(null),
                () -> Result.fromOptional(Optional.of(1), null), () -> Result.fromNullable(1, null),
                () -> Tuple.of(null, "v").mapLeft(null), () -> Triple.of(1, 2, 3).trimap(value -> value, null, value -> value),
                () -> NullSafe.computeOrElse(null, "default"))) {
            expect(NullPointerException.class, invalid);
        }
        var bug = new IllegalStateException("synthetic-program-error");
        check(expect(IllegalStateException.class, () -> success.map(value -> { throw bug; })) == bug, "map propagates exact program error");
        check(expect(IllegalStateException.class, () -> failure.recover(error -> { throw bug; })) == bug, "recovery propagates program error");
        check(expect(IllegalStateException.class, () -> Collects.mapNonNull(List.of(1), value -> { throw bug; })) == bug, "collection propagates program error");
        var fatal = new LinkageError("synthetic-fatal");
        check(expect(LinkageError.class, () -> success.flatMap(value -> { throw fatal; })) == fatal, "flatMap propagates Error");
        check(expect(LinkageError.class, () -> Result.of(() -> { throw fatal; })) == fatal, "exception adapter never catches Error");
        check(expect(LinkageError.class, () -> Result.ofRunnable(() -> { throw fatal; })) == fatal, "runnable adapter never catches Error");
        check(Result.of(() -> { throw bug; }).getErr() == bug, "opt-in Exception adapter retains historical RuntimeException capture");
        for (boolean runnable : List.of(false, true)) {
            var interrupted = new InterruptedException("synthetic-interrupt");
            try {
                Result<?, Exception> converted = runnable
                        ? Result.ofRunnable(() -> { throw interrupted; }) : Result.of(() -> { throw interrupted; });
                check(converted.getErr() == interrupted && Thread.currentThread().isInterrupted(), "adapter restores interrupt");
            } finally { Thread.interrupted(); }
        }
    }

    enum OrderFailure implements ErrorTypeInterface {
        UNKNOWN_PRODUCT, INVALID_QUANTITY;
        public String getModule() { return "ORDER"; }
        public int getCode() { return 810001 + ordinal(); }
        public String getMessageKey() { return "order." + name().toLowerCase(Locale.ROOT); }
        public String getDefaultMessage() { return "Cannot order {0}"; }
    }
    record Product(String sku, int unitCents) {}
    record OrderLine(String sku, int quantity, int totalCents) {}
    record PriceChange(String sku, int beforeCents, int afterCents) {}

    static Optional<Product> findProduct(String sku) {
        return "tea".equals(sku) ? Optional.of(new Product("tea", 250)) : Optional.empty();
    }
    static Result<OrderLine, OrderFailure> placeLine(String sku, int quantity) {
        return Result.fromOptional(findProduct(sku), () -> OrderFailure.UNKNOWN_PRODUCT)
                .ensure(product -> quantity > 0, () -> OrderFailure.INVALID_QUANTITY)
                .map(product -> new OrderLine(product.sku(), quantity, Math.multiplyExact(product.unitCents(), quantity)));
    }
    static void domainScenario() {
        check(findProduct("unknown").isEmpty(), "query absence is Optional in the domain");
        check(placeLine("tea", 3).get().equals(new OrderLine("tea", 3, 750)), "named business output");
        check(placeLine("tea", 0).getErr() == OrderFailure.INVALID_QUANTITY, "expected domain failure");
        check(placeLine("unknown", 1).mapErr(error -> WrappedError.ofWithArgs(error, "unknown"))
                .fold(line -> "unexpected", error -> error.getErrorType().getFullCode() + ":" + error.getFormattedMessage())
                .equals("ORDER-810001:Cannot order unknown"), "domain-owned error without framework or global enum");
        expect(ArithmeticException.class, () -> placeLine("tea", Integer.MAX_VALUE));
        var comparison = Collects.extractCompareTuple(Map.of("tea", 250), Map.of("tea", 300));
        PriceChange output = comparison.get("tea").merge((before, after) -> new PriceChange("tea", before, after));
        check(output.equals(new PriceChange("tea", 250, 300)), "legacy tuple adapts to a named business protocol");
        var legacy = Triple.of("tea", 3, 750);
        check(legacy.merge(OrderLine::new).equals(new OrderLine("tea", 3, 750)), "legacy triple consumer remains supported");
    }

    static void collectionCompatibility() {
        var input = Arrays.asList("a", null, "b", "a");
        check(Collects.mapNonNull(input, value -> value.equals("b") ? null : value.toUpperCase(Locale.ROOT))
                .equals(List.of("A", "A")), "mapping retains order and duplicates, drops null results");
        var source = new ArrayList<>(List.of("a", "b"));
        var joined = Collects.safelyJoin(source, null, List.of("a"));
        source.clear(); joined.add("c");
        check(joined.equals(List.of("a", "b", "a", "c")), "join owns its list but keeps order and duplicate values");
        var firstWins = Collects.toMap(List.of(new Product("tea", 250), new Product("tea", 300)), Product::sku, Product::unitCents);
        check(firstWins.equals(Map.of("tea", 250)), "first nonnull duplicate key wins");
        check(Collects.listDiff(List.of("b", "a", "a"), List.of("a")).stream().sorted().toList().equals(List.of("a", "b")), "difference is a multiset with unspecified order");
        check(Collects.longListToLongArray(null) == null, "legacy null array conversion");
        String[] array = {"a", null};
        var values = NullSafe.asList(array); array[0] = "changed"; values.add("b");
        check(values.equals(Arrays.asList("a", null, "b")), "NullSafe list is a mutable shallow copy");
        check(NullSafe.computeOrElse(() -> null, "fallback").equals("fallback"), "legacy null fallback");
        var nullableEntry = Tuple.of("k", (String) null).toNullableEntry();
        nullableEntry.setValue("v");
        check(nullableEntry.getValue().equals("v"), "legacy nullable entry remains mutable");
        expect(NullPointerException.class, () -> Tuple.of("k", null).toEntry());
        expect(UnsupportedOperationException.class, () -> Tuple.of("k", "v").toEntry().setValue("changed"));
    }

    static void shallowOwnership() {
        var mutable = new StringBuilder("before");
        Object[] supplied = {mutable, "stable"};
        var error = WrappedError.ofWithArgs(OrderFailure.UNKNOWN_PRODUCT, supplied);
        supplied[0] = "replaced"; error.getArgs()[1] = "replaced";
        check(error.getFormattedMessage().equals("Cannot order before"), "array structure is copied");
        expect(UnsupportedOperationException.class, () -> error.getArgsList().add("x"));
        mutable.replace(0, mutable.length(), "after");
        check(error.getFormattedMessage().equals("Cannot order after"), "array elements are deliberately shared");
        var values = new ArrayList<>(List.of("a"));
        var result = Result.<List<String>, String>ok(values);
        var tuple = Tuple.of("items", values);
        values.add("b");
        check(result.map(List::size).get() == 2 && tuple.merge((key, value) -> value.size()) == 2, "containers do not promise deep copies");
    }

    static void compositionProperties() {
        var random = new Random(180041);
        Function<Integer, Result<Integer, String>> f = value -> value % 5 == 0 ? Result.err("divisible-five") : Result.ok(value + 3);
        Function<Integer, Result<Integer, String>> g = value -> value < 0 ? Result.err("negative") : Result.ok(value * 2);
        for (int i = 0; i < 512; i++) {
            int value = random.nextInt(-1000, 1001);
            Result<Integer, String> original = (i & 1) == 0 ? Result.ok(value) : Result.err("original-error");
            check(original.map(Function.identity()).equals(original), "map identity seed=180041 iteration=" + i);
            check(original.flatMap(f).flatMap(g).equals(original.flatMap(number -> f.apply(number).flatMap(g))), "flatMap association seed=180041 iteration=" + i);
            check(original.swap().swap().equals(original), "non-null swap involution seed=180041 iteration=" + i);
        }
    }

    static void typedErrorArgumentsRequireAType() {
        var error = WrappedError.ofWithArgs(FacilityErrorType.JSON_SERIALIZE_ERROR, (Object) null);
        expect(NullPointerException.class, () -> error.getArg(0, null));
        check(error.getArg(0, String.class) == null, "null error arguments remain valid data");
    }

    static void oversizedJoinFailsBeforeAllocation() {
        // A legitimate virtual List advertises size without allocating its elements.
        // The old sum wraps to positive 1 and starts materializing this input.
        List<String> huge = new AbstractList<>() {
            public int size() { return Integer.MAX_VALUE; }
            public String get(int index) { throw new AssertionError("must reject before traversal"); }
            public Object[] toArray() { throw new AssertionError("must reject before materialization"); }
        };
        expect(ArithmeticException.class, () -> Collects.safelyJoin(
                huge, huge, List.of("a", "b", "c")));
    }

    static void capacityDoesNotWrap() {
        check(Collects.calculateCapacity(12) == 17, "legacy capacity convention");
        check(Collects.calculateCapacity(0) == 16 && Collects.calculateCapacity(-1) == 16, "legacy nonpositive capacity");
        check(Collects.calculateCapacity(1_610_612_734) == 2_147_483_647, "last representable capacity");
        check(Collects.calculateCapacity(1_610_612_735) == Integer.MAX_VALUE, "capacity must saturate");
        check(Collects.calculateCapacity(Integer.MAX_VALUE) == Integer.MAX_VALUE, "maximum input must not wrap");
    }

    static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    static void requiredCallbacks() {
        expect(NullPointerException.class, () -> Collects.toMap(List.of(), null));
        expect(NullPointerException.class, () -> Collects.toMap(null, null, value -> value));
        expect(NullPointerException.class, () -> Collects.toMap(List.of(), value -> value, null));
        expect(NullPointerException.class, () -> Collects.safelyMappingAndJoin(null));
        expect(NullPointerException.class, () -> Collects.mapNonNull(null, null));
    }

    static <T extends Throwable> T expect(Class<T> type, Runnable action) {
        try { action.run(); }
        catch (Throwable failure) {
            if (type.isInstance(failure)) return type.cast(failure);
            throw new AssertionError("Expected " + type.getName() + " but received " + failure, failure);
        }
        throw new AssertionError("Expected " + type.getName());
    }
}
