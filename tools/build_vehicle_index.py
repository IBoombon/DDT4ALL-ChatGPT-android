#!/usr/bin/env python3
"""Build a safe, compact vehicle-to-ECU index from a PyRen/DDT archive.

This script reads XML only. It deliberately does not unpickle .p files.
Usage:
  python tools/build_vehicle_index.py archive.zip --output vehicle-index.json
"""
from __future__ import annotations

import argparse
import json
import re
import zipfile
import xml.etree.ElementTree as ET
from pathlib import PurePosixPath


def xml_from_zip(zf: zipfile.ZipFile, name: str) -> ET.Element:
    # ElementTree handles the BOM/XML declaration encodings found in this archive.
    return ET.fromstring(zf.read(name))


def child_text(node: ET.Element, name: str) -> str | None:
    child = node.find(name)
    return child.text.strip() if child is not None and child.text else None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("archive")
    parser.add_argument("--output", default="vehicle-index.json")
    args = parser.parse_args()

    with zipfile.ZipFile(args.archive) as zf:
        names = zf.namelist()
        uces_path = next((n for n in names if n.lower() == "ecurenault/uces.xml"), None)
        if not uces_path:
            raise SystemExit("EcuRenault/Uces.xml not found")
        uces_root = xml_from_zip(zf, uces_path)
        uces = {e.get("name"): e for e in uces_root.findall("EcuData") if e.get("name")}

        profiles = []
        for path in names:
            match = re.fullmatch(r"Vehicles/TCOM_(\d+)\.xml", path, re.IGNORECASE)
            if not match:
                continue
            root = xml_from_zip(zf, path)
            ecus = []
            for kind in root.findall(".//EcuKind"):
                family = kind.get("idFamily")
                for ref in kind.findall("EcuRef"):
                    ecu_id = ref.get("name")
                    definition = uces.get(ecu_id)
                    model = child_text(definition, "ModelId") if definition is not None else None
                    optimizer = child_text(definition, "OptimizerId") if definition is not None else None
                    ecus.append({
                        "family_id": family,
                        "ecu_id": ecu_id,
                        "vehicle_doc": ref.get("doc"),
                        "programmable_in_vehicle_xml": ref.get("Programmable"),
                        "uces_found": definition is not None,
                        "uces_doc": definition.get("doc") if definition is not None else None,
                        "std_type": definition.get("stdType") if definition is not None else None,
                        "model_file": model,
                        "optimizer_file": optimizer,
                        "model_path": f"EcuRenault/Sessions/{model}" if model else None,
                        "optimizer_path": f"EcuRenault/Sessions/{optimizer}" if optimizer else None,
                    })
            profiles.append({
                "tcom": int(match.group(1)),
                "source_path": path,
                "label": root.get("defaultText"),
                "vehicle_type_code": root.get("vehTypeCode"),
                "topology_index": root.get("indexTopo"),
                "ecu_references": ecus,
            })

    result = {
        "format": "ddt4all-vehicle-index-v1",
        "source_archive": PurePosixPath(args.archive).name,
        "safety_note": "Index metadata only; ECU references do not identify hardware installed in a particular car.",
        "profile_count": len(profiles),
        "profiles": sorted(profiles, key=lambda x: x["tcom"]),
    }
    with open(args.output, "w", encoding="utf-8") as out:
        json.dump(result, out, ensure_ascii=False, indent=2)
        out.write("\n")
    linked = sum(1 for p in profiles for e in p["ecu_references"] if e["uces_found"])
    total = sum(len(p["ecu_references"]) for p in profiles)
    print(f"Wrote {args.output}: {len(profiles)} profiles, {total} ECU refs, {linked} linked to Uces.xml")


if __name__ == "__main__":
    main()
