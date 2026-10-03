# Explicit HTML policy consumer

`PolicySamples.java` contains literal inputs and independently specified HTML-body expectations. It can run directly with either jsoup jar; `observe` records historical behavior without asserting the target policy. No cleaner generates its own expected strings. Root tests and the ordinary-jar consumer supplement these upgrade samples with API and finite-resource contracts.

2026-10-04 comparison: jsoup 1.18.3 versus 1.23.2 on Oracle JDK25.0.4.1. The current jar is obtained from Maven Central, SHA256 `e9d8c856856680427f096d156f02a414289985c930998fcb519ce5be39b42003`.

- `hostless-http`: old `<a href="http:" rel="nofollow">go</a>`, new `<a rel="nofollow">go</a>`. A scheme without a host is not an accepted HTTP destination.
- `iframe`: old `PRIVATE<b>ok</b>`, new `<b>ok</b>`. Unsupported iframe fallback text is no longer retained by the parser/cleaner combination. Applications depending on such text must migrate their stored input; do not select iframe as a plain-text transport. The initial target expectation was removal; one exploratory attempt used the old fallback expectation and correctly failed on the new version, then restored the intended target expectation. That failed observation is retained.
- The other 14 examples are unchanged: explicit Unicode, entities, allowed tags, event/style removal, URI encoding/case variants, malformed nesting and comment removal.

The first Windows observation used native GBK stdout and lost Unicode in the log. It is retained but not used as Unicode evidence. The subsequent command explicitly sets `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`, preserving the checked-in UTF-8 sample. Logs are under `.verification-results/ticket-32`; they distinguish observations, rejected expectations, and strict successful verification.

Source context: [1.23.2 release notes](https://jsoup.org/news/release-1.23.2), [jsoup Safelist guide](https://jsoup.org/cookbook/cleaning-html/safelist-sanitizer). These checks concern HTML body fragments, not script/style/attribute output contexts or a browser sandbox.
