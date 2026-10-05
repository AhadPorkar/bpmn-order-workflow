# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - 2026-10-04

### Added

- BPMN process `order-to-cash` (version tag 1.0.0) with a user task and form, a DMN business rule task, an error
  boundary event, a receive task with message correlation, reminder and deadline timers, and compensation.
- DMN decision `order-approval` (hit policy FIRST) for approval, payment term and reminder time.
- Spring Boot service with job workers, REST API, PostgreSQL schema (Flyway), Kafka payment listener with a dead-letter
  topic, and a transactional outbox relayed to `order-events`.
- Process, decision, unit, integration and end-to-end tests.
- Docker Compose environment, multi-stage Dockerfile, CI, release to GHCR and Dependabot.
- Documentation in English and German: README, architecture, testing, eight ADRs.

[Unreleased]: https://github.com/AhadPorkar/bpmn-order-workflow/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/AhadPorkar/bpmn-order-workflow/releases/tag/v1.0.0
