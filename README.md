# Pokémon Watch Face for Galaxy Watch 5 Pro

A Wear OS watch face where you pick a Gen 1 starter Pokémon and watch it evolve as you hit your daily step goals.

**Evolution stages (daily steps)**

| Steps | Stage |
|-------|-------|
| 0 – 4,999 | Base form (Charmander, Bulbasaur, Squirtle, Pichu) |
| 5,000 – 9,999 | First evolution |
| 10,000 – 14,999 | Final evolution |
| 15,000 – 19,999 | Mega evolution |
| 20,000+ | Gigantamax (animated glow) |

---

## Installation

You will need a **Windows, Mac, or Linux PC** and your **Galaxy Watch 5 Pro** on the same Wi-Fi network as your PC.

### Step 1 — Download the APK

Download **app-release.apk** from the [Releases](../../releases/latest) page on this repo.

### Step 2 — Install ADB on your PC

ADB (Android Debug Bridge) is a small tool that lets your PC talk to the watch.

**Windows**
1. Download the [Android SDK Platform Tools](https://dl.google.com/android/repository/platform-tools-latest-windows.zip)
2. Extract the zip anywhere (e.g. `C:\platform-tools`)
3. Open **Command Prompt** and run:
   ```
   cd C:\platform-tools
   ```

**Mac**
```bash
brew install android-platform-tools
```

**Linux**
```bash
sudo apt install adb
```

### Step 3 — Enable developer mode on your watch

1. On your watch go to **Settings → About watch → Software**
2. Tap **Software version** 5 times quickly until you see *"Developer mode enabled"*
3. Go back to **Settings → Developer options**
4. Turn on **ADB debugging**
5. Turn on **Wi-Fi debugging**
6. Tap **Wi-Fi debugging** — note the **IP address and port** shown (e.g. `192.168.1.50:5555`)

### Step 4 — Connect your PC to the watch

In your terminal / Command Prompt, run (replace with your watch's IP and port):

```
adb connect 192.168.1.50:5555
```

A prompt will appear on your watch — tap **Allow**.

Run this to confirm it worked:
```
adb devices
```

You should see your watch listed as `connected`.

### Step 5 — Install the watch face

Run this from the folder where you downloaded the APK:

```
adb install app-release.apk
```

You will see `Success` when it's done.

### Step 6 — Set the watch face

1. On your watch, **long-press** your current watch face
2. Swipe left through the options until you see **Pokémon Watch Face**
3. Tap it to set it

### Step 7 — Pick your starter

1. Swipe up on the watch to open the app drawer
2. Open **Choose Your Starter**
3. Scroll through and tap your Pokémon

The watch face will update immediately. Walk your way to Gigantamax!

---

## Permissions

The app will ask for two permissions the first time you open **Choose Your Starter**:

- **Physical Activity** — needed to count your steps
- **Body Sensors** — needed to read your heart rate

Both are required for the watch face to work fully. If you accidentally denied them, go to **Settings → Apps → Choose Your Starter → Permissions** on the watch and grant them.

---

## Troubleshooting

**`adb connect` times out**
Make sure your PC and watch are on the same Wi-Fi network. Check the IP address shown on the watch under Wi-Fi debugging — it can change.

**`adb devices` shows `unauthorized`**
Check your watch screen — there should be an *Allow ADB debugging* prompt. Tap Allow.

**Steps show 0**
Make sure Physical Activity permission is granted (see Permissions above). Walk around for a minute — the sensor needs a few seconds to start reporting.

**Heart rate shows `--`**
Make sure Body Sensors permission is granted. The heart rate sensor reads periodically rather than continuously — it may take up to a minute to update.

**Pokémon sprite doesn't load**
The sprites are downloaded from the internet. Make sure your watch has Wi-Fi connected.

**Watch face reverts after reboot**
This is a Wear OS quirk. Just long-press the watch face and re-select Pokémon Watch Face.
