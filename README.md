# Duplicate Cleaner (Android, Kotlin)

Finds and safely removes duplicate **photos, videos and documents**. Offline, no ads, no analytics,
**no INTERNET permission**. Nothing is ever deleted without your selection *and* Android's own confirmation,
and at least one copy of every group is always kept.

## ⚠️ Verification status (read first)

This source tree was written in a sandbox that had **only a JDK**: no Android SDK, no Gradle, no Kotlin
compiler and no network. Therefore:

| Step | Status |
|---|---|
| `./gradlew clean`, `assembleDebug`, `testDebugUnitTest` | **NOT run** |
| Unit tests (written, not executed) | `data/hashing`, `domain`, `core`, `presentation` |
| Instrumented tests (written, not executed) | dHash on real bitmaps, MediaStore + delete, Compose home screen |
| APK | **not built** |
| Compile errors | possible; none could be detected without a compiler |

Expect a first-build fix-up pass (typos, one or two API signature mismatches, dependency versions).
Do not treat the project as tested until the commands below pass on your machine.

## Build

Requirements: Android Studio Ladybug+ (or JDK 17 + Android SDK 35), internet for the first Gradle sync.

1. Open the folder in Android Studio and let it sync. The `gradle-wrapper.jar` is not included;
   Android Studio will create it, or run `gradle wrapper --gradle-version 8.9` once.
2. Command line:
   ```
   ./gradlew clean
   ./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
   ./gradlew testDebugUnitTest
   ./gradlew connectedDebugAndroidTest   # needs an emulator/device (API 29+; try 29, 33 and 34)
   ```
3. Release builds need your own signing config; none is included.

## Architecture

MVVM + Clean Architecture, Hilt, Room, DataStore, Coroutines/Flow, Compose Material 3, Navigation Compose, Coil.

```
core/        permissions (StoragePermissionManager, SafPermissionManager), security (SafeDeleteManager), util
data/        hashing (FileHashCalculator, DHash, PerceptualHashCalculator, DuplicateGroupingEngine),
             media (MediaStoreScanner), documents (DocumentScanner), local (Room), repository (impls)
domain/      models, repository interfaces, SelectionPolicy
presentation home, scanning (ViewModel), results, duplicates (details), settings, theme
di/          Hilt modules
```

## How detection works

* **Exact duplicates (photos, videos, documents):** group by (category, size) -> streamed SHA-256 only for files
  that share a size -> group by identical (size, hash). Names, dimensions or durations are never evidence.
  Zero-byte/unreadable files are skipped; files that vanish mid-scan are skipped without crashing.
* **Same file through two providers:** every file carries a `physicalKey` (`volume:path`). MediaStore and SAF
  entries for one physical file collapse to one, so a file can never be reported as a duplicate of itself.
* **Similar photos (never auto-deleted):** 64-bit dHash (9x8 greyscale, EXIF-rotated, down-sampled decode),
  Hamming distance <= threshold (default 10, user-adjustable 0-10). Candidate pairs come from multi-index
  bucketing (hash split into threshold+1 bands, so every pair within the threshold shares a band) with bounded
  bucket sizes (200) and a sliding window fallback. Aspect ratio must agree within 15 %. Groups whose photos
  were taken within 10 s are labelled "burst". Exact copies are collapsed to one representative first.
* **Hash cache:** Room stores URI, name, MIME, size, modified time, SHA-256, dHash and group membership
  (metadata only; file contents are never copied). Unchanged files reuse cached hashes on the next scan.

## Deletion safety

1. User selects files per group; `SelectionPolicy` rejects any selection that would remove the last copy of
   **any** group (checked across groups, because one photo can be in an exact and a similar group).
2. Confirmation dialog shows the exact count and size. The selection is re-validated when confirmed.
3. Android 11+: `MediaStore.createDeleteRequest()` (one system dialog for the batch). Android 10:
   per-file delete with `RecoverableSecurityException` consent. SAF documents: `DocumentsContract.deleteDocument`
   (fallback `DocumentFile.delete`), authorised by the folder grant you gave.
4. Success is only reported after the provider no longer lists the file. Declined, failed, partially failed and
   already-gone files are reported separately. Results and recoverable space are then refreshed.

## Permissions

`READ_EXTERNAL_STORAGE` (<= API 32), `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_VISUAL_USER_SELECTED`
(API 34+, partial access). No `MANAGE_EXTERNAL_STORAGE`, no `WRITE_EXTERNAL_STORAGE`, no legacy storage flag.
Permission state is recomputed on every resume; permanent denial routes to app settings; on Android 14 partial
access is scanned and a "Choose more photos" action re-opens the system picker. Documents are only scanned in
folders you pick (SAF tree grants are persisted, and re-validated before each scan).

## Decisions and trade-offs

* SAF listing uses `DocumentsContract` child queries (one query per folder) rather than `DocumentFile.listFiles()`
  (one query per child, very slow on large trees). `DocumentFile` is used to validate the granted root and as a
  delete fallback.
* Scans run in `viewModelScope` (survive rotation, no foreground service per the spec). If Android kills the
  process in the background the scan stops; re-run it, cached hashes make the second pass fast.
* Similar-photo recall is intentionally bounded on very large, highly repetitive galleries (bucket cap).
* The app label icon is a system placeholder; add a launcher icon before publishing.
* Video "similarity" is not implemented (the spec asks for exact video duplicates only).

## Known gaps / manual test checklist

* **No automated SAF-tree test**: obtaining a tree grant needs UI interaction. Test manually: add a folder
  containing copies of a PDF, run a scan, open the group, remove one copy, confirm the other remains.
* No automated test of the Android 11+ `createDeleteRequest` dialog (system UI). Test manually on API 30+ and
  API 29 (consent path) with files created by other apps.
* Test permission flows on API 29, 33 and 34 (deny, deny twice, partial access, revoke while scanning).
* Test a gallery with 10k+ photos for performance and memory before shipping.
