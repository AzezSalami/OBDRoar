# 🏁 OBD Roar

An Android app that connects to your car through a **Bluetooth ELM327 OBD2 adapter**, shows its live data and
trouble codes in a clean dashboard, and also has a **live engine sound synthesizer** (similar to Wrumer) that you
can rev yourself or that follows your car's real RPM.

## Features

**Car data**
- Dashboard with animated RPM and speed gauges, plus tiles for coolant, load, throttle, intake air, MAF, manifold
  pressure, battery voltage, fuel level, fuel trims, oil temperature, timing and fuel rate
- **Live data**: every Mode 01 sensor the car supports (about 55 PIDs), grouped by category, with min/max values
  and a live graph for each one
- A **(!) icon next to every sensor** (gauges, dashboard tiles and live data rows): tap it to see what the sensor
  measures, its unit, normal values, a practical tip and the current reading
- **Diagnostics**: check-engine light status, stored, pending and permanent trouble codes, each with a
  description, severity, common causes and an online lookup. You can clear codes (it asks you to confirm first).
- Emission readiness monitors, with petrol and diesel labels
- Vehicle info: VIN, make and model year read from the VIN, adapter chip, protocol, battery voltage, and the list
  of supported sensors
- **History**: while connected, the app saves one reading per sensor per second (plus battery voltage) and
  every trouble code it finds. The data stays on the phone. Each drive is listed with its duration, top RPM, top
  speed and the number of codes found. Open a drive to see a graph for any sensor; drag your finger along it to
  read the value at any moment. You can export a drive as a CSV spreadsheet or delete drives. Saving can be
  turned off in the History tab.
- **Demo car mode**, so you can try every screen without an adapter

**Engine sound (just for fun)**
- 10 engines: V8 Muscle, V8 Flat-plane, Inline-4 Hot Hatch, Inline-6, V6, Boxer-4, V12, V-Twin, Single dirt bike,
  Diesel truck
- Made live from each engine's real firing order (no recordings). The cross-plane V8 burble, the boxer rumble and
  the V-twin "potato-potato" all come out of the physics.
- Starter crank, rev limiter, overrun pops and bangs, and an optional turbo whistle with a blow-off valve
- A touch gas pedal, a hold-throttle slider, or **Follow my car's RPM** mode while you're connected

## Getting the APK (GitHub Actions)

1. Create a new **empty** repository on GitHub.
2. Push this project to it:
   ```bash
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. Open the **Actions** tab and wait for the build to finish (about 4–6 minutes).
4. Open **Releases**, then download `OBDRoar-vX.Y.Z.apk` on your phone and install it.

Every push to `main` builds a new version automatically:

| | Value |
|---|---|
| `versionCode` | GitHub run number (always goes up) |
| `versionName` | `MAJOR.MINOR.<run number>`. Change MAJOR and MINOR in `version.properties`. |
| Release tag | `v1.0.<run number>` with the APK attached |

Pull requests are built too (you'll find the APK under the run's artifacts), but they don't create a release.

### Signing, so updates install over the old version
With no extra setup, CI signs with a key that it caches, so updates install fine. GitHub deletes a cache after
7 days without use, though, so for a permanent key, create your own once:

```bash
keytool -genkeypair -v -keystore obdroar.jks -alias obdroar -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 obdroar.jks   # copy the output
```

Then go to the repo's **Settings → Secrets and variables → Actions** and add these secrets:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`obdroar`), `KEY_PASSWORD`.
The first APK signed with the new key needs a single uninstall-and-reinstall.

## Building locally
Open the folder in Android Studio (Ladybug or newer) and press Run, or use the command line:
`./gradlew assembleDebug`. Requirements: JDK 17 and Android SDK 35.

## Using it in the car
1. Plug the ELM327 adapter into the OBD2 port (under the dashboard, near the steering column).
2. Switch the ignition **ON**, or start the engine.
3. Pair the adapter in Android Bluetooth settings. The PIN is usually `1234` or `0000`.
4. In the app, tap **Connect** and choose the adapter.

Supported: Bluetooth Classic (SPP) ELM327 v1.x and v2.x adapters and clones (OBDLink, Vgate, Veepeak,
Konnwei, and others) on any OBD2 car (petrol cars from 2001 in the EU and 1996 in the US, diesels from 2004 in the EU).
Bluetooth LE-only adapters are not supported.

> ⚠️ Don't use the app while driving. Clearing codes doesn't fix the problem behind them. The engine sound is
> for fun only.

## Project layout
```
app/src/main/java/com/obdroar/app/
├── MainActivity.kt          navigation, permissions, top and bottom bars
├── MainViewModel.kt         connection, polling loop, diagnostics, history recording, sound controls
├── data/
│   └── HistoryRepository.kt saved drives, readings and codes (built-in SQLite), CSV export
├── obd/
│   ├── Transport.kt         Bluetooth SPP socket (with fallbacks for clones)
│   ├── ObdSession.kt        ELM327 protocol: init, PIDs, DTCs (CAN and legacy), VIN, monitors
│   ├── Pids.kt              ~55 SAE J1979 sensor definitions and formulas
│   ├── DtcDatabase.kt       offline code descriptions, causes, VIN decoder
│   └── DemoTransport.kt     simulated car
├── sound/
│   ├── EngineProfiles.kt    firing orders, resonances, redlines
│   └── EngineSoundEngine.kt real-time synthesizer (AudioTrack, float PCM)
└── ui/                      Jetpack Compose screens and components
.github/workflows/android.yml  CI: build, auto-version, release
```
