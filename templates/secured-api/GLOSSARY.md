# Application language

- **Actor**: the authenticated party performing an operation, identified by its trusted issuer and stable subject. Display names, email addresses, network addresses and trace identifiers do not establish actor identity.
- **Authority**: a permission granted to an actor for the current request. Authentication alone does not grant every operation.
- **Trust policy**: the application's selected issuer, audience and verification requirements for credentials it accepts.
- **Workspace**: the tenant boundary containing notes and the current members permitted to access them.
- **Command identity**: one workspace, Actor, operation and caller-selected key identifying a single intended change across retries.
- **Business receipt**: the original successful command result available for a bounded period. It does not confer permission and its expiry does not release command identity.
- **Business-command budget**: the workspace's lifetime allowance of successful command identities. Replays do not spend it again; ingress admission is a separate allowance for attempts.
