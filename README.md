# Galaxy Z Fold8 — iPhone Duo-style hinge transition POC

This project is a native Android proof of concept that ties the Fold8 hinge angle to an Apple-like fold/unfold transition.

1. Read `Sensor.TYPE_HINGE_ANGLE`.
2. Normalize the physical hinge angle to 0…1.
3. Feed that progress into Android **AGSL / RuntimeShader**.
4. Warp, blur, shade, and crossfade the cover/inner visual around the hinge.
5. Attach a `Presentation` to any secondary display Android exposes to the app.

## Important limitation

This is **not a SystemUI / One UI patch** and it does not replace Samsung's system fold animation. A normal third-party app cannot override that transition globally. This repository is a visual POC app for testing the hinge-driven effect on real hardware.

## First run

No private screenshots are needed. `DuoTransitionView.kt` generates test cover/inner home-screen mockups in code so the project can build and run immediately.

After the hinge behavior is verified on-device, you can replace `createHomeMock()` with a screenshot-loading path if you want the demo to resemble your actual Fold8 home screens.

## Device calibration

The top-left overlay shows the raw hinge angle. Tune the endpoints in `HingeAngleSource.kt` if needed:

```kotlin
var closedAngle = 0f
var openAngle = 180f
```

For example, if a device reports roughly 2° closed and 178° fully open, use those values to make the transition hit its endpoints more cleanly.

## Build

Recommended:

- Android Studio / JDK 17
- Galaxy Z Fold8
- USB debugging enabled for direct install/testing

Package / application ID:

`com.qet1234.fold8duotransition`

## GitHub Actions

`.github/workflows/android.yml` builds a debug APK from `main` and uploads it as the `Fold8DuoTransition-debug` artifact.
