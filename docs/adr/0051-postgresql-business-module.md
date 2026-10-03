# ADR-0051: One PostgreSQL business Module in the secured application

## Status

Accepted. 2026-10-04. Ticket28, FR07/08, AC03/04/11. Extends ADR0050's application Actor and protected HTTP boundary with current business membership. The ordinary facility jar acquires no persistence dependency. No earlier persistence implementation is being silently replaced.

## Context

An authenticated greeting does not demonstrate a usable business application. The template needs one maintainable transaction, migration and pagination path, with genuine database failures and restart evidence. A memory Map or H2 test cannot establish PostgreSQL constraint and transaction behavior.

Boot4.1.1 manages Flyway12.4.0, pgJDBC42.7.13 and HikariCP7.0.2. A dependency-only preflight resolved the JDBC and Flyway starters plus PostgreSQL-specific Flyway module, and an independent native PostgreSQL18.6 probe applied and rechecked a migration containing a literal Unicode sample. These are capability facts, not the business acceptance result.

## Decision

- The copied application owns a `notes` Module: workspace (the tenant boundary), current membership and notes. A workspace is created together with its creator membership. Membership is business permission, not an identity provider or authentication user database. Actor remains the verified issuer/normalized-subject pair from ADR0050; supplied headers and tenant claims cannot establish permission.
- HTTP translates authenticated credentials and request DTOs into explicit Module arguments. The Module owns authorization, SQL, business invariants and transactions using Boot's `JdbcClient` and transaction manager. No `BaseService`, generic Repository, ORM option or changes to the facility runtime are required.
- PostgreSQL is the single database recommendation; Flyway is the single schema owner. Use Boot JDBC/Flyway starters, `flyway-database-postgresql`, and the runtime PostgreSQL driver. Schema migration is required before serving business traffic. Basic SQL initialization and automatic baseline are disabled; checksum validation is retained.
- Notes have a workspace-local unique slug distinct from the command identity to be introduced by29. Pagination accepts only bounded page/size and a fixed sort-name mapping with an ID tiebreaker; caller SQL never enters query structure. Domain values enter SQL as bound parameters.
- Connection acquisition, network connection, statement/lock waits and transaction duration have separate finite budgets. None is described as a complete HTTP deadline or proof that a timed-out write did not occur. Actual rollback and recovery observations establish effects.
- The HTTP adapter sanitizes persistence exceptions before composing FacilityHttpErrors. Expected connection/transaction timeout and transient failures use503; SQL/programming/unknown persistence failures remain500. PostgreSQL's `55P03` (lock_not_available) is explicitly recognized through `UncategorizedSQLException.getSQLException().getSQLState()`, because the exact Spring7.0.9 default translator leaves it uncategorized. Error messages and localized database detail are never classification inputs or response/log payloads. The actual row-lock test supplies the regression.
- The native PostgreSQL test fixture is an explicitly declared prerequisite and fails when absent. It owns its process, ephemeral loopback port and temporary data directory, and archives diagnostics before cleanup. Tests exercise signed HTTP and the public Module; independent database observations and controlled database faults use the user-approved persistence seam. No database test is silently skipped.
- A frozen first schema checkpoint is an explicit predecessor sample for this new application, not a claimed historical production release. Migration tests cover empty databases, fixed predecessor data, competing startup, invalid checksum and a failing transactional migration.
- Flyway must use the same controlled Hikari instance as JdbcClient; its separate URL/user connection surface is rejected. The effective policy requires latest target, no ignored migration patterns and successful application V1/V2 history. Real counterexamples demonstrated that an independently configured migration database and a real empty resource directory could otherwise make an unmigrated business application ready. Unknown future schema and partial targets also fail startup.
- The application owns Jackson input constraints, including a finite document budget for unknown-length HTTP bodies. Native PostgreSQL binaries used by the development/CI recipe are pinned to18.6.0 with per-platform SHA-512; only test/development helpers own those processes. JUnit6 root-store AutoCloseable cleanup propagates fixture shutdown failure into the test result; an emergency JVM hook is secondary.
- Flyway12.4.0's internal `RetryStrategy` has static retry-policy fields; each instance copies the retry count but reads global unlimited mode. Its PostgreSQL advisory-lock retry sleeps one second per unsuccessful attempt, including the last. The application selects a finite policy and does not promise different concurrent in-JVM Flyway policies are isolated or precisely timed. Controlled startup competition verifies the selected behavior.

## Consequences

**Positive:** the recommended application demonstrates current authorization, actual persistence, constraints, transaction rollback and migration through a small business interface. Authentication, transport and persistence remain application-owned standard framework configuration.

**Negative:** starting and fully verifying the application now requires PostgreSQL in addition to the declared JDK and trust policy. Database and connection budgets are deployment choices within the documented supported limits; they are not capacity estimates. Membership policy is deliberately small and has no administration UI or generalized role engine.

**Carry-forward:**29 adds tenant/Actor/operation/key/fingerprint and receipt in the same business transaction, retaining current authorization before replay.30 adds actual process loss, response-cut and dual-instance recovery plus representation cleanup semantics.28 makes none of those command-recovery guarantees.26 owns standard trace/Observation integration;28 composes its error boundary and does not invent another context mechanism.33 still requires the same final candidate's Windows/Linux complete gates.

## References

1. [Boot SQL support](https://docs.spring.io/spring-boot/reference/data/sql.html), [Boot schema initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
2. [Published Boot4.1.1 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom).
3. [PostgreSQL18 client settings](https://www.postgresql.org/docs/18/runtime-config-client.html), [pgJDBC connection properties](https://jdbc.postgresql.org/documentation/use/).
4. [Flyway PostgreSQL support](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database).
5. [Flyway12.4.0 retry strategy](https://github.com/flyway/flyway/blob/flyway-12.4.0/flyway-core/src/main/java/org/flywaydb/core/internal/strategy/RetryStrategy.java), [PostgreSQL advisory lock](https://github.com/flyway/flyway/blob/flyway-12.4.0/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLAdvisoryLockTemplate.java).
6. [PostgreSQL18 SQLSTATE catalogue](https://www.postgresql.org/docs/18/errcodes-appendix.html).
7. [Flyway12.4 BEFORE_MIGRATE and schema-history lock order](https://github.com/flyway/flyway/blob/flyway-12.4.0/flyway-core/src/main/java/org/flywaydb/core/internal/command/DbMigrate.java), [JUnit6.0.3 guide](https://docs.junit.org/6.0.3/_exports/junit-user-guide-6.0.3.pdf).

Exact implementation limits and executed evidence belong in the ticket28 report. This decision is not an assertion that all acceptance scenarios have passed.
