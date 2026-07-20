# Android playback contract

TypeType-Android discovers this contract through `androidPlayback` in
`GET /api/instance`. Contract version 1 supports completed YouTube VODs only;
active livestreams remain explicitly unsupported.

Android playback sessions use `/api/android/youtube/playback/*` and are isolated
from the web player's `/api/sabr/playback/*` sessions, generations, caches, and
window protocol. A ready VOD manifest is a complete static DASH presentation
from time zero. The server requires exact audio and video segment indexes, but
continues to fetch media bytes on demand.

Creating a session may return `202` while the exact indexes are preparing. A
seek keeps the session ID and selected itags stable, increments the generation,
and makes older media URLs return `409`. Unknown sessions return `404`; recently
expired sessions return `410`. Session manifests and media remain same-origin
and use `Cache-Control: no-store`.
