# Galaxy Z Fold8 — Duo Home Launcher

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
