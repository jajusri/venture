# Venture Mobile

Android-first Flutter application for read-only Tally access.

## Architecture

```
lib/
├── app/              # MaterialApp, theme, routing entry
├── core/             # DI, platform types, events, feature flags
├── data/             # Remote/local datasources, repository implementations
├── domain/           # Repository interfaces, use cases
├── features/         # Feature modules (connection, ledgers, vouchers, ...)
└── presentation/     # Screens and widgets
```

Rules enforced from Milestone 0:

- UI does not call HTTP or SQL directly
- Feature modules depend on repository/use-case interfaces
- Capability checks live in the domain/application layer

## First-time setup

Platform folders are not committed in Milestone 0. Generate them once Flutter SDK is installed:

```bash
cd apps/venture_mobile
flutter create . --project-name venture_mobile --org com.venture --platforms=android
flutter pub get
flutter analyze
flutter test
```

From the repo root, prefer Melos:

```bash
melos bootstrap
melos run analyze
melos run test
```
