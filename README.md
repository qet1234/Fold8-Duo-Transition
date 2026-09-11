# Galaxy Z Fold8 — Duo Home Launcher

## v0.3.1 — Fold8 Ultra hinge diagnostics

Install the **Fold8Ultra-HingeCheck** artifact for the sensor test. Its application ID is
`com.qet1234.fold8duotransition.diagnostics`, independent of the existing launcher.
It has no HOME intent filter and does not ask to become the default home app.

1. Install `app-diagnostic.apk` and open **힌지 센서 진단**.
2. Slowly fold/unfold the device, pause at several positions, and reverse direction.
3. Check whether the measured number changes. **—°** means no measurement yet, not zero degrees.
4. Tap **결과 복사** and paste into the chat, or choose a destination with **결과 공유**.

The target is the user's reported Fold8 Ultra. No model codes, angle endpoints, or
compatibility are presumed verified. The report contains the actual Build.MODEL,
Android version, window dimensions, standard sensor metadata, observed range and
the latest 200 raw samples. It excludes identifiers such as serial numbers and
installed app lists. No network permission or automatic report upload is used.

Measurements are unfiltered. Invalid/out-of-order samples are excluded and counted.
History is bounded to 1,000 samples; background pauses create separate graph segments.
Stationary on-change sensors may stop sending events normally. A missing standard
sensor is distinguished from registration failure and waiting for measurements.
If Android recreates the process, the last report can be copied on the next launch.
This test does not validate physical accuracy or prove continuous angle support.

Build: `gradle :app:testDebugUnitTest :app:lintDiagnostic :app:assembleDiagnostic`.
The existing launcher debug APK continues to be built separately.

`Duo Home` is a native Android launcher experiment for the Galaxy Z Fold8. It turns the previous hinge-animation POC into a usable HOME app while keeping the fold/unfold transition tied to the physical hinge angle.

## v0.3.0

- Registers as an Android HOME launcher (`ACTION_MAIN` + `CATEGORY_HOME`).
- Requests the official Android `ROLE_HOME` so the user can choose Duo Home as the default home app.
- Queries real installed launchable apps and renders their real icons and labels.
- Tapping an icon launches the actual installed app.
- Uses a 4-column cover layout and 6-column inner layout.
- Interpolates the same app index between cover and inner coordinates from hinge progress so icons morph instead of snapping.
- Adds speed-reactive blur during the middle of the fold.
- Keeps AGSL for the animated wallpaper/background only; fake icons and fake widgets were removed.
- Falls back to a stable Canvas renderer if AGSL fails on the device GPU.
- Supports vertical scrolling when many apps are installed.

## Install / use

1. Install the debug APK on the Galaxy Z Fold8.
2. Open **Duo Home**.
3. Tap **기본 홈 앱으로 설정**.
4. Choose **Duo Home** as the default Home app.
5. Press the Android Home gesture/button to return to Duo Home.
6. Fold and unfold the phone while on the home screen to test the transition.

## Important limitation

This is a real launcher, but it is still **not a Samsung SystemUI / One UI patch**. The transition is controlled while Duo Home is the active home screen. A normal third-party launcher cannot globally replace Samsung's fold transition while another app is on screen.

The Good Lock / private-hook route is intentionally not part of the stable build yet because it can break after One UI updates and can cause launcher instability.

## Hinge calibration

`HingeAngleSource.kt` maps the reported hinge angle to 0…1:

```kotlin
var closedAngle = 0f
var openAngle = 180f
```

If the real Fold8 reports slightly different endpoints, tune these two values after checking the device.

## Build

Recommended:

- Android Studio / JDK 17
- Galaxy Z Fold8
- USB debugging enabled

Package / application ID:

`com.qet1234.fold8duotransition`

Version:

`0.3.0 (versionCode 4)`

## GitHub Actions

`.github/workflows/android.yml` builds a debug APK from `main` and uploads it as the `Fold8DuoTransition-debug` artifact.
