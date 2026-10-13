# ILS — bin inventory for the Zebra TC52

Make a bin, add the items inside it (each with a photo), print the bin's barcode label,
then scan the label from the **Search** tab to see everything in that bin.

## What it does

- **Bins tab:** create, rename and delete bins. Each bin gets a permanent code (`ILS00001`, `ILS00002`, …) and a Code 128 barcode.
- **Inside a bin:** add items by name, then take a photo with the TC52 camera. Tap an item to see its photo full screen. Press and hold an item to rename it, retake the photo, move it to another bin, or delete it.
- **Share / print label:** saves a PNG label (bin name + barcode + code) and opens the share sheet, so you can print it, email it, or save it to Drive.
- **Search tab:** press the scan trigger and aim at a label. The app shows the bin and its items with photos. You can also type a code (`ILS00001`, or just `1`) or an item name. Typing an item name shows every bin that contains it.
- Scanning while a bin is open jumps to the scanned bin.

All data stays on the device (SQLite database and photos in the app's private storage).
No internet access and no third-party libraries are used.

## Scanner setup (automatic)

On first launch the app creates a DataWedge profile named **ILS** for itself. The profile
turns on intent output (`com.ils.app.SCAN`, broadcast) and turns off keystroke output.
If scanning ever stops working, open the ⋮ menu and choose **Set up scanner again**,
or check DataWedge → Profiles → ILS on the device.

## Building the APK

The app is a standard Android Studio project (Java, minSdk 26, targetSdk 34).

**Option A — Android Studio:** open the `ILS` folder, wait for Gradle sync, then choose
*Build → Build App Bundle(s) / APK(s) → Build APK(s)*. Use `app/build/outputs/apk/release/app-release.apk`
(or the debug APK; both are signed with the same key).

**Option B — GitHub Actions, no Android Studio needed:** push this folder to a GitHub
repo. The included workflow builds the APK automatically. Open the repo's *Actions* tab, choose the
latest run, and download the `ILS-apk` artifact.

**Option C — command line** (with the Android SDK installed): `./gradlew assembleRelease`

### Signing

`app/ils.jks` is the app's signing key. Every build uses it, so a new version installs over the
old one without wiping your bins. It is listed in `.gitignore` and never goes to GitHub. The build
workflow recreates it from the `ILS_KEYSTORE_B64` secret. Back up this file: a different key would
require uninstalling the app, which deletes its data.

### Download link

Each push to `main` publishes a new release. The latest APK is always at
`https://github.com/<your-username>/ILS/releases/latest/download/ILS.apk`

## Installing on the TC52

- **StageNow:** host `ILS.apk` where the device can reach it (file server, FTP, or copy it to the
  device), create a profile with a *File Manager* step that downloads it and an *App Manager* step
  that installs it, then scan the StageNow barcode with the TC52.
- **USB:** `adb install -r ILS.apk`

Photos use the built-in Camera app, so the app doesn't need camera permission.
