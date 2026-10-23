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

## Using the snapshot

Every push to `master` publishes `0.1.0-SNAPSHOT` of `erp-connector-core` and `erp-connector-testkit` to GitHub Packages.
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

## License

[Apache-2.0](LICENSE)
