# CLAUDE.md

Guidance for working in this repository.

## What this is

"Machine To Be A Cursed Other" — an Android VR/AR perception experiment, a low-budget
"cursed" take inspired by (but deliberately diverging from) The Machine To Be Another.
It renders the phone's rear camera to the screen in stereo for a Cardboard headset,
always left/right-mirrored. Stereo is drawn by a small in-app renderer whose per-eye
geometry comes from a Cardboard viewer profile, so different headsets can be calibrated
(see the VR-rendering section below). Single-module Android app, written in Java,
package `io.github.metavee.machinetobeacursedother`.

## Build & run

The project uses the Gradle wrapper — no separate Gradle install needed.

```sh
./gradlew assembleDebug     # builds app/build/outputs/apk/debug/app-debug.apk
```

Requires an Android SDK (set `ANDROID_HOME`, accept licenses). If the SDK is missing
in the environment, install the packages CI uses:

```sh
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

There are no unit tests in the project. "Verifying a change builds" means a clean
`./gradlew assembleDebug`.

## Toolchain

- Android Gradle Plugin **8.5.2**, Gradle **8.9**, JDK **17**
- `compileSdk` / `targetSdk` **34**, `minSdk` **21**
- AndroidX (the app was migrated off the legacy `android.support.*` libraries)
- The QR calibration scanner pulls in **CameraX 1.3.4** (`camera-core`/`camera-camera2`/
  `camera-lifecycle`/`camera-view`) and **ML Kit `barcode-scanning:17.3.0`** (bundled
  on-device model). Keep CameraX on the **1.3.x** line — 1.4.x requires `compileSdk 35`, which
  this module is not on. Both resolve from the already-configured `google()`/`mavenCentral()`.

## VR rendering & viewer calibration (no VR SDK)

The app used to render stereo through the deprecated Google VR (GVR/Cardboard) SDK,
consumed from a vendored fat AAR. **That dependency has been removed** — there is no
VR SDK anymore, and no `app/libs` AAR. Do **not** re-introduce `com.google.vr:sdk-base`
(it resolves from no Maven repo) or the current NDK Cardboard SDK unless there's a
concrete need; stereo is handled in-app.

How it works now:

- `TextureTestActivity` hosts a plain `GLSurfaceView` and implements
  `GLSurfaceView.Renderer` itself. Each frame it draws the camera passthrough quad once
  per eye into the left/right half of the surface. There is intentionally **no head
  tracking** — the image is pinned in front of the viewer, matching the original app.
  The activity runs **edge-to-edge/immersive** (system bars hidden, drawn into the
  cutout) so the GL surface covers the whole physical screen; the per-eye geometry assumes
  the full screen, and it keeps the screen awake (`FLAG_KEEP_SCREEN_ON`). A thin white
  vertical **alignment line** is drawn down the screen center. There is no trigger
  (no screen tap, magnet, or NFC) — the view just renders the mirrored passthrough.
- The live camera is shown as a **life-size passthrough**: the full frame is drawn on a
  quad sized (from `Camera#getHorizontalViewAngle`/`getVerticalViewAngle`) so the camera's
  field of view maps 1:1 to the eye, so objects appear their real-world size instead of
  being center-cropped/magnified.
- Per-eye projection comes from a **Cardboard viewer profile** (`CardboardProfile`): the
  same lens/screen geometry the official Cardboard app uses, encoded in the QR code on a
  headset (`https://google.com/cardboard/cfg?p=<base64 DeviceParams protobuf>`).
  `CardboardProfile` parses that protobuf with a tiny hand-rolled wire reader (no
  protobuf runtime dependency), persists the raw bytes in `SharedPreferences`, and
  computes an asymmetric frustum per eye so each eye's image is centered under its lens
  and scaled to the headset. A built-in Cardboard v2 default is used until one is saved.
