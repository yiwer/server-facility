# Independent CSV fixtures

Generated 2026-10-04 with CPython 3.14 `csv.writer`, seed `0x15C5`, using the checked-in `generate.py`. The 164 records include Unicode/emoji, quote doubling, mixed embedded CR/LF, separators, empty fields and records. Expected values are the original independent Python input encoded per cell as Base64 UTF-8, joined by commas; no facility writer/parser generates expected results. A zero-cell Python row is explicitly expected as one empty CSV field under this library's documented record convention.

Reproduce: `python src/test/resources/csv/generate.py`.

- `python-golden.csv` SHA256 `1ec58ce2d16219e962a04cb1dd4c7734f8130517c2bad8ab40430bb034d5596e`
- `python-golden.expected` SHA256 `8656c80014a8a105369e62c36914eaafe14dedf0ed179268cceb2c1eb161d0b8`

The CSV is binary to Git so its CRLF record separators and embedded field line endings survive Windows/Linux checkouts. Runtime tests require Java only. Python `csv`'s permissive input defaults are not used to define the STRICT dialect; strict/legacy malformed cases have separate literal tests.
