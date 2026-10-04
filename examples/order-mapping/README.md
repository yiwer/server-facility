# Explicit order-to-dispatch mapping

`OrderDispatch.prepare` is an application-owned business conversion: an order draft becomes a dispatch DTO with renamed fields, ordered product lines, sorted named attributes and an optional delivery note. It constructs named records directly. The module has no Spring or facility runtime dependency; Jakarta `@Nullable` documents the two nullable note components.

The repository has no production `autoCopy` call to replace. This executable example demonstrates the migration from its documented copy pattern and is used by the independent `OrderMappingConsumer`; it is not presented as a migrated production service. Put equivalent mapping next to your own business module, not in a new generic mapper interface.

```java
var order = new OrderDispatch.Order("ORD-19", "Ada",
    List.of(new OrderDispatch.Line("SKU-人", 2), new OrderDispatch.Line("SKU-人", 5)),
    Map.of("zone", "east", "gift", "yes"), null);
var dispatch = OrderDispatch.prepare(order);
// dispatch.orderId() == "ORD-19"; recipient == "Ada";
// duplicate product lines remain in order; attributes sort by name; deliveryNote stays null.
```

The input containers are borrowed for the duration of the call; the caller must not mutate them concurrently. The returned DTO from `prepare` has independent unmodifiable lists and immutable string/int record elements. Later source list/map edits do not alter it. No mapping writes a final field or evaluates a constructor through reflection. Plain record constructors otherwise retain their ordinary Java semantics; `prepare` is the validated business boundary.

| Input | Policy |
|---|---|
| order/id/buyer/lines/attributes and each line/key/value | Required; null rejects |
| note | Null remains absent; empty string remains empty |
| lines | 1–1000, preserve encounter order and duplicate SKU rows |
| attributes | 0–64, sorted by String natural key order; input Map already owns any duplicate-key resolution |
| id/buyer/SKU/note/key/value | Maximum64/256/128/4096/64/256 UTF-16 code units; no unpaired surrogates; required id/buyer/SKU/key must be nonblank |
| quantity | 1–10000 |

Actual iteration is bounded as well as declared collection size. Error messages describe policy without echoing input. These are example business limits, not a new library configuration DSL or a general validator. User-defined container/iterator work and concurrent request admission remain application responsibilities.

Run `java verification/Verify.java integration` from the repository root after the documented JDK/PostgreSQL prerequisites. Its mapping stage copies these exact sources, compiles an independent consumer, executes a literal complete-field oracle and runs compiler/business negative controls. A new target record component must be supplied by the explicit constructor; an accessor rename fails compilation. Swapping same-type fields compiles but fails the literal business oracle. Source record shape changes also require review of the explicit expected component list. A compiler cannot prove arbitrary mapping meaning.

For only this example, use JDK25 `javac -encoding UTF-8 -cp <jakarta.annotation-api-3.0.0.jar> -d <classes> examples/order-mapping/src/example/orders/OrderDispatch.java verification/mapping-consumer/OrderMappingConsumer.java`, then `java -cp <classes> OrderMappingConsumer`. The aggregate runner additionally supplies the annotation jar and source/work file URIs to execute the mutation controls.

Legacy compatibility, budgets, alias policies and migration are documented in [explicit mapping](../../docs/building/explicit-mapping.md). This example has no HTTP, database, executor or temporary-file lifecycle of its own.
