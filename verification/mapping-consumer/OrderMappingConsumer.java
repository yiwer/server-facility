import example.orders.OrderDispatch;
import example.orders.OrderDispatch.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLClassLoader;
import java.lang.reflect.InvocationTargetException;
import javax.tools.ToolProvider;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

/** Executed order-to-dispatch consumer with a literal business oracle. */
public class OrderMappingConsumer {
    public static void main(String[] args) throws Exception {
        completeDispatchSnapshot();
        if (args.length == 1 && args[0].equals("business-only")) return;
        finiteDispatchContainers();
        finiteDispatchValues();
        if (args.length == 2) evolutionControls(Path.of(URI.create(args[0])), Path.of(URI.create(args[1])));
        System.out.println("ORDER_MAPPING_CONSUMER_PASS complete-fields=true duplicate-lines=true named-values=true");
    }

    static void completeDispatchSnapshot() {
        check(Arrays.stream(Order.class.getRecordComponents()).map(component -> component.getName()).toList()
                .equals(List.of("id", "buyer", "lines", "attributes", "note")), "source field review is required after shape change");
        var lines = new ArrayList<>(List.of(new Line("SKU-人", 2), new Line("SKU-人", 5), new Line("SKU-B", 1)));
        var attributes = new LinkedHashMap<String, String>(); attributes.put("zone", "east"); attributes.put("gift", "yes");
        Dispatch actual = OrderDispatch.prepare(new Order("ORD-19", "Ada", lines, attributes, null));
        Dispatch expected = new Dispatch("ORD-19", "Ada", List.of(new DispatchLine("SKU-人", 2),
                new DispatchLine("SKU-人", 5), new DispatchLine("SKU-B", 1)),
                List.of(new Attribute("gift", "yes"), new Attribute("zone", "east")), null);
        check(actual.equals(expected), "complete dispatch fields/order/duplicate product lines/null note");
        lines.clear(); attributes.clear();
        check(actual.equals(expected), "snapshot owns containers independently of later source mutation");
        rejects(UnsupportedOperationException.class, () -> actual.lines().add(new DispatchLine("mutated", 1)));
        rejects(UnsupportedOperationException.class, () -> actual.attributes().clear());
        Dispatch noted = OrderDispatch.prepare(new Order("ORD-20", "Grace", List.of(new Line("SKU", 1)), Map.of(), "leave at desk"));
        check(noted.deliveryNote().equals("leave at desk"), "non-null delivery note");
    }
    static void finiteDispatchContainers() {
        for (int size : new int[]{999, 1000}) {
            var result = OrderDispatch.prepare(new Order("id", "recipient", Collections.nCopies(size, new Line("sku", 1)), Map.of(), null));
            check(result.lines().size() == size, "line budget accepts N-1 and N");
        }
        rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(new Order("id", "recipient",
                Collections.nCopies(1001, new Line("sku", 1)), Map.of(), null)));
        for (int size : new int[]{63, 64, 65}) {
            Map<String, String> attributes = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) attributes.put("key" + i, "value");
            Order source = new Order("id", "recipient", List.of(new Line("sku", 1)), attributes, null);
            if (size <= 64) check(OrderDispatch.prepare(source).attributes().size() == size, "attribute budget N-1/N");
            else rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(source));
        }
        rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(new Order("id", "recipient", List.of(), Map.of(), null)));
    }

    static void finiteDispatchValues() {
        var limits = Map.of("id", 64, "buyer", 256, "sku", 128, "note", 4096, "key", 64, "value", 256);
        for (var entry : limits.entrySet()) {
            for (int size : new int[]{entry.getValue() - 1, entry.getValue()})
                OrderDispatch.prepare(withText(entry.getKey(), "x".repeat(size)));
            rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(withText(entry.getKey(), "x".repeat(entry.getValue() + 1))));
        }
        for (int units : new int[]{1, 9999, 10000})
            check(OrderDispatch.prepare(new Order("id", "buyer", List.of(new Line("sku", units)), Map.of(), "")).lines().getFirst().units() == units, "quantity boundaries");
        for (int units : new int[]{0, -1, 10001, Integer.MAX_VALUE})
            rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(new Order("id", "buyer", List.of(new Line("sku", units)), Map.of(), null)));
        rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(withText("id", " ")));
        rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(withText("sku", "bad\uD800")));
        OrderDispatch.prepare(withText("sku", "🧭".repeat(64)));
        rejects(IllegalArgumentException.class, () -> OrderDispatch.prepare(withText("sku", "🧭".repeat(65))));
        rejects(NullPointerException.class, () -> OrderDispatch.prepare(null));
        rejects(NullPointerException.class, () -> OrderDispatch.prepare(new Order(null, "buyer", List.of(new Line("sku", 1)), Map.of(), null)));
        rejects(NullPointerException.class, () -> OrderDispatch.prepare(new Order("id", "buyer", null, Map.of(), null)));
    }
    static Order withText(String field, String value) {
        return new Order(field.equals("id") ? value : "id", field.equals("buyer") ? value : "buyer",
                List.of(new Line(field.equals("sku") ? value : "sku", 1)),
                Map.of(field.equals("key") ? value : "key", field.equals("value") ? value : "value"),
                field.equals("note") ? value : null);
    }

    static void evolutionControls(Path source, Path work) throws Exception {
        String original = Files.readString(source, StandardCharsets.UTF_8);
        String targetShape = "List<Attribute> attributes, @Nullable String deliveryNote)";
        check(original.contains(targetShape), "target constructor control still matches actual source");
        check(!compile(original.replace(targetShape, "List<Attribute> attributes, @Nullable String deliveryNote, String warehouse)"),
                work.resolve("added-target-field")), "added DTO field must fail the explicit constructor at compile time");
        check(original.contains("String id, String buyer,"), "source rename control matches actual source");
        check(!compile(original.replace("String id, String buyer,", "String id, String recipientName,"),
                work.resolve("renamed-source-field")), "renamed source accessor must fail compilation");
        check(original.contains("new Dispatch(id, buyer,"), "business mutation control matches actual source");
        Path swapped = work.resolve("swapped-fields");
        check(compile(original.replace("new Dispatch(id, buyer,", "new Dispatch(buyer, id,"), swapped),
                "same-type field swap compiles, requiring a business oracle");
        var classes = OrderMappingConsumer.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{swapped.resolve("classes").toUri().toURL(), classes},
                ClassLoader.getPlatformClassLoader())) {
            try {
                loader.loadClass("OrderMappingConsumer").getMethod("main", String[].class)
                        .invoke(null, (Object) new String[]{"business-only"});
                throw new AssertionError("swapped mapping escaped literal business oracle");
            } catch (InvocationTargetException rejected) {
                check(rejected.getCause() instanceof AssertionError
                        && rejected.getCause().getMessage().contains("complete dispatch fields"), "business field-swap rejection");
            }
        }
        Files.writeString(work.resolve("evolution-summary.txt"),
                "added-target-field: compile rejected\nrenamed-source-field: compile rejected\nswapped-fields: compiles, business oracle rejected\n", StandardCharsets.UTF_8);
        System.out.println("ORDER_EVOLUTION_CONTROLS_PASS added-field/renamed-accessor/compiler; swapped-field/business");
    }

    static boolean compile(String source, Path work) throws Exception {
        Path input = work.resolve("src/example/orders/OrderDispatch.java"); Files.createDirectories(input.getParent());
        Files.writeString(input, source, StandardCharsets.UTF_8);
        Path classes = Files.createDirectories(work.resolve("classes"));
        var compiler = Objects.requireNonNull(ToolProvider.getSystemJavaCompiler(), "JDK compiler required");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            boolean success = compiler.getTask(null, files, diagnostics,
                    List.of("--release", "25", "-classpath", System.getProperty("java.class.path"), "-d", classes.toString()),
                    null, files.getJavaFileObjects(input)).call();
            Files.writeString(work.resolve("diagnostics.txt"), diagnostics.getDiagnostics().toString(), StandardCharsets.UTF_8);
            return success;
        }
    }

    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void rejects(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); } catch (Throwable failure) {
            if (type.isInstance(failure)) return; throw new AssertionError("wrong failure category", failure);
        }
        throw new AssertionError("expected rejection: " + type.getSimpleName());
    }
}
