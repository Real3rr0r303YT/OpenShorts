# Contributing to OpenShorts

Thanks for your interest in improving OpenShorts! 🎉

## Ground rules

- **Privacy first.** Do not add analytics, tracking, crash-reporting, advertising or
  Google Play Services / Firebase dependencies. Pull requests that do will be declined.
- **Keep dependencies minimal.** Every new library must be open source, GPLv3-compatible and
  available from Maven Central or Google's Maven repository. Explain why it is needed in the PR.
- **HTTPS only.** Network requests go exclusively to the instance(s) the user configured.
- By contributing, you agree that your contributions are licensed under the
  [GNU GPL v3.0](LICENSE).

## Getting started

1. Fork the repository and create a branch from `main`:
   `git checkout -b feature/my-change`
2. Build and test locally:
   ```bash
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
3. Commit with a clear message (e.g. `feed: preload next video`).
4. Open a pull request and describe *what* and *why*. Screenshots help for UI changes.

## Code style

- Kotlin, following the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
  (`kotlin.code.style=official`).
- Jetpack Compose + Material 3 for all UI. Use colors and typography from `MaterialTheme`, never
  hard-coded values (the full-screen video overlay is the only exception).
- User-facing text belongs in `res/values/strings.xml`. If you can, add the German translation in
  `res/values-de/strings.xml` too.
- Add unit tests for non-UI logic in `app/src/test/`.

## Translations

Copy `app/src/main/res/values/strings.xml` to `values-<language>/strings.xml`, translate the
strings (leave `translatable="false"` entries out) and open a pull request.

## Reporting bugs and requesting features

Use GitHub Issues. Please include your Android version, device, app version and the
PeerTube instance you were using. **Do not** report security vulnerabilities publicly —
see [SECURITY.md](SECURITY.md).

## Supporting the project

Not a coder? You can still help by testing, translating, spreading the word or
[buying the developer a coffee](https://buymeacoffee.com/3rr0r303YT). ☕
