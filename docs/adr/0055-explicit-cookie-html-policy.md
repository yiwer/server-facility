# ADR-0055: Explicit cookie scope and HTML fragment policy

Status: Proposed

Ticket: 32. Retains ADR-0001's optional jsoup dependency. No global request rewriting or authentication provider is introduced.

Cookie writes use Spring ResponseCookie as the immutable standard description. Legacy overloads keep their signatures and use host-only, SameSite=Lax, HttpOnly, Secure by default. Explicit policies support domain/path/flags/lifetime; deletion retains the original scope. Invalid or excessive headers fail before response mutation. Ambiguous duplicate request cookie names are rejected rather than selected as identity.

XssUtil remains an explicit HTML fragment cleaner. Original application input is never changed automatically; output-context encoding remains the application’s responsibility. The jsoup upgrade is accompanied by independently specified samples, historical comparison, and bounded input tests.
