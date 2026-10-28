# Changelog

## [1.0.3]

### Feed & "For You"
- New default **For You** tab (PeerTube "hot" ranking) – short clips under 60 seconds, vertical clips first;
  falls back to clips up to 3 minutes so the feed is never empty
- Searches like "edits", "cybersecurity", "gaming", "tech", "anime" or "shorts" become category searches for
  short clips with matching tags; text searches ask the server for clips under 60 s (`durationMax`)
- Instant swipes: two players take turns, the next clip is buffered (first frame ready) before you swipe
- TikTok-style snap: one video per swipe, quick flick threshold, no shrink effect; faster start of playback
- Vertical clips fill the whole screen; the bottom navigation floats transparently over the video
- Long-press a video for a clean, button-free full-screen view
- Videos loop endlessly by default

### Look & feel
- Action rail with glow effects, press-to-shrink touch feedback and haptics
- Like button with particle burst, bounce and rolling live counter; rotating gradient ring around the creator avatar
- Text shadows for better readability on bright videos; gradient progress bar

### Search
- "Trending now" badges when opening the search: Edits, Cybersecurity, Gaming, Tech, Anime, Shorts

## [1.0.2]

### Added
- **PeerTube login** (OAuth2 password grant, incl. two-factor codes) for any PeerTube server;
  tokens are stored encrypted (EncryptedSharedPreferences / Android Keystore) and refreshed automatically
- Likes, follows (subscriptions) and bookmarks ("watch later" playlist) are sent to the server when logged in;
  videos from other servers are resolved on your own server first
- "Sync" merges server likes, bookmarks and subscriptions into the profile and sends local ones to the server
- Write comments under videos when logged in
- **"+" upload** in the bottom bar and profile: resumable upload of a gallery video to your channel with title,
  description, up to 5 tags (#cybersecurity, #tech, …) and visibility
- "Following" feed tab with videos of your subscriptions
- Profile shows your server account, real follower count and your posted videos
- Session-expired dialog when the server revokes the login

## [1.0.1]

### Added
- TikTok-style feed: action rail with creator avatar + follow, like button with animated live counter,
  comments, save and share; double-tap to like with a flying heart; spinning creator disc; progress bar;
  softer swipe animation; pull to refresh
- Default **Tech** feed (cybersecurity, hacking, linux, infosec, programming, tech) next to Trending, Latest and Local
- Search bar: free text, `#tags`, `@creator@server`, live creator suggestions and tech topic shortcuts
- Comments sheet with the video's PeerTube comments
- Creator sheet: follow, show their videos, send a message, open the channel
- Local profile: avatar, banner (image or color), bio, username, own tip link, supporter badge,
  stats and tabs for posted, liked and saved videos and followed creators
- Inbox and chat with contacts (stored on the device; not delivered yet – PeerTube has no DM API)
- Bottom navigation: Home, Inbox, Profile, Settings
- Theme engine: custom accent color, bundled fonts (Poppins, Atkinson Hyperlegible, Space Mono, Pacifico)
  and import of your own .ttf/.otf fonts
- Tech-focused PeerTube server presets in the settings

## [1.0.0]

### Added
- First release: vertical PeerTube video feed, Material 3 theme engine, settings and "Support OpenShorts" button
