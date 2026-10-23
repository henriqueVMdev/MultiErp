# erp-connector-lib

> Working name. Status: early development. `0.1.0-SNAPSHOT` available, no release yet, no public connector yet.

A stateless, framework-free Java library that reads sales orders from ERP systems and returns them in one canonical model.
Each ERP is supported by a connector; applications using the library never deal with an ERP's own format.

The library stores nothing. The application using it keeps credentials, tokens and sync cursors, and decides when to run.

## Modules

| Module | Purpose |
|---|---|
| `core` | Canonical model, connector interface, typed errors, retry, CPF/CNPJ validation. No runtime dependencies. |
| `testkit` | Conformance suite and architecture rules every connector must pass. |
| `connector-fake` | In-memory connector used to test the testkit. Not published. |

Connectors for private or in-house systems do not live here: they depend on `core` and prove conformance with `testkit`.

## Reading orders

```java
ErpConnector erp = ...; // one instance per ERP account
Retrier retrier = Retrier.of(RetryPolicy.defaults());

retrier.call(() -> { erp.checkConnection(); return null; });

Instant from = lastSyncedUpTo.minus(overlap);
Instant to = Instant.now();
OrderPage page = retrier.call(() -> erp.listOrders(from, to));
while (true) {
    for (Order order : page.orders()) {
        store(order); // upsert by (order.source(), order.externalId())
    }
    if (page.nextCursor().isEmpty()) break;
    String cursor = page.nextCursor().get(); // persist it to resume an interrupted read
    page = retrier.call(() -> erp.listOrders(cursor));
}
lastSyncedUpTo = to;
```

- **Windows** are `[changedFrom, changedTo)`. The caller passes `changedTo`, so the library never reads a clock.
- **At least once.** The same order can arrive more than once. Deduplicate by (`source`, `externalId`); when `updatedAt` is present keep the newest, otherwise the last read wins.
- **Overlap.** ERPs edit orders days after creation. Re-read the last hours on each sync.
- **Cursors** are opaque strings, valid only for the connector's ERP account. An empty `nextCursor` ends the read; a page can be empty and still have one.
- `fetchOrder(externalId)` reads a single order. `info()` tells which ERP it is and what it supports (`FILTER_BY_UPDATE_DATE`, `UPDATED_AT_IN_PAYLOAD`, `WEBHOOKS`, `SANDBOX`), without calling the ERP.

### The canonical model

`Order` carries the ERP id, the order and channel order numbers, created and updated dates, canonical status plus the ERP's text, sales channel (`DIRECT`, `MARKETPLACE`, `UNKNOWN` plus the raw text), customer, items, payments, shipping, discount, total, NF-e access key and a list of anomalies.
Records are immutable; money is `BigDecimal` plus currency; timestamps are UTC `Instant`s.

- **Absent is `Optional.empty()`**, never null, zero or an empty string. Nothing is invented: if the ERP does not say where an order came from, the channel is `UNKNOWN`, not `DIRECT`.
- **Bad data does not abort a read.** An invalid CPF/CNPJ, totals that do not add up or an unknown status come back as `Anomaly` entries on the order.
- **Customer documents** are normalized (no mask, upper case, alphanumeric CNPJ supported) and flagged with their check-digit result. `TaxDocuments` validates them on its own.

### Errors and retry

Every failure is an unchecked `ConnectorException`. The hierarchy is sealed, so a `switch` over it is exhaustive:

| Type | Retryable | When |
|---|---|---|
| `AuthenticationException` | no | credentials rejected |
| `RateLimitedException` | yes | request limit reached; optional `retryAfter` |
| `TransientException` | yes | timeout, 5xx, deadlock |
| `PermanentException` | no | other 4xx, missing table; optional `httpStatus` |
| `UnexpectedFormatException` | no | an order the connector cannot read; names the field, never its value |

`Retrier` repeats only retryable failures within a `RetryPolicy` (default: 3 attempts, waits from 1 s to 30 s, exponential backoff with full jitter).
A `retryAfter` is obeyed exactly; if it is longer than the maximum wait, the failure is thrown at once. When the budget is spent, the last failure is thrown as is.

## Personal data

- CPF/CNPJ, e-mail and phone never appear in full in `toString()` or exception messages; they are masked. Tokens and secrets never appear at all.
- The library does not log.
- It **reports** data. Whether a given use of that data is allowed is up to the application using it.

## Writing a connector

Depend on `erp-connector-core`, plus `erp-connector-testkit` in test scope, and extend the conformance suite:

```java
class MyErpConnectorConformanceTest extends ConnectorConformanceSuite {

    @Override
    protected ErpConnector connectorWith(List<SeedOrder> orders) {
        // a connector over a test backend (stubbed HTTP API, test database) holding exactly these orders,
        // with a page size of 10 or less
    }

    @Override
    protected ErpConnector connectorFailingWith(Failure failure, SeedOrder order) {
        // a backend failing that way while holding this order; retries configured without real sleeping
    }

    @Override
    protected Set<Failure> applicableFailures() {
        return EnumSet.complementOf(EnumSet.of(Failure.RATE_LIMITED)); // e.g. a database is never rate limited
    }

    @Override
    protected Set<String> secrets() {
        return Set.of(TEST_TOKEN); // none may appear in an exception message
    }
}
```

The suite checks complete pagination without loss or duplicates, the incremental window, repeatable reads, the mapping of every failure, that missing fields stay unknown, that an invalid document becomes an anomaly, and that no secret or personal data leaks into exception messages.
`ArchitectureRules` holds ArchUnit rules for connector code (no mutable static fields, no package cycles).

Connectors use documented, official APIs only, with one instance per ERP account and no global state.
Credentials come from the application, never from files in the repository. Test fixtures are synthetic; `SyntheticDocuments` generates valid fake CPF/CNPJ numbers.

## Build

Requires Java 21 and Maven 3.9+.

```
mvn verify
```

Runs every module's tests, including the ArchUnit rules. Use `mvn clean verify` after deleting sources.

## Using the snapshot

`0.1.0-SNAPSHOT` of `erp-connector-core` and `erp-connector-testkit` is published to GitHub Packages on demand (the "Publish snapshot" workflow).
GitHub Packages requires authentication even for public Maven packages: create a personal access token with the
`read:packages` scope and add it to `~/.m2/settings.xml` (or to your CI secrets), never to a repository.

```xml
<!-- ~/.m2/settings.xml -->
<servers>
  <server>
    <id>github-multierp</id>
    <username>YOUR_GITHUB_USER</username>
    <password>${env.GITHUB_PACKAGES_TOKEN}</password>
  </server>
</servers>
```

```xml
<!-- your pom.xml -->
<repositories>
  <repository>
    <id>github-multierp</id>
    <url>https://maven.pkg.github.com/henriqueVMdev/MultiErp</url>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.github.henriquevmdev</groupId>
    <artifactId>erp-connector-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  <!-- for connector authors: the conformance suite -->
  <dependency>
    <groupId>io.github.henriquevmdev</groupId>
    <artifactId>erp-connector-testkit</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>test</scope>
  </dependency>
</dependencies>
```

## Versioning

Semantic versioning. The canonical model and the connector interface are the public API; changes to them are recorded in [CHANGELOG.md](CHANGELOG.md) with their semver impact.

## License

[Apache-2.0](LICENSE)
