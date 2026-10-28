# OpenShorts

**A privacy-friendly, decentralized short-video client for Android.**

OpenShorts brings the familiar vertical, full-screen "swipe to the next video" experience to the
[Fediverse](https://joinpeertube.org/) — starting with **PeerTube**. No account, no ads, no tracking,
no analytics SDKs. Videos are fetched anonymously over HTTPS from the instance *you* choose.

[![Build](https://github.com/Real3rr0r303YT/OpenShorts/actions/workflows/build.yml/badge.svg)](https://github.com/Real3rr0r303YT/OpenShorts/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Buy Me a Coffee](https://img.shields.io/badge/Support-OpenShorts-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/3rr0r303yt)

---

## ☕ Support OpenShorts

OpenShorts is built in spare time and will always be free and ad-free.
If you like the project, you can support its development here:

### 👉 **[buymeacoffee.com/3rr0r303yt](https://buymeacoffee.com/3rr0r303yt)**

The same link is available inside the app under **Settings → Support OpenShorts**.

---

## Download

📦 **[Download the latest APK](https://github.com/Real3rr0r303YT/OpenShorts/releases/latest)** (Android 8.0+)

Each release includes SHA-256 checksums so you can verify the file.

## Features

- 📱 **Vertical full-screen feed** – swipe up/down between videos, tap to pause/resume
- 🌐 **PeerTube support** – use any public instance (default: `framatube.org`)
- ⏱️ **Shorts filter** – optionally show only videos up to 3 minutes
- 🎨 **Material Design 3 theme engine**
  - Light / dark / system mode
  - Material You dynamic color (Android 12+)
  - Five built-in color schemes (Ocean, Forest, Sunset, Grape, Mono)
  - Pure-black AMOLED mode
  - Selectable fonts, with support for bundling your own
- 🔒 **Privacy by design**
  - Only the `INTERNET` permission
  - HTTPS only (cleartext traffic disabled)
  - No Google Play Services, Firebase, analytics, crash reporters or ads
  - Settings are stored locally and excluded from cloud backups
- 🛠️ **Transparent builds** – every APK is built by GitHub Actions with published SHA-256 checksums

## Screens

| Feed | Settings |
|------|----------|
| Full-screen video, channel, title, stats, share & open-in-browser | Donation card, theme, colors, font, instance, playback options, about |

## Tech stack

| Area | Library |
|------|---------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 / ExoPlayer (HLS + progressive) |
| Persistence | Jetpack DataStore (Preferences) |
| Networking / JSON | `HttpURLConnection` + kotlinx.serialization |

The dependency list is intentionally short — see [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Building

### Requirements

- JDK **17** or newer
- Android SDK with platform **36** (Android Studio installs this automatically)

### Command line

```bash
git clone https://github.com/Real3rr0r303YT/OpenShorts.git
cd OpenShorts

# Debug APK  -> app/build/outputs/apk/debug/
./gradlew assembleDebug

# Unsigned release APK -> app/build/outputs/apk/release/
./gradlew assembleRelease

# Unit tests and lint
./gradlew testDebugUnitTest lintDebug
```

If the SDK is not found, create a `local.properties` file with
`sdk.dir=/path/to/Android/Sdk` or set the `ANDROID_HOME` environment variable.

### Android Studio

Open the project folder, let Gradle sync, and run the `app` configuration.

### CI builds

Every push and pull request is built by [`.github/workflows/build.yml`](.github/workflows/build.yml):
unit tests → lint → debug & unsigned release APK. The APKs and a `SHA256SUMS.txt` file are attached
to each workflow run as artifacts, and the checksums are printed in the run summary, so anyone can
verify that a downloaded APK was built from the public source.

Pushing a version tag (`git tag v0.2.0 && git push origin v0.2.0`) — or pushing a commit whose
message contains `[release]` — publishes a public GitHub release with the APK automatically.
Without a tag, the version is taken from `versionName` in `app/build.gradle.kts`.

## Project structure

```
app/src/main/java/org/openshorts/
├── MainActivity.kt          # Edge-to-edge activity, simple screen switching
├── OpenShortsApp.kt         # Application + tiny dependency container
├── data/
│   ├── AppSettings.kt       # Settings model (theme, palette, font, instance…)
│   ├── SettingsRepository.kt# DataStore persistence
│   ├── InstanceUrl.kt       # Instance URL validation
│   └── peertube/            # PeerTube API client and models
└── ui/
    ├── theme/               # Theme engine: palettes, dynamic color, fonts, dark mode
    ├── feed/                # Vertical pager feed + ExoPlayer surface
    ├── settings/            # Settings screen + donation card
    └── components/          # Thumbnail loader, formatting helpers
```

### Adding a custom font

1. Put the font files (e.g. `inter_regular.ttf`) into `app/src/main/res/font/`.
2. Add an entry to `FontChoice` in `data/AppSettings.kt`.
3. Map it to a `FontFamily` in `ui/theme/Type.kt` and add a label string.
4. Only use fonts with a GPL-compatible license (e.g. SIL OFL) and list them in [CREDITS.md](CREDITS.md).

## Roadmap

- [ ] Multiple instances / federated search
- [ ] Channel pages and subscriptions (stored locally)
- [ ] Comments (read-only)
- [ ] Additional platforms
- [ ] F-Droid release

## Contributing

Contributions are welcome! Please read [CONTRIBUTING.md](CONTRIBUTING.md) first.
Security issues: see [SECURITY.md](SECURITY.md).

## License

```
OpenShorts – a privacy-friendly, decentralized short-video client
Copyright (C) 2026 OpenShorts contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

See [LICENSE](LICENSE) for the full text.

---

<p align="center">
  Made with ❤️ for an open, decentralized web ·
  <a href="https://buymeacoffee.com/3rr0r303yt"><b>Support OpenShorts</b></a>
</p>
