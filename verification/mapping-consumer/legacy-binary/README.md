# Pre-change API binary fixture

Compiled on 2026-10-04 with JDK25 `javac --release25` against the actual pre-ticket19 ordinary jar from ticket16 source99ae71adabb6ada6c3a346ea142c7bf666b7a25d. Jar SHA256:12c2113e54d8ec3552753ff408e41f49fce0bf24690f05e2d5805ae03cb81bef. The exact source used is compiled-source.java.txt; class hashes are SHA256SUMS.

This is a compatibility control compiled now against a frozen old artifact, not a claimed historical production application. It ran with that old jar in `legacy` mode and must also run unchanged against the newly installed ordinary jar. Only the common historical field/order/null/default/shallow/delegation/signature subset executes in this mode; tightened final/cycle/budget policy is verified separately against the new jar. No old facility jar is embedded or shipped here.
