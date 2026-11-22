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

All 1,216 `.p` files were opcode-checked successfully: 1,215 use Pickle protocol 2 and one legacy file (`EcuRenault/Sessions/SG0110102.p`) uses the older protocol-0 form. The common opcode patterns are dictionary construction and string key/value insertion; no file failed the opcode parser.

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


## Vehicle-to-ECU indexer (implemented)

A first reproducible indexing tool now exists at `tools/build_vehicle_index.py` on branch `feature/bluetooth-elm327`.

- It reads vehicle profile XML and `EcuRenault/Uces.xml`, then emits a compact JSON index.
- It records TCOM profile identity, ECU family, ECU ID, vehicle-side document label, matching Uces entry, model XML path, and optimizer resource path.
- It resolves references against actual archive member names. This matters because model references may say `.XML` while the archive stores `.xml`, and optimizer references may say `.XML` while the resource is actually a `.p` file.
- It does not deserialize Pickle. Optimizer resources are indexed as paths only.
- Usage: `python tools/build_vehicle_index.py archive.zip --output vehicle-index.json`.

### Directly checked Laguna profile links

For TCOM 116, the profile contains 39 ECU references; 34 have matching entries in `Uces.xml`. The database links include:

| Family | ECU ID | Profile document | Uces model | Optimizer resource |
|---|---:|---|---|---|
| 1 (injection) | 10192 | INJ_EDC15C_00C3_0C_A | FG0110192.xml | SG0110192.p |
| 1 (injection) | 10194 | INJ_EDC15C_74Ph1_9BC1_10_A | FG0110194.xml | SG0110194.p |
| 2 (ABS) | 10175 | ABS_BMKX74_0000_0C_A | FG0210175.xml | SG0210175.p |
| 2 (ABS) | 10177 | ABS_BMKX74_0F08_08_A | FG0210177.xml | SG0210177.p |
| 4 (UCH) | 10247 | UCH_BUCH74_0090_04_A | FG0410247.xml | SG0410247.p |
| 4 (UCH) | 10248 | UCH_BUCH74_0090_0C_A | FG0410248.xml | SG0410248.p |

TCOM 121 has 38 ECU references, 32 linked to `Uces.xml`. In the inspected data it shares UCH candidate IDs 10247/10248 with TCOM 116, but has a different topology/profile identity. This is database linkage only, not confirmation that any one of these ECUs is fitted to the user's car.

The indexer is a first data-processing step, not yet Android integration. No live ECU session, UCH service, card registration, PIN extraction, or write operation is enabled by this change.
