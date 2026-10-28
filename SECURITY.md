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
- No user account or credentials are stored. Settings live in a local DataStore file that is
  excluded from cloud backups.
- Release APKs are built publicly by GitHub Actions; SHA-256 checksums are published with every run.
