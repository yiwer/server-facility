# JSON / HTTP migration consumer

This standalone Maven application consumes only the installed ordinary facility jar and its declared dependency graph. It has no parent, reactor membership, library source directory or library test classes. Its own `spring-boot-starter-web` supplies the real Servlet container. The smaller `verification/consumer` remains the non-Web / optional-dependency absence probe.

`example.JsonConsumer constructed` uses the existing public `new Jsons(ObjectMapper)` constructor with the application mapper. `injected` obtains the application's `Jsons` through Spring injection. Both modes run the same endpoints, assertions and frozen resources. The injected mode additionally alternates real HTTP requests to two live applications with different mapper policies, closes one, recreates it, and verifies the surviving application. Applications bind only `127.0.0.1:0`, own at most four Tomcat worker threads, and close explicitly; HTTP connect/request deadlines are five seconds. The verification runner bounds the child JVM by time and heap.

## Golden provenance

The UTF-8 files in `src/main/resources/golden` are hand-written protocol examples, checked against the pre-expand Boot 3.5.16 / Jackson 2.21.4 artifact at integration commit `731598b`. Expected output is read from these literal files, never serialized from a second expected object. `default.json` and `custom.json` freeze a record containing a long beyond JavaScript's safe integer range, a scale-preserving decimal, an enum, Java time values, a legacy Date, a nullable field, present/empty Optional, and Unicode. Explicit application properties fix the Date format and timezone; the custom application adds snake case, numeric strings, NON_EMPTY, strict unknown fields and trailing-token rejection.

`generic.json` is an independently authored generic input with an unknown field; `invalid.txt` and `trailing.json` exercise error channels and the deliberate default/custom policy difference. The baseline default accepts a trailing second value; this is documented compatibility behavior, not a recommendation for untrusted input. HTTP checks compare MVC output and service serialization to paired files, exercise UTF-8 POST, and assert malformed/strict trailing input status 400. Error messages and stack traces are not golden protocol: ticket 23 must remove sensitive payloads from errors and ticket 04 owns the HTTP error envelope.

## Execution

From the repository root, `java verification/Verify.java integration` (or `all`) builds and installs into a worktree-local repository, compiles this project separately, checks all dependency entries are repository jars, and runs both constructed and injected consumers. Reports include this application's effective POM, dependency tree, jar hash and process log. The pre-expand constructed run is a migration evidence step, not a permanent claim that the global registry supports multiple applications.

For a manual run, install the library with the checked-in Wrapper into an explicitly selected isolated repository, run the root Wrapper in this directory with `clean compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt` and the same repository, then launch `example.JsonConsumer` with `target/classes` plus that file's classpath. Do not substitute library `target/classes` or a shared SNAPSHOT cache.

The `*-http.json` companions freeze Jackson 2 UTF-8 generator surrogate escaping for the non-BMP test character; String serialization preserves the character. These represent identical values but deliberately assert each distinct wire representation. A preliminary probe also showed that capturing JsonUtil.DEFAULT during bean creation can happen before the legacy registry is initialized. That global initialization limitation is not the migration comparison's guarantee; the baseline uses the existing explicit mapper constructor.


The consumer explicitly sets `facility.web.exception.use-problem-detail=true` so that the pre-expand platform already returns HTTP 400 for malformed input. The old default envelope's HTTP 200 defect belongs to ticket 04 and is not frozen here. Escaped and raw non-BMP forms are equivalent JSON values; a future platform migration should preserve Unicode, numeric precision and type semantics, and document any equivalent wire change rather than adding an escape compatibility switch.
