# Claim ordinary-jar fixtures

`legacy-api/cn/code91/facility/idempotency/IdempotencyStore.java` is the exact source from integration commit `c32e72e86de7e4f708e0b423c18f84c955ab683a`, before ticket11 expanded the SPI. `LegacyOnlyStore` is first compiled against that snapshot. The runner then copies **only its implementation class** into the runtime directory; the old interface class stays outside the runtime classpath. `ClaimConsumer` links it to the new installed ordinary jar and proves that the new defaults return unsupported without invoking any old method.

`ClaimConsumer` uses only the installed jar and JDK, with 64MiB heap/2 processors/45s. Seed110034 and literal receipt bytes7/11/23 test terminal invariants. A real worker barrier reproduces the old-owner race; 32768 attempts at256 fixed slots preserve binding capacity. Keeping128 closed stores reachable while each previously held1MiB proves payload release.

`ClaimFailureProbe` runs as three separate32MiB processes, each with2 processors/45s:

- `clone`:20MiB input fits, its owned copy cannot; the original OutOfMemoryError propagates and the binding remains UNKNOWN after advancing the clock.
- `close-tables`:2048 stores each hold2048 qualified and2048 legacy small entries; all remain reachable after close, proving map table release as well as payload release.
- `clock-error`:both qualified complete and release preserve the exact host Clock Error and leave UNKNOWN.

Use `java verification/Verify.java integration` or `all`; no test framework, private-field reflection or backend mock enters these JVMs. The root test JVM is not subjected to deliberate OOM. These are local, nonpersistent protocol tests, not distributed or business-transaction evidence.
