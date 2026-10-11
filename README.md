# Phone Status

Android app + home-screen widget showing battery, RAM, storage, refresh rate and temperatures.
Kotlin, Jetpack Compose, and a RemoteViews home-screen widget. Min Android 8.0 (API 26).

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
| CPU clock per core | /sys/devices/system/cpu | **Yes** |

Grant root to Phone Status in your root manager the first time. The app waits up to 15 s for you to tap Grant.

## Widget (3x2, live, every 3 seconds)

- One size: a 3x2 widget (resizable a little). Left: battery ring, a solid current-draw circle (green, amber or red by the drain), RAM ring and CPU clock ring. Right: a Top apps bar with a Refresh pill, the 4 apps using the most memory, a storage bar, and CPU and GPU temperatures.
- Only the Top apps bar is tappable: it reads the memory report once (through Shizuku) and redraws the widget.
- A background service redraws the widget every 3 seconds (temperatures about every 10 seconds) **only while Home Dragon says the home screen is on top and the screen is on**. Android's own 30-minute update, adding or resizing the widget, and the Refresh tap also redraw it once.
- Home Dragon tells Phone Status when the home screen is on top with a broadcast (`com.ramim.homedragon.HOME_STATE`); Phone Status answers with `dev.ramim.phonestatus.HOME_ACK`, and Home Dragon can ask for the state again with `dev.ramim.phonestatus.HOME_ASK`.
- The service shows a small silent notification. Android requires it for any always-running service.
- Top apps need Shizuku (start it with wireless debugging, allow Phone Status once). Without it the widget shows a short note instead of the list.
- Root, when granted, is used to read CPU/GPU temperatures and clocks.
- Add the widget with the button in the app, or long-press the home screen, then Widgets, then Phone Status. Open the app once after installing.

## Limits

- Per-app RAM is blocked for normal apps on Android 10+. The Top apps list works only through Shizuku. Per-core CPU usage is not included.
- Total storage is the data partition size, so it is lower than the advertised capacity.
