# ADR 0001: Programmatic JavaFX views for the foundation

- Status: Accepted
- Date: 2026-09-02

## Context

Phase 1 needs a small navigation shell, Studies Home, Study Dashboard, a focused editor dialog,
loading states, and a startup/error boundary. The longer roadmap will add larger workspaces whose
layouts may eventually benefit from FXML.

## Decision

Use programmatic JavaFX views in Phase 1. Views own rendering and event binding, while all business
rules remain in services/domain objects and all SQL remains in persistence classes. Navigation is
centralized in `Navigator`; background work is centralized in `Async`.

## Consequences

- The foundation has no FXML loader/controller lifecycle or duplicate controller wiring.
- Refactoring a future complex workspace to FXML will not affect domain, service, or repository APIs.
- View classes must be kept small. A workspace should move to FXML or reusable components if its
  programmatic layout becomes difficult to scan or test.
