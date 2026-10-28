# Security Policy

## Supported versions

| Version | Supported |
|---------|-----------|
| Latest release / `main` | ✅ |
| Older releases | ❌ |

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Report vulnerabilities privately via GitHub's
[private vulnerability reporting](https://github.com/Real3rr0r303YT/OpenShorts/security/advisories/new)
("Security" tab → "Report a vulnerability").

Please include:

- a description of the issue and its impact,
- steps to reproduce or a proof of concept,
- affected version(s) and Android version.

You can expect an acknowledgement within **7 days**. We will keep you informed about the fix and
credit you in the release notes unless you prefer to stay anonymous.

## Security & privacy design

- The app requests only the `INTERNET` permission.
- Cleartext (HTTP) traffic is disabled via `network_security_config.xml`; only system CAs are trusted.
- No analytics, tracking, crash-reporting or advertising SDKs are included.
- Logging in to PeerTube is optional. Passwords are never stored; OAuth access/refresh tokens are kept in
  `EncryptedSharedPreferences` (AES-256, key in the Android Keystore), excluded from all backups and revoked on
  logout. Tokens are only ever sent to the server they were issued by, over HTTPS.
- Settings and the local profile live in DataStore files that are excluded from cloud backups.
- The optional YouTube Shorts feed only contacts public Piped/Invidious instances (HTTPS); those instances see the
  search terms and which videos are played, YouTube/Google itself is never contacted by the app.
- Optional TikTok embeds load TikTok's official embed page in a WebView (JavaScript on, file/content access off,
  third-party cookies off, known analytics hosts blocked, navigation away from the embed blocked). TikTok sees the
  IP address. The optional direct YouTube loading contacts m.youtube.com and is off by default.
- Release APKs are built publicly by GitHub Actions; SHA-256 checksums are published with every run.
