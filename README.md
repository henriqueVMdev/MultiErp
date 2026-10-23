# erp-connector-lib

> Working name. Status: early development, no release yet.

A stateless, framework-free Java library that reads sales orders from ERP systems and returns them in one canonical model.
Each ERP is supported by a connector; applications using the library never deal with an ERP's own format.

The library stores nothing. The application using it keeps credentials, tokens and sync cursors, and decides when to run.

## Modules

| Module | Purpose |
|---|---|
| `core` | Canonical model, connector interface, typed errors, validation. No runtime dependencies. |
| `testkit` | Conformance suite and architecture rules every connector must pass. |
| `connector-fake` | In-memory connector used to test the testkit. Not published. |

## Build

Requires Java 21 and Maven 3.9+.

```
mvn verify
```

## License

[Apache-2.0](LICENSE)
