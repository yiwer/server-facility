# Frozen command schema checkpoint

V1–V3 are exact copies of the ticket29 application checkpoint qualified by CI19 at `ae7215eb6fea6c12aea9d4bb450aaf4ad21ab0fa`, also present unchanged in ticket30 base `3f25c38a680be498ead77bf29fb7d5caf30537f6`. They are compatibility fixtures, not a claim about an earlier public production release. Keep them unchanged when current migrations advance.

The command golden uses the independent UTF-8 length-prefixed SHA-256 literals already checked in NoteCommandProtocolTest (Python struct.pack/hashlib, with an additional PostgreSQL actor probe). Literal SQL below supplies old data without invoking the current Notes implementation.
