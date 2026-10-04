# Date, capacity and pattern migration

New input policy belongs to the application. The [scheduled export example](../../examples/export-input/README.md) uses explicit `Clock`, `ZoneId`, `Locale`, fixed strict `java.time` parsing and a finite positive MiB budget. Its public `plan` boundary is tested by an independent JDK-only consumer. There is no new generic formatting or validation facade.

| Existing entry | Continuing contract / migration |
|---|---|
| `DateUtil.format/parseDate/parseDateTime` | Legacy `DateTimeFormatter.ofPattern` with SMART resolution and the call's default FORMAT Locale. `2025-02-30` normalizes to February28. Dynamic formatters are no longer cached; a Locale change takes effect on the next call. Quoted pattern text is never rewritten. Applications should own reusable fixed formatters with explicit Locale and ResolverStyle. |
| Other `DateUtil` defaults | Existing system-zone conversion, current-time helpers and null conventions remain. These are not substitutes for an injected Clock/ZoneId. Legacy error Results can carry the original parse exception; do not expose those causes as safe HTTP details. New export-input refusals carry no input cause. |
| `NumberFormat.parseSize` | Optional signed ASCII decimal parser; B/KB/MB/GB/TB mean powers of1024, fraction truncates toward zero, zero/negative remain valid. Exact BigDecimal arithmetic fixes binary64 byte loss and rejects integral overflow. Input is at most128 UTF-16 units before trim, decimal scale in[-128,128] before multiplication. Nonfinite, hexadecimal floating syntax, excessive text/exponents and unsupported suffixes reject. This is a deliberate tightening; budget callers must still reject nonpositive results. |
| `NumberFormat` display methods | Existing default FORMAT Locale, HALF_UP rounding and null-to-empty behavior remain. Large caller-owned BigDecimal values/output scale are trusted display inputs, not a request-budget API. Callers bound them before formatting. French separators and binary size display have literal goldens. |
| `NumberUnits` | Deprecated, signatures retained. Uses binary64; mm→px uses Math.round (negative half rounds toward positive infinity), px→mm uses HALF_UP with scale2; existing null/zero-DPI behavior remains. New code uses BigDecimal and explicitly validates DPI and selects scale/rounding. The replacement can intentionally differ at ties and extremes. |
| `Patterns.compile`, `cacheSize`, `clearCache` | At most256 retained regex/flags entries with access-order eviction. Regex keys over4096 UTF-16 units compile without retention. Identity is not permanent across eviction/clear. Cache operations are synchronized; compilation occurs outside the lock, so in-flight compilation may publish after a concurrent clear. No key/reference survives merely because it was used historically. |
| Matching / extracting / replacing | Caller-owned trusted developer patterns only. Cache bounds do not limit regex compile/match CPU, external content size or output cardinality. Restrict external choices to a finite application allowlist; never claim Future cancellation makes arbitrary Java regex safe. Syntax errors continue to throw except tryCompile/isValidRegex; invalid flags throw. Negative numeric groups throw when matched, out-of-range positive groups yield empty; missing named groups use the existing empty/null contracts. Replacements keep JDK group syntax; use escapeReplacement for literals. |
| Predefined predicates | DATE/ID/IP/password literals are legacy shape checks, not full semantic validation, authorization, password security or an HTML parser. DATE accepts2025-02-30; the export module rejects invalid calendar values. |

For example, an explicitly chosen HALF_UP integer-pixel policy is:

```java
if (dpi <= 0) throw new IllegalArgumentException("positive dpi required");
BigDecimal pixels = millimeters.multiply(BigDecimal.valueOf(dpi))
        .divide(new BigDecimal("25.4"), 0, RoundingMode.HALF_UP);
```

This does not silently preserve old negative-half rounding. Select the desired policy and verify domain samples before migrating. No printing module was introduced because the repository has no real printing consumer.

The finite retained-state evidence uses public cacheSize and a separate64MiB process, not reflection into private fields. Clock/locale defaults mutate only in isolated consumers. No new thread, queue, file or network ownership is added to these library paths; interrupting arbitrary caller code or a regex engine remains outside the contract. See [ADR0043](../adr/0043-explicit-value-policies.md) and [verification](../verification/ticket-20-value-policies.md).
