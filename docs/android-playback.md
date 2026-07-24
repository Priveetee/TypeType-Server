# Android playback contract

TypeType-Android discovers this contract through `androidPlayback` in
`GET /api/instance`. Contract version 4 supports completed YouTube VODs only;
active livestreams remain explicitly unsupported.

Android playback sessions use `/api/android/youtube/playback/*` and are isolated
from the web player's `/api/sabr/playback/*` sessions, generations, caches, and
window protocol. A ready VOD manifest is a complete static DASH presentation
from time zero. The server requires exact audio and video segment indexes, but
continues to fetch media bytes on demand.

Creating a session may return `202` while one shared background task obtains the
exact initialization indexes. The response includes `preparationStage` and
`retryAfterMs`; manifest polling only reads that task's current state and never
starts duplicate network work. Preparation has an eight-second server deadline.
It ends with a complete static MPD, `422 android_playback_invalid_index`, or a
typed `503` (`android_playback_preparation_timeout` or
`android_playback_preparation_failed`). A client should retry only the same
manifest URL after `retryAfterMs` and create a new session after a terminal
error.

The index task fetches only the selected formats' bounded initialization ranges.
It does not preload media. The SABR pump starts later when Media3 requests media
segments, so segment delivery remains demand-driven.

A seek keeps the session ID and selected itags stable, increments the generation,
and makes older media URLs return `409`. Unknown sessions return `404`; recently
expired sessions return `410`. Expiration cancels unfinished preparation work.
Session manifests and media remain same-origin and use `Cache-Control: no-store`.

Every successful creation response contains the complete authoritative
`subtitles` descriptor catalog. Videos without captions return an empty list.
The catalog is copied into the playback session and remains unchanged across
seek generations, so Android can attach every `SubtitleConfiguration` before
the first Media3 preparation.

Descriptor discovery reads caption metadata only. It does not fetch or convert
a subtitle document. Each descriptor points to a session-scoped
`/api/android/youtube/playback/{sessionId}/subtitles/{trackId}.vtt` resource,
and the server obtains that WebVTT content only when the resource is requested.
The `deferredSubtitleContent` and `bootstrapSubtitleDescriptors` capabilities
advertise these separate guarantees.
