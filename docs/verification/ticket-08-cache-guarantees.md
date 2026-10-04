# Ticket08 cache guarantees — incremental evidence

In progress; no full gate or platform completion is claimed. Worktree ticket-08, branch codex/ticket-08, starting integration `1d6377dee64db3e8b072dd590a9f09c78df5b6df`. Raw RED/GREEN logs live in .verification-results/ticket-08 and survive clean. Public seams and decisions are in the ticket plan and ADR0031.

| Cycle | RED / prior behavior | GREEN / result |
| --- | --- | --- |
| 01 default capability absence | red-01-default-absence.log: unselected application contains caffeineCacheManager | green-01-default-absence.log:1/0/0/0; no implicit cache manager |
| 02 missing backend | red-02-missing-dependency.log: selected cache starts successfully with a permanent Map when Caffeine is hidden | green-02-missing-dependency.log:2/0/0/0; explicit stable missing dependency failure |
| 03 selection/override matrix | Existing implementation passed; no artificial RED | contract-03-selection-matrix.log:4/0/0/0; missing Spring support or both dependencies refuses; disabled and explicit user-manager contexts start; complete pair supports public cache get/put |
| 04 fixed names | red-04-fixed-names.log: configured names ignored by the dynamic manager | green-04-fixed-names.log:5/0/0/0; two declared caches remain the only caches after4096 unknown names |
| 05 policy validation | red-05-policy-inputs.log: selected zero TTL starts successfully | green-05-policy-inputs.log:6/0/0/0; nonpositive/overflow TTL, nonpositive capacity, missing/duplicate/overlong/control names reject before cache publication |
| 06 expiration clock | red-06-expiration-clock.log: at10s fake time the old manager still returned the value | green-06-expiration-clock.log:7/0/0/0; supplied public Caffeine Ticker drives exact write expiry, read does not extend it; optional signatures moved behind class-level dependency guards |
| 07 capacity/maintenance | Existing selected backend passed; no artificial RED | contract-07-capacity-maintenance.log:8/0/0/0; N−1/N/N+1 at two entries, public Caffeine cleanUp observation point,4096key churn, clear and reload; no instantaneous strict-size claim |

| 08 legacy null loading | red-08-null-loading.log: legacy check/load/put calls the null loader twice | green-08-null-loading.log:17/0/0/0; delegates Cache.get loading contract |
| 09 actual workers | Existing backend passed | contract-09-concurrent-loading.log:3/0/0/0; first loader barrier, six followers, different-key completion before release |
| 10 null/failure/invalidation | Existing selected backend passed | contract-10-loader-failures.log:4/0/0/0; cached null, checked loader failure cause, no pollution, recovery and eviction |
| 11 close ownership | red-11-close-retention.log: closed application still exposes loaded1MiB value | green-11-close-retention.log:20/0/0/0; private standard-SPI lifecycle adapter detaches provider and clears owned values; second app independent |
| 12 invalidation/close probe | contract-12-invalidation-close-races.log:7 tests/1 failure; the experimental assertion that close always waits for an initial loader was false | This failed assumption is preserved, not reported as a provider bug or silently erased |
| 13 host lifetime correction | Provider behavior determines the documented contract; no product change to create artificial waiting | contract-13-close-host-lifetime.log:31/0/0/0; manager detaches, initial loader may finish later; host quiesce responsibility explicit; historical default/fallback and @Cacheable expectations migrated |
| 14 provider fault probe | contract-14-provider-failures.log:9 tests/1 failure; experimental expectation of clearing a cache whose ticker keeps throwing was false | Backend read failure passes without calling business loader or replacing cached data; cleanup guarantee narrowed to attempts and ownership release |
| 15 cleanup continues | No product change; one failing external clock read then recovery tests the declared best-effort cleanup boundary | contract-15-provider-cleanup-attempts.log:9/0/0/0; exact first exception preserved, other cache cleared, manager detached, repeat destroy harmless |
| 16 legacy failure contract | Existing loading delegation passed | contract-16-compatibility.log:34/0/0/0; Cache.ValueRetrievalException cause, failed load recovery, wrong type does not replace value, null loader rejection |

Commands use the checked-in Maven Wrapper with `-Dtest=... test`. Actual filenames are retained under `.verification-results/ticket-08`; the root full suite is not claimed green during this incremental phase. Host ticker faults can cause Caffeine's own maintenance logger to print the original cause: the facility cache code itself introduces no key/value/message logging, and does not claim control over third-party logging.

## Independent consumer preflight

`consumer-preflight-package.log` builds an ordinary jar with tests skipped, `consumer-preflight-install.log` installs that exact jar to the local development Maven repository, and `consumer-preflight-build.log` compiles the independent Maven application. These steps are explicitly a preflight, not a substitute for the clean full quality gate or isolated-repository provenance checks.

`consumer-preflight-64m.log` passed in a real `-Xmx64m -XX:ActiveProcessorCount=2` JVM:2 independent application policies, seed80031/2048 public expiry+invalidation operations,8192 unknown cache names,4096 entry churn,8 real workers;256 retained closed managers, each formerly holding32768 entries and1MiB payload. Loader threads are joined and contexts close naturally. No test dependency is present in this consumer graph. Retained managers expose empty names; no private map inspection or GC timing assertion.

`matrix-preflight-{minimal,caffeine-only,context-support-only,cache-pair}-{default,disabled,override,cache-enabled}.log`:16 actual independent production-graph JVMs all passed. Default/disabled does not force registration; a host manager wins even under explicit selection and missing dependencies; missing either local backend dependency rejects selection; complete pair uses standard Cache/CacheManager with finite names. Final runner additionally retains the fifth no-Jackson graph and existing JSON/executor checks, bringing its matrix to24 JVM scenarios.

## Remaining gate

Checkpoint is ready for integration merge and full `Verify all`. Exact source, final raw report, coverage/architecture/dependency results and platform status will be recorded below. Linux closure requires current CI; historical ticket24/default-fallback evidence does not cover this changed policy.


Merged integration `c581dc6` (ticket19/32 and ticket28 Windows process fix) at `0d11ef5fed98b648f564ac72400a14760345aff4`; runner preserves mapping/html/cache/http/claim/partner/template consumers. Full gate will run the combined source.
