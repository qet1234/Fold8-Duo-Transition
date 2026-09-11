# Galaxy Z Fold8 — iPhone Duo-style hinge transition POC

This project reproduces a Fold8 hinge-driven transition proof of concept:

1. Read `Sensor.TYPE_HINGE_ANGLE`.
2. Normalize the physical hinge angle to 0…1.
3. Feed that progress into an Android **AGSL / RuntimeShader**.
4. Blend/warp/blur an outer-home screenshot and inner-home screenshot near the hinge.
5. Mirror the transition to Android displays exposed to the app via **Presentation**.

## Important limitation

This is **not a SystemUI / One UI patch** and is not a true launcher transition. A normal third-party app cannot replace Samsung's system fold/unfold animation. It is a visual POC using screenshots.

## Replace the placeholder screenshots

Replace these files with screenshots from your own Fold8:

- `app/src/main/res/drawable/outer_home.png`
- `app/src/main/res/drawable/inner_home.png`

Keep the filenames unchanged.

## Build

Open the folder in Android Studio and run on a Galaxy Z Fold8.

Recommended:

- Android Studio with JDK 17
- Galaxy Z Fold8 with USB debugging enabled
- Test while physically folding/unfolding the phone

The top-left overlay shows the raw hinge angle so you can tune calibration in `HingeAngleSource.kt`.

```kotlin
var closedAngle = 0f
var openAngle = 180f
```

## GitHub Actions

`.github/workflows/android.yml` builds a debug APK on pushes to `main` and publishes it as the `Fold8DuoTransition-debug` artifact.
