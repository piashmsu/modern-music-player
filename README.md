# Modern Music Player

A full-featured offline music player for Android, written in Kotlin + Jetpack
Compose, powered by Media3 / ExoPlayer. Designed for Bangla listeners — UI
strings, EQ presets and number rendering all have first-class Bangla support
— but it works equally well as a daily-driver player in any language.

**Package:** `com.gsmtrick.musicplayer`
**Latest version:** 3.2 (versionCode 8)
**Min SDK:** 23 (Android 6.0)  •  **Target SDK:** 34 (Android 14)

---

## Features

### Playback engine
- Media3 / ExoPlayer with gapless decoding, bit-perfect output
- 10-band system equalizer + bass boost, virtualizer, loudness, reverb
- Pitch shift (-12..+12 semitones), per-song speed override
- Channel mixing: balance L/R, mono / reverse-stereo, karaoke (vocal removal)
- Crossfade (0–12 s, smooth volume ramp at end-of-track)
- Skip silence (Media3 native)
- Sleep timer with fade-out + "stop at end of song"
- A-B loop, lock-screen player

### Library
- MediaStore-backed library with songs / albums / artists / genres / folders
- Auto-tag from filename when MediaStore has no real metadata
  ("Artist - Title.mp3" → fills missing fields)
- Smart playlists: rule-based (play count, genre, year, BPM, …)
- Trash + restore, hidden songs, profiles
- Backup & restore (encrypted JSON, optional password)

### Online
- YouTube tab (powered by NewPipeExtractor) — search & stream music
- Internet Radio tab — curated Bangla / world stations + custom URLs
- Last.fm scrobbling (configurable Wi-Fi-only, follows the standard
  >=30 s / >=50 % rule)

### UI / theming
- Material 3 with dynamic-color (Material You) support on Android 12+
- 8 launcher icon variants (Classic, Neon, Minimal, Vinyl, Dark,
  Sunset, Ocean, Gold)
- 5 accent colours when dynamic-color is off
- Full Bangla translation + Bangla numerals toggle (০–৯ in durations
  and counts)
- 6 Bangla folk EQ presets: Baul, Rabindra, Nazrul, Adhunik, Bangla Pop,
  Qawwali

### Audio modes
- Headphone presets (Sony WH, AirPods, Boat, Realme, Beats)
- Workout / sleep / driving / cinema modes
- Auto-EQ by listening environment

---

## Building from source

The project uses the Android Gradle Plugin and is checked in as a standard
Android Studio project. You will need:

- JDK 17 (Android Gradle Plugin requirement)
- Android SDK 34 with build-tools 34.0.0
- Internet access for the first build (Maven Central, Google Maven)

```bash
git clone https://github.com/piashmsu/modern-music-player.git
cd modern-music-player

# Optional: enable Last.fm scrobbling by adding your API credentials.
# Without these the rest of the app still works — scrobbling is just
# disabled gracefully at runtime.
cat >> local.properties <<EOF
lastfm.apiKey=<your last.fm api key>
lastfm.apiSecret=<your last.fm api secret>
EOF

./gradlew assembleDebug      # debug-signed APK in app/build/outputs/apk/debug/
./gradlew assembleRelease    # release APK (currently signed with the debug key)
```

To install via ADB:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## Architecture

- **MVVM** with a single `PlayerViewModel` exposing reactive `StateFlow`s
  (library, prefs, playlists, lyrics, audio session id, playback state).
- **Repositories** for each persistence concern:
  - `MusicRepository` — MediaStore loaders for songs / albums / artists
    / genres / folders.
  - `PreferencesRepository` — Jetpack DataStore, a single `AppPrefs` data
    class, JSON-encoded sub-fields for complex maps.
  - `PlaylistRepository` — user playlists.
  - `LyricsRepository` — local + online (caption-to-lyrics) loading.
  - `LastFmRepository` — minimal mobile-session scrobbler.
- **Playback** runs in `MusicPlaybackService` (a Media3 `MediaSessionService`).
  It owns the `ExoPlayer`, applies `EffectsState`, drives sleep-timer
  fade-out, end-of-track crossfade, and Last.fm now-playing/scrobble
  dispatch on a single 500 ms ticker coroutine.
- **UI** is pure Jetpack Compose with Material 3. Navigation uses
  `androidx.navigation.compose` with five top-level routes: `library`,
  `youtube`, `effects`, `settings`, `about` (plus `stats` and `radio`).

---

## License

This project is shared as-is for personal use. See LICENSE if/when added.
