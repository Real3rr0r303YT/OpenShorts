# OpenShorts

**A privacy-friendly, decentralized short-video client for Android.**

OpenShorts brings the familiar vertical, full-screen "swipe to the next video" experience to the
[Fediverse](https://joinpeertube.org/) — starting with **PeerTube**. No account, no ads, no tracking,
no analytics SDKs. Videos are fetched anonymously over HTTPS from the instance *you* choose.

[![Build](https://github.com/Real3rr0r303YT/OpenShorts/actions/workflows/build.yml/badge.svg)](https://github.com/Real3rr0r303YT/OpenShorts/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Buy Me a Coffee](https://img.shields.io/badge/Support-OpenShorts-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/3rr0r303YT)

---

## ☕ Support OpenShorts

OpenShorts is built in spare time and will always be free and ad-free.
If you like the project, you can support its development here:

### 👉 **[buymeacoffee.com/3rr0r303YT](https://buymeacoffee.com/3rr0r303YT)**

The same link is available inside the app under **Settings → Support OpenShorts**.

---

## Download

📦 **[Download the latest APK](https://github.com/Real3rr0r303YT/OpenShorts/releases/latest)** (Android 8.0+)

Install `OpenShorts-vX.Y.Z-debug.apk`. Each release includes SHA-256 checksums so you can verify the file.

## Features

- 📱 **TikTok-style vertical feed** – one-video-per-swipe snap, the next clip is pre-buffered for instant swipes,
  vertical clips fill the screen, endless loop, tap to pause, **double-tap to like**, long-press for a clean view
- ⚡ **For You** – short clips under 60 s with vertical clips first (falls back to ≤ 3 min), plus category searches
  for Edits, Cybersecurity, Gaming, Tech, Anime and Shorts with trend badges in the search
- 🛡️ **Tech & cybersecurity first** – the default **Tech** tab shows videos tagged
  `cybersecurity`, `hacking`, `linux`, `infosec`, `programming` and `tech`; tech-focused PeerTube servers are offered
  as quick picks. Tabs for Trending, Latest and Local are one tap away.
- ▶️ **YouTube Shorts (optional)** – a second feed via public **Piped/Invidious** proxies, switchable in the header
  (PeerTube | YouTube Shorts) with For You, Edits, Cybersecurity, Gaming, Tech and Anime tabs
- 🎵 **TikTok embeds (optional)** – share a TikTok link to OpenShorts (or paste it) and it plays full screen in
  TikTok's official embed player inside the swipe feed
- ⏭️ **Auto-skip** – videos that cannot be played are skipped automatically instead of stopping the feed
- 🔍 **Search** – free text, `#tags` and `@creator@server`, live creator suggestions, tech topic shortcuts
- ❤️ **Action rail** on every video – glowing buttons with touch feedback and haptics: creator avatar with animated
  ring and follow button, like with particle burst and live counter, comments, save, share, spinning creator disc
- 💬 **Comments** – read a video's PeerTube comments in a bottom sheet
- 👤 **Profile** – avatar, custom banner (image or color), bio, supporter badge, your own tip link, stats
  (following / followers / likes) and tabs for posted, liked and saved videos and followed creators
- ✉️ **Inbox & chat** – conversations with creators and contacts (see limitations below)
- 🎨 **Theme engine (Material 3)**
  - Light / dark / system mode, **Material You** dynamic color (Android 12+), **AMOLED black**
  - Six color schemes including a **custom accent color** (hue slider)
  - **Custom fonts**: bundled Poppins, Atkinson Hyperlegible, Space Mono and Pacifico (`res/font/`), or
    **import your own .ttf/.otf** – applied app-wide
- 🌐 **Server selection** – tech PeerTube servers as presets or any custom HTTPS server
- 🔒 **Privacy by design**
  - Only the `INTERNET` permission, HTTPS only (cleartext traffic disabled)
  - No Google Play Services, Firebase, analytics, crash reporters or ads in the app itself
    (optional TikTok embeds and direct YouTube loading contact TikTok/Google – see below)
  - Profile, likes, follows and chats are stored only on the device and excluded from cloud backups
- 🛠️ **Transparent builds** – every APK is built by GitHub Actions with published SHA-256 checksums

### PeerTube account (optional)

Log in with an account of **any PeerTube server** (Settings → PeerTube account, or "+"):

- OAuth2 password grant directly against your server (incl. two-factor codes). PeerTube offers no
  browser-based login for third-party apps, so this is the official way apps sign in.
- Your password is never stored. Access/refresh tokens are kept in `EncryptedSharedPreferences`
  (key in the Android Keystore) and excluded from backups; logging out revokes the token on the server.
- When logged in, likes, follows (subscriptions) and bookmarks ("watch later") are sent to your server,
  you can write comments, see a "Following" feed and upload shorts with title, description, tags and visibility.

### YouTube Shorts via Piped / Invidious

The YouTube Shorts feed uses public [Piped](https://github.com/TeamPiped/Piped) and
[Invidious](https://invidious.io) instances (e.g. `pipedapi.kavin.rocks`, `invidious.nerdvpn.de`). Search results,
thumbnails and video streams come from these proxies; OpenShorts never talks to YouTube or Google directly.
Public instances are run by volunteers and are often rate-limited or blocked by YouTube, so the app tries several
of them in order. If none responds, the YouTube feed shows an error while PeerTube keeps working.
Likes, saves and follows of YouTube videos are stored only on the device. The source can be turned off in the settings,
and you can enter **your own Piped or Invidious instance** (Settings → Feed & server), which is always tried first.

### TikTok embeds and direct YouTube loading

- **TikTok:** TikTok has no public discovery API, so the TikTok tab plays links you share to OpenShorts
  (TikTok app → Share → OpenShorts) or paste. They play in TikTok's **official embed player**; TikTok sees your IP
  address and runs its own scripts in that player. Known analytics/ad hosts are blocked and third-party cookies are
  off, but this is not as private as PeerTube. Can be turned off in the settings.
- **Load directly from YouTube** (off by default): if all Piped/Invidious instances fail, Shorts are read from
  m.youtube.com (`ytInitialData` for search, `ytInitialPlayerResponse` for streams). Google then sees your IP address.
  Streams protected by YouTube's signature cipher are **not** decrypted – those videos are skipped, so this fallback
  often finds only part of the videos. YouTube's terms do not allow third-party clients; use it at your own discretion.

### Current limitations (honest list)

- **Direct messages:** PeerTube has no direct-message API. Chats are stored on the device and are **not delivered**
  to the contact yet. The `ChatRepository` is where a real transport (e.g. Matrix) will plug in.
- **Without login** the profile is local only: likes and follows stay on the device and the follower count is 0.
- **Uploads** run while the app is open (no background service yet); large files are sent in 8 MB chunks.
- Profile banner, bio and tip link are stored locally and not (yet) written to the PeerTube account.
- `androidx.security:security-crypto` is deprecated by Google but still maintained for existing apps; a migration
  to a direct Keystore implementation is planned.

## Screens

| Feed | Profile | Inbox | Settings |
|------|---------|-------|----------|
| Tabs (Tech, Trending, Latest, Local), search, action rail, comments | Banner, avatar, bio, stats, liked/saved videos, following | Conversations & chat | Support button, theme, colors, fonts, servers |

## Tech stack

| Area | Library |
|------|---------|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Playback | AndroidX Media3 / ExoPlayer (HLS + progressive) |
| Persistence | Jetpack DataStore (Preferences), EncryptedSharedPreferences for tokens |
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

**Releases:** pushing a version tag (`git tag v1.1.0 && git push origin v1.1.0`), publishing a
release in the GitHub UI, or pushing a commit whose message contains `[release]` uploads the debug
APK, the unsigned release APK and `SHA256SUMS.txt` as release assets
(via [softprops/action-gh-release](https://github.com/softprops/action-gh-release)).
Without a tag, the version is taken from `versionName` in `app/build.gradle.kts`.

## Project structure

```
app/src/main/java/org/openshorts/
├── MainActivity.kt          # Edge-to-edge activity, simple screen switching
├── OpenShortsApp.kt         # Application + tiny dependency container
├── data/
│   ├── AppSettings.kt       # Settings model (theme, palette, font, servers…)
│   ├── SettingsRepository.kt# DataStore persistence
│   ├── ProfileRepository.kt # Local account: profile, likes, saved videos, follows
│   ├── ChatRepository.kt    # Conversations (stored locally)
│   ├── LocalFiles.kt        # Import of profile images and fonts
│   ├── SearchQuery.kt       # Search syntax (#tag, @creator) and tech tags
│   └── peertube/            # PeerTube API client and models
└── ui/
    ├── theme/               # Theme engine: palettes, accent color, Material You, AMOLED, fonts
    ├── feed/                # Feed, video page + action rail, search, comments, creator sheet
    ├── profile/             # Profile and profile editor
    ├── chat/                # Inbox and chat
    ├── settings/            # Settings screen + donation card
    └── components/          # Image loader, avatar, formatting helpers
```

### Fonts

Users can import any `.ttf`/`.otf` font in **Settings → Font**. To bundle another font with the app:

1. Put the font files (lowercase names, e.g. `inter_regular.ttf`) into `app/src/main/res/font/`.
2. Add an entry to `FontChoice` in `data/AppSettings.kt`.
3. Map it in `fontFamilyFor()` in `ui/theme/Type.kt` and add a label string.
4. Only use fonts with a GPL-compatible license (e.g. SIL OFL), put the license into `licenses/fonts/` and list
   the font in [CREDITS.md](CREDITS.md).

## Roadmap

- [ ] Delivered direct messages via a decentralized protocol (e.g. Matrix)
- [ ] "Following" feed built from followed creators
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
  Made by <a href="https://github.com/Real3rr0r303YT"><b>Real3rr0r303YT</b></a> with ❤️ for an open, decentralized web ·
  <a href="https://buymeacoffee.com/3rr0r303YT"><b>Support OpenShorts</b></a>
</p>
