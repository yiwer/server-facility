package example.orders;

import jakarta.annotation.Nullable;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Application-owned order submission snapshot; no framework or reflection mapper. */
public final class OrderDispatch {
    private OrderDispatch() {}
    /** Borrowed input: the application must not mutate it concurrently during prepare. */
    public record Order(String id, String buyer, List<Line> lines, Map<String, String> attributes, @Nullable String note) {}
    public record Line(String sku, int quantity) {}
    public record DispatchLine(String productCode, int units) {}
    public record Attribute(String name, String value) {}
    public record Dispatch(String orderId, String recipient, List<DispatchLine> lines,
                           List<Attribute> attributes, @Nullable String deliveryNote) {}

    public static Dispatch prepare(Order source) {
        Objects.requireNonNull(source, "order");
        String id = required(source.id(), 64);
        String buyer = required(source.buyer(), 256);
        String note = source.note() == null ? null : bounded(source.note(), 4096);
        if (source.lines().size() > 1000 || source.attributes().size() > 64)
            throw new IllegalArgumentException("dispatch container budget exceeded");
        List<DispatchLine> lines = new ArrayList<>();
        for (Line line : source.lines()) {
            if (lines.size() == 1000) throw new IllegalArgumentException("dispatch line budget exceeded");
            String sku = required(line.sku(), 128);
            if (line.quantity() < 1 || line.quantity() > 10000)
                throw new IllegalArgumentException("dispatch quantity must be between 1 and 10000");
            lines.add(new DispatchLine(sku, line.quantity()));
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("dispatch requires at least one line");
        List<Attribute> attributes = new ArrayList<>();
        for (var entry : source.attributes().entrySet()) {
            if (attributes.size() == 64) throw new IllegalArgumentException("dispatch attribute budget exceeded");
            attributes.add(new Attribute(required(entry.getKey(), 64), bounded(entry.getValue(), 256)));
        }
        attributes.sort(Comparator.comparing(Attribute::name));
        return new Dispatch(id, buyer, List.copyOf(lines), List.copyOf(attributes), note);
    }

    private static String required(String value, int maximum) {
        String text = bounded(value, maximum);
        if (text.isBlank()) throw new IllegalArgumentException("required dispatch text is blank");
        return text;
    }

    private static String bounded(String value, int maximum) {
        Objects.requireNonNull(value, "dispatch text");
        if (value.length() > maximum) throw new IllegalArgumentException("dispatch text budget exceeded");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (++i == value.length() || !Character.isLowSurrogate(value.charAt(i)))
                    throw new IllegalArgumentException("dispatch text contains an unpaired surrogate");
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("dispatch text contains an unpaired surrogate");
            }
        }
        return value;
    }

}
