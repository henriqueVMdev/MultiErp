# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).
The canonical model and the connector interface are the public API: changing them is recorded here with its semver impact.

## [Unreleased]

### Changed
- `testkit`: the conformance suite's order ids are digits only (`"1007"` instead of `"order-07"`), so a test backend
  that keys orders by a number, such as a SQL table, can hold them. `fetchOrder` is also checked with a numeric id that
  does not exist. Test-only change; the core API is unchanged.

### Added
- Maven multi-module skeleton: `core`, `testkit`, `connector-fake`.
- Architecture rules (ArchUnit): `core` is framework-free, no mutable static fields, no package cycles.
- CI: build, tests and secret scan.
- Canonical model (public API, initial): `Order`, `Customer`, `TaxDocument`, `Item`, `Payment`, `PaymentMethod`,
  `Money`, `OrderStatus`, `SalesChannel`, `Anomaly` in `core.model`. Absent data is `Optional.empty()`.
- `TaxDocuments` in `core.validation`: CPF and CNPJ check digits, including the alphanumeric CNPJ.
- `Order.channelOrderNumber` (the sales channel's own order number) and `Customer.city` / `Customer.state` (UF).
- Connector interface (public API, initial): `ErpConnector`, `ConnectorInfo`, `OrderPage` in `core.connector`.
- Typed errors (public API, initial): sealed `ConnectorException` and its five subtypes in `core.error`.
- Retry: `RetryPolicy`, `Retrier`, `Sleeper` in `core.retry`.
- `testkit` (public API, initial): `ConnectorConformanceSuite`, `SeedOrder`, `SyntheticDocuments`; `ArchitectureRules` ignores compiler-generated fields.
- `connector-fake` (not published): reference `FakeConnector` over an in-memory `FakeErp`, and a self-test proving the suite fails against broken connectors.
- Publication of `0.1.0-SNAPSHOT` (parent pom, `core`, `testkit`) to GitHub Packages, run manually.
