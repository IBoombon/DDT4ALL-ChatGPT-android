#!/usr/bin/env python3
"""Build a Laguna II offline pack without executing Python Pickle payloads."""
from __future__ import annotations
import argparse, json, pickletools, zipfile
from pathlib import PurePosixPath
from xml.etree import ElementTree as ET

MAX_PICKLE_BYTES = 8 * 1024 * 1024
MAX_ITEMS = 200_000
MAX_STRING = 2 * 1024 * 1024

class SafePickleError(ValueError):
    pass

def safe_pickle_dict(data: bytes) -> dict[str, str]:
    """Interpret only the string-to-string dictionary opcodes observed in this archive."""
    if len(data) > MAX_PICKLE_BYTES:
        raise SafePickleError("pickle exceeds size limit")
    stack, memo, marker, count = [], {}, object(), 0
    allowed = {"PROTO", "EMPTY_DICT", "MARK", "BINUNICODE", "UNICODE",
               "BINPUT", "LONG_BINPUT", "PUT", "BINGET", "LONG_BINGET",
               "SETITEM", "SETITEMS", "STOP", "DICT"}
    try:
        operations = list(pickletools.genops(data))
    except Exception as exc:
        raise SafePickleError(f"invalid pickle stream: {exc}") from exc
    for op, arg, pos in operations:
        name = op.name
        if name not in allowed:
            raise SafePickleError(f"forbidden opcode {name} at {pos}")
        if name == "PROTO":
            if arg not in (0, 2):
                raise SafePickleError(f"unsupported protocol {arg}")
        elif name == "EMPTY_DICT":
            stack.append({})
        elif name == "MARK":
            stack.append(marker)
        elif name in ("BINUNICODE", "UNICODE"):
            if not isinstance(arg, str) or len(arg) > MAX_STRING:
                raise SafePickleError("string limit/type violation")
            stack.append(arg)
        elif name in ("BINPUT", "LONG_BINPUT", "PUT"):
            if not stack:
                raise SafePickleError("memo write on empty stack")
            memo[int(arg)] = stack[-1]
        elif name in ("BINGET", "LONG_BINGET"):
            if int(arg) not in memo:
                raise SafePickleError("unknown memo reference")
            stack.append(memo[int(arg)])
        elif name == "SETITEM":
            if len(stack) < 3:
                raise SafePickleError("invalid SETITEM stack")
            value, key = stack.pop(), stack.pop()
            target = stack[-1]
            if not isinstance(target, dict) or not isinstance(key, str) or not isinstance(value, str):
                raise SafePickleError("only string dictionary entries accepted")
            target[key] = value
            count += 1
        elif name in ("SETITEMS", "DICT"):
            try:
                idx = len(stack) - 1 - stack[::-1].index(marker)
            except ValueError as exc:
                raise SafePickleError("missing MARK") from exc
            items = stack[idx + 1:]
            del stack[idx:]
            if len(items) % 2:
                raise SafePickleError("odd dictionary item count")
            if name == "DICT":
                target = {}
                pairs = zip(items[::2], items[1::2])
                stack.append(target)
            else:
                if not stack or not isinstance(stack[-1], dict):
                    raise SafePickleError("SETITEMS target missing")
                target = stack[-1]
                pairs = zip(items[::2], items[1::2])
            for key, value in pairs:
                if not isinstance(key, str) or not isinstance(value, str):
                    raise SafePickleError("only string dictionary entries accepted")
                target[key] = value
                count += 1
            if count > MAX_ITEMS or len(target) > MAX_ITEMS:
                raise SafePickleError("item limit exceeded")
        elif name == "STOP":
            if len(stack) != 1 or not isinstance(stack[0], dict):
                raise SafePickleError("root must be one dictionary")
            return stack[0]
    raise SafePickleError("missing STOP")

