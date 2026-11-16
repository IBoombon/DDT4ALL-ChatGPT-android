# DDT4ALL Android

Android application prototype for Renault diagnostics. The current development branch focuses on establishing a Bluetooth Classic SPP connection to a paired ELM327 adapter.

## Current scope

- Lists Bluetooth devices already paired in Android settings.
- Connects using the standard Bluetooth Serial Port Profile (SPP) UUID.
- Requests the Android 12+ `BLUETOOTH_CONNECT` runtime permission.
- Sends basic ELM327 initialization commands: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`.
- Provides a manual OBD-II connectivity test using `0100`.
- Displays adapter responses in an on-screen log.

## Not implemented yet

This prototype is **not yet a complete DDT4ALL port**. ECU identification, Renault-specific diagnostic sessions, fault-code operations, live parameters, ECU configuration, actuator tests, and PIN extraction are not implemented.

## Safety

Only connection and read-only protocol initialization/testing are enabled at this stage. Do not use this prototype as proof that a particular ECU supports generic OBD-II. Do not add write/configuration, fault clearing, or actuator commands until the vehicle-specific protocol and safeguards have been validated.

For the first vehicle test:
1. Park safely, apply the parking brake, and switch the ignition on as required by the vehicle.
2. Pair the ELM327 in Android Bluetooth settings first.
3. Open the app and choose the paired adapter.
4. Review the log and run `0100` only if the adapter is connected.
5. If connection fails, record the exact adapter name and log message; do not repeatedly cycle the ignition or issue unknown commands.

## Build

The GitHub Actions workflow builds a debug APK for the `main` and `feature/bluetooth-elm327` branches. Download the `DDT4ALL-debug-APK` artifact from the successful workflow run.

## Target environment

- Android: API 26+
- Compile SDK: 35
- Java: 17
- Kotlin: 2.0.21
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
