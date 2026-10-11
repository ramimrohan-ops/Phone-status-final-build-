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
| Battery level, charging, current (widget) | BatteryManager | No |
| Battery temperature | BatteryManager | No |
| RAM used / total | ActivityManager | No |
| Storage used / total | StatFs on /data | No |
| CPU / GPU temperature | /sys/class/thermal via `su` | **Yes** |
| Battery capacity estimate, design capacity, health | Fuel-gauge charge counter ÷ level (smoothed, saved between runs); design capacity only if the phone reports it | No |
| CPU clock (peak core) | /sys/devices/system/cpu | **Yes** |

Grant root to Phone Status in your root manager the first time. The app waits up to 15 s for you to tap Grant.

## Widget (3x2, live, every 3 seconds)

- One size: a 3x2 widget (resizable a little). Left: battery ring, a solid current-draw circle (green, amber or red by the drain), RAM ring and CPU clock ring (a complete solid mint ring, amber at the top clock of 2.4 GHz). Right: an APPS bar with a Refresh pill, the 4 apps using the most memory, a storage bar, and CPU and GPU temperatures.
- Only the APPS bar is tappable: it reads the memory report once (through Shizuku) and redraws the widget.
- A background service redraws the widget every 3 seconds (temperatures about every 10 seconds) **only while Home Dragon says "Run"**: the home screen is on top, on page 2 (where the widget sits), the phone is unlocked, the screen is on, no keyboard and no notification shade. Any other page, another app or no message pauses it. Android's own 30-minute update, adding or resizing the widget, and the Refresh tap also redraw it once.
- Home Dragon decides and sends one of two messages as a broadcast (`com.ramim.homedragon.HOME_STATE`, with `run` true or false, a short `why`, `seq` and `t`); Phone Status does not look at page numbers. It answers with `dev.ramim.phonestatus.HOME_ACK` (`seq`, `run`), and Home Dragon can ask for the state again with `dev.ramim.phonestatus.HOME_ASK`. "Run" starts the live service if it is not running; "Don't run" never starts anything.
- The service shows a small silent notification (with an "Open" button) **only while it runs**. When you leave page 2, lock the phone or turn the screen off, the notification is removed at once and comes back with the next "Run". If Android refuses to start the service in the background, the widget stays as it was until the next "Run" (the Debug tab shows the service line).
- Top apps need Shizuku (start it with wireless debugging, allow Phone Status once). Without it the widget shows a short note instead of the list.
- Root, when granted, is used to read CPU/GPU temperatures and clocks.
- Add the widget with the button in the app, or long-press the home screen, then Widgets, then Phone Status. Open the app once after installing.

## Limits

- Per-app RAM is blocked for normal apps on Android 10+. The Top apps list works only through Shizuku. Per-core CPU usage is not included.
- Total storage is the data partition size, so it is lower than the advertised capacity.

## v3.4 - Run / Don't run, notification only on page 2, plain screens
- **Home Dragon decides.** Phone Status reads a single `run` yes/no from Home Dragon 4.9 and no longer reads page numbers. It needs Home Dragon 4.9; with an older Home Dragon it stays paused.
- **Notification only on page 2.** The foreground notification is shown only while the widget runs and removed at once on "Don't run" (the wait for the next update is cut short). Android requires a notification for every start, so a start that is not "Run" shows it for a moment and removes it again; Android usually does not show such a short notification at all. An "Open" button on it lets Android show it without its usual delay of up to 10 s.
- **Plain screens.** The app is a flat dark list of numbers with no rings, bars, gradients or animations: battery level, temperatures, CPU clock (amber at the top clock), RAM, storage, the top apps with a Refresh link, and the display size. Set-up links (Background running, Home Dragon, root) appear only when something is missing. The Debug tab is six lines (State, Why, Link, Service, Last update, Notification), a Copy button and the last five changes of state.
- **Less data read.** Voltage, charger type, battery health text, technology, cycle count, swap and the hottest sensor are no longer read, because nothing shows them. The widget is unchanged.

## v3.5 - asks Home Dragon on every start and on wake-up
- **Ask on every new process.** A small `Application` class asks Home Dragon once for its state every time the app's process starts, whatever started it: you opening the app, Android restarting it after you closed it, killed it or it crashed, the widget, a boot, or a message from Home Dragon. (Android never starts a force-stopped app by itself: it stays stopped until you open it.)
- **Ask on screen on.** While the live service is running, the screen turning on sends one more question. A frozen app that wakes in the same process is covered by this.
- **Works with Home Dragon 4.11:** Home Dragon holds a change 0.4 s before sending, answers a question at once, and sends a missing confirmation once more after 1 s.

