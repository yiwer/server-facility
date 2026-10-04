# Scheduled export input

`ExportRequests` is an application-owned Module that validates a scheduled export request and returns a named immutable plan. It uses only the JDK. It does not start a scheduler or allocate memory equal to the requested budget. The repository had no production `DateUtil`/`parseSize` request workflow to claim migrated; this independently compiled consumer is the executable migration example.

The host supplies `Clock`, `ZoneId` and `Locale` once. A request supplies three values:

| Field | Policy |
|---|---|
| `localSchedule` | Exactly 16 UTF-16 units, fixed `uuuu-MM-dd HH:mm`, strict calendar, years 0001–9999. No requester-supplied format. |
| `preferredOffset` | May be null for an unambiguous time. DST gaps reject; overlaps require one of the actual valid offsets. An explicit invalid offset also rejects outside overlaps. |
| `capacityMiB` | At most 32 UTF-16 units; ASCII unsigned decimal, up to six fraction digits, no exponent, unit suffix or whitespace. Positive and at most 64 MiB. MiB is exactly 1,048,576 bytes; fractional bytes floor to whole bytes. The smallest admitted value, `0.000001`, produces one byte. Zero/negative never mean unlimited here. |

The scheduled instant must be strictly after the injected clock's current instant and at most 30 × 24 hours ahead. This is elapsed time, not 30 local calendar days. The clock is consulted once per accepted parse. The host's supplied zone owns local-time interpretation; `Clock.getZone()` and process defaults cannot replace it. Display uses the supplied locale and includes the actual offset. Refusals use fixed `IllegalArgumentException` messages without raw input/cause. Missing host dependencies fail at construction; arbitrary failure inside a host-provided Clock propagates.

```java
var inputs = new ExportRequests(clock, ZoneId.of("Asia/Shanghai"), Locale.US);
var plan = inputs.plan("2028-02-29 10:15", null, "1.5");
// With a clock on 2028-02-01: 2028-02-29T02:15:00Z, 1_572_864 bytes.
```

The caller owns request admission, actual export execution, concurrency and cancellation. A plan is a value, not an execution promise. No expression language, dynamic regex, timezone resolver service or generic number-policy framework is added.

`verification/Verify.java integration` / `all` compile this application without the facility jar or framework dependencies and run its literal contract in three separate locale/timezone JVMs. The oracle covers strict/legacy differences, both DST offsets, gap refusal, years and numeric boundaries, fixed-clock horizons and seed 200043 numeric cases. Commands and evidence are recorded in [ticket20](../../docs/verification/ticket-20-value-policies.md).
