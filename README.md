# Phone Status

Android app + home-screen widget showing battery, RAM, storage, refresh rate and temperatures.
Kotlin, Jetpack Compose, Glance (widget). Min Android 8.0 (API 26).

## Get the APK from GitHub (no Android Studio needed)

1. Create a new GitHub repo and upload everything in this folder (keep the `.github` folder).
2. Open the **Actions** tab. The `Build APK` workflow runs on every push (or press **Run workflow**).
3. When it turns green, open the run and download the **PhoneStatus-debug-apk** artifact.
4. Unzip it and install `app-debug.apk` on your phone.

If the build fails, copy the red error lines from the Actions log and send them back.

## What it shows

| Item | Source | Needs root |
|---|---|---|
| Battery level, charging, voltage, current, health | BatteryManager | No |
| Battery temperature | BatteryManager | No |
| RAM used / total, swap | ActivityManager, /proc/meminfo | No |
| Storage used / total | StatFs on /data | No |
| Refresh rate (live measured + system mode + supported rates) | Choreographer frames, Display | No |
| CPU / GPU / hottest sensor temperature | /sys/class/thermal via `su` | **Yes** |
| Battery capacity estimate, design capacity, health, cycles | Fuel-gauge charge counter ÷ level (smoothed, saved between runs); design capacity, health and cycles only if the phone reports them | No |
| CPU clock per core, GPU clock | /sys/devices/system/cpu, /sys/class/kgsl (GPU path varies by chip) | **Yes** |

Grant root to Phone Status in your root manager the first time. The app waits up to 15 s for you to tap Grant.

## Widget (live, 1 second)

- Sizes: small (2x2), wide (4x2, adds CPU and GPU clock) and tall (4x3+, adds CPU/GPU temperature and clock, current, voltage, battery capacity). Resizable.
- A background service pushes the widget every second **while the screen is on**. It pauses when the screen is off and stops by itself if you remove the widget.
- The service shows a small silent notification. Android requires it for any always-running service.
- Root is used to read CPU/GPU temperatures through one persistent shell, and once at start to allow the app to run in the background (battery allowlist, background appops, notification permission).
- Tap the widget to open the app. Open the app once after installing so the service starts.
- Add it with the button in the app, or long-press the home screen, then Widgets, then Phone Status.

## Limits

- Per-app RAM and per-core CPU usage are blocked for normal apps on Android 10+, so they are not included.
- Total storage is the data partition size, so it is lower than the advertised capacity.