def xml_text(node, name):
    child = node.find(name)
    return child.text.strip() if child is not None and child.text else None

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archive")
    parser.add_argument("--output", default="laguna2-data.zip")
    parser.add_argument("--tcom", type=int, default=116)
    args = parser.parse_args()
    with zipfile.ZipFile(args.archive) as src:
        names = src.namelist()
        lower = {name.lower(): name for name in names}
        uces_path = lower.get("ecurenault/uces.xml")
        profile_path = lower.get(f"vehicles/tcom_{args.tcom}.xml")
        if not uces_path or not profile_path:
            raise SystemExit("Required Uces.xml or TCOM profile not found")
        uces_root = ET.fromstring(src.read(uces_path))
        uces = {e.get("name"): e for e in uces_root.findall("EcuData") if e.get("name")}
        profile = ET.fromstring(src.read(profile_path))
        references, selected = [], {uces_path, profile_path}
        def find_session(reference, extensions):
            if not reference:
                return None
            base = "EcuRenault/Sessions/" + reference.rsplit(".", 1)[0]
            for ext in extensions:
                path = lower.get((base + "." + ext).lower())
                if path:
                    return path
            return None
        for kind in profile.findall(".//EcuKind"):
            for ref in kind.findall("EcuRef"):
                ecu_id = ref.get("name")
                definition = uces.get(ecu_id)
                if definition is None:
                    continue
                model = xml_text(definition, "ModelId")
                optimizer = xml_text(definition, "OptimizerId")
                model_path = find_session(model, ["xml"])
                optimizer_path = find_session(optimizer, ["p", "xml"])
                info = definition.find("ECUInformations")
                def info_value(name):
                    node = info.find(name) if info is not None else None
                    return node.get("value") if node is not None else None
                frames = info.find("Frames") if info is not None else None
                apc = frames.find("APC") if frames is not None else None
                ids = info.find("Ids") if info is not None else None
                id_frame = ids.find("IdFrame") if ids is not None else None
                id_byte = ids.find("IdByte") if ids is not None else None
                item = {"familyId": kind.get("idFamily"), "ecuId": ecu_id,
                        "doc": ref.get("doc"), "stdType": definition.get("stdType"),
                        "modelPath": model_path, "optimizerPath": optimizer_path,
                        "protocol": info_value("Protocol"), "baudRate": info_value("BaudRate"),
                        "fastInitAddress": info_value("FastInitAddress"),
                        "slowInitAddress": info_value("SlowInitAddress"),
                        "kw1": info_value("KW1"), "kw2": info_value("KW2"),
                        "apcRequest": apc.get("request") if apc is not None else None,
                        "apcResponse": apc.get("response") if apc is not None else None,
                        "identificationRequest": id_frame.get("request") if id_frame is not None else None,
                        "identificationResponse": id_frame.get("response") if id_frame is not None else None,
                        "identificationLength": id_frame.get("length") if id_frame is not None else None,
                        "identificationMode": id_frame.get("mode") if id_frame is not None else None,
                        "identificationByteRank": id_byte.get("rank") if id_byte is not None else None,
                        "identificationByteMask": id_byte.get("mask") if id_byte is not None else None,
                        "identificationByteValue": id_byte.get("value") if id_byte is not None else None}
                references.append(item)
                if model_path:
                    selected.add(model_path)
                if optimizer_path:
                    selected.add(optimizer_path)
        manifest = {"format": "renault-laguna2-pack-v1", "tcom": args.tcom,
                    "label": profile.get("defaultText"), "vehicleTypeCode": profile.get("vehTypeCode"),
                    "topologyIndex": profile.get("indexTopo"), "ecuReferences": references,
                    "sourceArchive": PurePosixPath(args.archive).name,
                    "picklePolicy": "restricted string-to-string dictionary opcodes only; no object construction or code execution"}
        converted, rejected = 0, []
        with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as out:
            out.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
            for path in sorted(selected):
                data = src.read(path)
                if path.lower().endswith(".p"):
                    try:
                        obj = safe_pickle_dict(data)
                        out.writestr(path[:-2] + ".json",
                                     json.dumps(obj, ensure_ascii=False, separators=(",", ":")))
                        converted += 1
                    except SafePickleError as exc:
                        rejected.append({"path": path, "reason": str(exc)})
                else:
                    out.writestr(path, data)
            out.writestr("conversion-report.json", json.dumps(
                {"convertedPickleFiles": converted, "rejectedPickleFiles": rejected,
                 "selectedFiles": len(selected)}, ensure_ascii=False, indent=2))
    print(json.dumps({"output": args.output, "tcom": args.tcom,
                      "ecuReferences": len(references), "selectedFiles": len(selected),
                      "convertedPickleFiles": converted, "rejectedPickleFiles": rejected},
                     ensure_ascii=False, indent=2))

if __name__ == "__main__":
    main()
