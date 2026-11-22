# DDT4ALL Android

Android application prototype for Renault diagnostics. The current development branch focuses on establishing a Bluetooth Classic SPP connection to a paired ELM327 adapter.

## Current scope

- Lists Bluetooth devices already paired in Android settings.
- Connects using the standard Bluetooth Serial Port Profile (SPP) UUID.
- Requests the Android 12+ `BLUETOOTH_CONNECT` runtime permission.
- Sends basic ELM327 initialization commands: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`.
- Provides a manual OBD-II connectivity test using `0100`.
- Includes read-only requests for adapter protocol identification (`ATDP`), engine RPM (`010C`), vehicle speed (`010D`), coolant temperature (`0105`), intake-air temperature (`010F`), throttle position (`0111`), MAF (`0110`), control-module voltage (`0142`), and standard stored diagnostic trouble codes (`03`).
- Decodes standard OBD-II DTC identifiers into P/C/B/U code families.
- Displays XML catalog entries for ECU identifications, states, parameters, commands and fault interpretations.
- Displays ECU communication metadata from `Uces.xml` (protocol label, baud rate, init addresses, keywords, and identification request/response) as reference data only.
- Serializes initialization and diagnostic requests to prevent overlapping ELM327 transactions.
- Shows decoded RPM and speed when the ECU returns standard OBD-II response frames; otherwise preserves the raw response in the log.
- Displays adapter responses in an on-screen log.
- Imports a compact offline Laguna II TCOM 116 data pack from ZIP storage.
- Reads the imported vehicle profile and browses ECU session XML; optimizer `.p` dictionaries are consumed as converted JSON, never deserialized in the Android app.

## Not implemented yet

This prototype is **not yet a complete DDT4ALL port**. The database browser displays ECU session catalog entries and communication metadata, but does not yet execute Renault-specific ECU diagnostic services or read live Renault ECU parameters. Renault fault-code interpretation, ECU configuration, actuator tests, UCH/card programming and PIN extraction are not implemented. The added live parameters are generic OBD-II PIDs and depend on support by the connected ECU. Standard OBD-II read-only requests are experimental and depend on ECU support. Negative KWP/OBD responses are displayed with their response code; a positive Mode 03 response containing only zero bytes is reported as no non-zero standard DTCs in that response, not as proof that all vehicle modules are fault-free.

## Safety

Only connection and read-only protocol initialization/testing are enabled at this stage. Do not use this prototype as proof that a particular ECU supports generic OBD-II. Do not add write/configuration, fault clearing, or actuator commands until the vehicle-specific protocol and safeguards have been validated.

For the first vehicle test:
1. Park safely, apply the parking brake, and switch the ignition on as required by the vehicle.
2. Pair the ELM327 in Android Bluetooth settings first.
3. Open the app and choose the paired adapter.
4. Review the log and run `0100` only if the adapter is connected.
5. If connection fails, record the exact adapter name and log message; do not repeatedly cycle the ignition or issue unknown commands.

## Laguna II offline data pack

The repository includes `tools/build_laguna_pack.py`. Run it on a computer with Python 3 using the original PyRen/DDT archive:

```bash
python tools/build_laguna_pack.py pyrendata_232_and_Lada_and_OldRenault.zip --output laguna2-tcom116-data.zip
```

The generated ZIP contains the TCOM 116 profile, matching `Uces.xml` definitions, referenced session XML files, and optimizer dictionaries converted to JSON. The converter accepts only a restricted set of string/dictionary Pickle opcodes (protocol 0/2); it rejects object-construction and code-execution opcodes. The Android app imports this ZIP using **Импортировать базу Laguna II (ZIP)**, then **Открыть диагностические сеансы TCOM 116**. The initial pack is a curated Laguna II subset, not the entire 600 MB archive. The TCOM ECU list is a list of database candidates, not a scan result for a particular car.

## Build

The GitHub Actions workflow builds a debug APK for the `main` and `feature/bluetooth-elm327` branches. Download the `DDT4ALL-debug-APK` artifact from the successful workflow run.

## Target environment

- Android: API 26+
- Compile SDK: 35
- Java: 17
- Kotlin: 2.0.21
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
