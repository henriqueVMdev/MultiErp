# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses [Semantic Versioning](https://semver.org/).
The canonical model and the connector interface are the public API: changing them is recorded here with its semver impact.

## [Unreleased]

### Added
- Maven multi-module skeleton: `core`, `testkit`, `connector-fake`.
- Architecture rules (ArchUnit): `core` is framework-free, no mutable static fields, no package cycles.
- CI: build, tests and secret scan.