- Calibration input is **camera-first**: the "Calibrate viewer" button in `MainActivity`
  launches `QrScanActivity`, which shows a live camera preview and decodes the headset's QR
  with ML Kit (on-device bundled model, QR only) over a CameraX `ImageAnalysis` stream. The
  scanner returns only the raw scanned **string**; `MainActivity` feeds it into the same
  `resolveAndSaveProfile` path as before. Manual URL **paste** is kept as a fallback
  (`MainActivity#showManualUrlDialog`), reached via the scanner's "Enter URL manually" button
  or automatically when the camera permission is denied. A **QR short link** (e.g. `goo.gl/…`)
  works whether scanned or pasted — `MainActivity#resolveDeviceParams` follows the HTTP
  redirects on a background thread until it reaches the `cfg?p=` URL (stopping before the
  "get Cardboard" landing page).
- Lens **barrel distortion** is applied by `DistortionRenderer`: each eye is rendered to
  an off-screen FBO, then drawn to the screen through a pre-distorted mesh built from the
  profile's `distortion_coefficients` (Cardboard's `r*(1+k1*r²+k2*r⁴)` model, inverted
  per mesh vertex). With zero coefficients it degrades to an identity blit. The distortion
  math has been verified to build but should be **eyeballed on a real headset** and tuned
  if needed — it wasn't validated on-device.

## CI / releases

`.github/workflows/android.yml` builds on pushes to **any branch** and manual
dispatch (there is no separate `pull_request` trigger — a PR's branch already
builds from its push, so adding one would just double the runs). There are two
paths:

- **Any non-default branch push and manual runs** build a **debug** APK and
  publish it to a rolling **`debug-latest`** GitHub *pre-release* (direct-download
  `.apk`, no zip) and upload it as a zipped workflow artifact. (`debug-latest` is
  a single rolling pre-release, so concurrent pushes on different branches
  overwrite each other's debug APK there.)
- **Merges to `main`/`master`** build a **release** APK and publish it as a full
  (non-pre) GitHub **Release** with a stable, versioned tag
  (`v<versionName>-build.<run#>`), marked as the repo's "Latest release".

Publishing needs `contents: write` permission and uses the `gh` CLI.

The release build is signed. Add repository secrets `RELEASE_STORE_FILE`,
`RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` to sign
with a real upload/release key; without them the release build falls back to the
debug signing key (see `app/build.gradle`) so the APK still installs for sideloading.

## Permissions model (modern Android)

Two permissions are declared: `CAMERA` (requested at runtime in `MainActivity`) and
`INTERNET`. INTERNET is used **only** to follow a calibration short link's redirects to
the underlying profile URL (`MainActivity#resolveDeviceParams`). No storage permission is
needed — the app writes nothing to external storage. (The `READ/WRITE_EXTERNAL_STORAGE`
strips that used to counter the VR AAR's manifest contributions are gone with the AAR.)
Every activity sets `android:exported` explicitly. Keep this minimal set — don't
reintroduce storage or phone-state permissions.

## Key files

- `app/src/main/java/.../MainActivity.java` — launcher menu + camera permission + "Calibrate viewer" (launches the QR scanner; manual URL paste as fallback)
- `app/src/main/java/.../QrScanActivity.java` — CameraX + ML Kit QR scanner; returns the scanned string for calibration
- `app/src/main/java/.../TextureTestActivity.java` — the custom GLSurfaceView stereo renderer (live mirrored passthrough)
- `app/src/main/java/.../CardboardProfile.java` — Cardboard viewer profile: QR/protobuf parse, persistence, per-eye frustum
- `app/src/main/java/.../WorldLayoutData.java` — quad geometry + L/R-flip texture coords
- `app/src/main/res/raw/rect_*.glsl` — pass-through OES-texture shaders

## Modernization backlog (not yet done)

The app builds and runs but still leans on deprecated APIs. Durable follow-ups:

- Replace the deprecated `android.hardware.Camera` API (still used by `TextureTestActivity`)
  with Camera2/CameraX. (The calibration `QrScanActivity` already uses CameraX; the stereo
  passthrough renderer does not.)
- **Verify/tune the lens distortion on a real headset.** `DistortionRenderer` builds and
  is correct in principle, but its output has not been eyeballed on-device; confirm lines
  look straight through the lenses and adjust if the model/coefficients need refining.
