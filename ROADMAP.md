# NOCTURNE Roadmap

## Milestone 0.1 — Android foundation
- [x] Native Jetpack Compose UI
- [x] MediaStore local library scanner
- [x] Media3 playback engine and MediaSession service
- [x] Search and persistent favourites
- [x] Browse and play filtered queues by song, album, or artist
- [x] Album-art lookup from local media provider
- [x] Metadata cache schema and AI engine contracts
- [x] GitHub Actions workflow configured to produce a debug APK artifact
- [x] APK build verification on GitHub Actions
- [ ] Install and playback QA on a physical Android device

## Milestone 0.2 — Music DNA
- [ ] AcoustID/MusicBrainz provider adapter and commercial-use review
- [x] Offline genre extraction from MediaStore and genre filtering in the library
- [x] Composer tags from Android MediaStore included in Music DNA and library search
- [ ] Broader embedded tag extraction and metadata reconciliation
- [ ] Fingerprint queue, retry strategy, rate limiting, confidence/source tracking
- [x] User-triggered on-device BPM estimate from up to 30 seconds of a local track; cached per track
- [x] Approximate slow/steady/fast tempo filters for tracks with stored BPM
- [ ] Validate tempo accuracy against reference tracks; implement key/rhythm analysis and filtering
- [ ] Filter builder and rule-based smart playlists

## Milestone 0.3 — Stem Lab
- [ ] Benchmark eligible models (quality, runtime, memory, thermals, licensing)
- [ ] On-device inference prototype for vocals/drums/bass/other
- [ ] Export location and storage management
- [ ] Stem playback/mixer with solo, mute and per-stem volume
- [ ] Optional cloud fallback only with clear consent and cost disclosure

## Release gate
No online metadata query, AI analysis, or stem separation should be represented as complete until the provider/model is connected and tested. No secret API keys should be embedded in the Android app.
