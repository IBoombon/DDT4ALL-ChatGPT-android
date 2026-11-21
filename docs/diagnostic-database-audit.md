# Diagnostic database archive audit

Audit source: `pyrendata_232_and_Lada_and_OldRenault.zip`

## Archive inventory

- ZIP entries: 7,183
- Uncompressed size: 600,180,531 bytes
- XML files: 5,967
- `.p` files: 1,216
- Top-level folders: `Vehicles/` (178 entries), `EcuRenault/` (6,981 entries), `Location/` (24 entries)
- Vehicle profile files matching `Vehicles/TCOM_<number>.xml`: 121
- XML parse check: all 5,967 XML files parsed successfully with Python's XML parser.
- XML root element counts: `ScmRoot` 4,334; `Ecu` 1,349; `Vehicle` 122; `Session` 85; remaining files use several specialized roots.

## File formats observed

### XML

The archive is heterogeneous XML, not one uniform document type. Examples:

- `Vehicles/TCOM_116.xml`: root `Vehicle`, TCOM 116, label `LAGUNA II`, topology index 14, vehicle type X74X.
- `Vehicles/TCOM_121.xml`: root `Vehicle`, TCOM 121, label `LAGUNAII ph2`, topology index 19, vehicle type X74.
- `EcuRenault/Uces.xml`: root `Ecus`, 2,505 child elements.
- Session files such as `FG0110192.xml`, `FG0210175.xml`, and `FG0410247.xml`: root `Ecu`, with sections such as `Screens`, `Datas`, `Helps`, and `GlobalInformations`.
- Scenario files commonly use root `ScmRoot`.

XML encodings are mixed: UTF-16LE BOM (127 files), UTF-8 BOM (82 files), and no BOM/other declaration (5,758 files). The parser accepted every XML file in this archive.

### Pickle protocol 2 (.p)

The sampled `.p` files are valid Python Pickle protocol 2 streams. They must not be treated as XML or plain text.

Two observed shapes:

- `Location/DiagOnCAN_GB.p`: a dictionary of string keys and string values, including localization/diagnostic text identifiers such as `C3P_33`, `ABS`, `1006`, and English text.
- `EcuRenault/Sessions/SG0111511.p`: a dictionary whose keys include paths such as `Identification\\Legacy_I505`, `State\\CS_P070`, `Parameter\\PR1129_CID1101H`, and `DF_D0054`; values are XML fragments for those diagnostic objects.

This is based on opcode inspection with `pickletools`; the archive was not deserialized with Python's general-purpose `pickle.load`. Android cannot consume Python Pickle directly without a dedicated safe conversion/import path.

## Renault Laguna II references found

TCOM 116 includes:
- CAN network index 0, pins 6/14.
- ISO network index 1, K/L pins 7/15.
- ECU references including engine candidates 10192/10194 and ABS candidates 10175/10177.
- UCH candidates 10247/10248.
- These are database candidates, not proof of installed modules in a specific vehicle.

TCOM 121 is a separate Laguna II Phase 2 profile and has a different topology and network layout. It must not be merged blindly with TCOM 116.

## Integration implications

1. Preserve the original relative paths and distinguish XML root/document types.
2. Build an index connecting vehicle TCOM profiles, ECU references, ECU definitions in `Uces.xml`, session XML, scenario XML, and matching `.p` resources.
3. Convert Pickle dictionaries to a neutral format (for example JSON or SQLite) using a restricted parser/converter. Do not execute arbitrary pickle payloads in the Android app.
4. Avoid loading the full expanded 600 MB database into memory. Use an indexed offline database pack and load individual documents on demand.
5. Keep diagnostic communication read-only until ECU addressing, session selection, timing, and command definitions have been validated against the exact vehicle.
6. The current Android prototype does not yet implement this database import, Renault ECU sessions, PIN extraction, or configuration/write operations.

## Current status

This commit records the archive format audit only. It does not claim that the archive has been integrated into the APK or that vehicle-specific Renault diagnostics are operational.
