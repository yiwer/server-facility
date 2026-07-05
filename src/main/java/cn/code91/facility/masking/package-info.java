/**
 * <h2>cn.code91.facility.masking</h2>
 *
 * <p><b>Purpose:</b> Sensitive-data masking over arbitrary text with a fixed
 * built-in rule set — key-value secrets (password/token/secret/apiKey/...,
 * substring key semantics), bare JWTs, CN resident ID cards (GB 11643
 * mod 11-2), bank cards (Luhn), emails, and CN mobile numbers. ID-card and
 * bank-card masking is checksum-gated, so snowflake IDs, epoch timestamps and
 * other long digit runs are spared from false positives.</p>
 *
 * <p><b>Entry classes:</b> {@code MaskUtil}.</p>
 *
 * <p><b>Design (ADR-0020):</b> pure-JDK static facade — a single pre-compiled
 * alternation {@code Pattern} scanned in one pass; never throws, {@code null}
 * passes through, and when nothing is effectively masked the original string
 * instance is returned. No Spring bean / no autoconfiguration / no properties
 * / no error codes. {@code LogUtil} masks every message via
 * {@code MaskUtil.mask} by default, before both the disk write and the
 * {@code LogPostHandler} dispatch (a one-way dependency from {@code log} to
 * {@code masking}); {@code Throwable} messages and stack traces are not
 * masked (honest limit).</p>
 *
 * <p><b>Depends on:</b> nothing beyond the JDK ({@code java.util.regex}) —
 * no facility package, no third-party library.</p>
 *
 * <p><b>Depended on by:</b> {@code log} ({@code LogUtil} pre-write masking)
 * and downstream application code (ad-hoc text masking).</p>
 */
package cn.code91.facility.masking;
