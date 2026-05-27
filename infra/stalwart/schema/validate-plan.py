#!/usr/bin/env python3
"""Validate a stalwart-cli `apply` NDJSON plan against the server config
schema (schema.min.json in this directory).

The schema is the same artifact stalwart-cli downloads and caches from the
server; it describes every management object (x:* JMAP types), their fields,
types, enums, and discriminated-union variants. This validator checks object
names, field names, enum values, and nested object/variant shapes so plan
mistakes are caught before they reach a live server.

Usage: validate-plan.py <schema.json> <plan.ndjson> [plan2 ...]
Lines may contain ${VAR} placeholders (prod template); they are replaced with
a dummy token so the JSON parses.
"""
import json
import re
import sys

PLACEHOLDER = re.compile(r"\$\{[A-Za-z_][A-Za-z0-9_]*\}")


def load_schema(path):
    return json.load(open(path))


class Validator:
    def __init__(self, schema):
        self.schemas = schema["schemas"]
        self.fields = schema["fields"]
        self.enums = schema["enums"]
        self.errors = []

    def err(self, where, msg):
        self.errors.append(f"{where}: {msg}")

    def props_for_schema_name(self, schema_name):
        return self.fields.get(schema_name, {}).get("properties", {})

    def resolve(self, object_name, value, where):
        """Resolve the property map for an object schema, handling
        single vs multiple (discriminated-union via @type) schemas."""
        sch = self.schemas.get(object_name)
        if sch is None:
            self.err(where, f"unknown object/schema {object_name!r}")
            return None
        if sch.get("type") == "single":
            return self.props_for_schema_name(sch["schemaName"])
        if sch.get("type") == "multiple":
            tag = value.get("@type") if isinstance(value, dict) else None
            if tag is None:
                self.err(where, f"{object_name} requires a @type discriminator")
                return None
            variant = next((v for v in sch["variants"] if v["name"] == tag), None)
            if variant is None:
                names = ", ".join(v["name"] for v in sch["variants"])
                self.err(where, f"{object_name}: unknown @type {tag!r} (valid: {names})")
                return None
            return self.props_for_schema_name(variant.get("schemaName", ""))
        return None

    def check_enum(self, enum_name, val, where):
        variants = self.enums.get(enum_name)
        if variants is None:
            return  # unknown enum table; skip
        names = {v["name"] for v in variants}
        if val not in names:
            self.err(where, f"invalid {enum_name} value {val!r} (valid: {', '.join(sorted(names))})")

    def check_value(self, type_def, val, where):
        """Validate a value against a field type definition."""
        if isinstance(val, str) and PLACEHOLDER.fullmatch(val):
            return  # substituted placeholder; skip strict typing
        t = type_def.get("type")
        if t == "enum":
            if isinstance(val, str):
                self.check_enum(type_def["enumName"], val, where)
        elif t == "object":
            if isinstance(val, dict):
                self.check_object(type_def["objectName"], val, where)
        elif t == "set":
            # set is encoded as {member: true} or a list
            cls = type_def.get("class", {})
            members = val.keys() if isinstance(val, dict) else (val if isinstance(val, list) else [])
            for m in members:
                self.check_value(cls, m, where)
        elif t == "list":
            cls = type_def.get("class", {})
            if isinstance(val, list):
                for i, m in enumerate(val):
                    self.check_value(cls, m, f"{where}[{i}]")
        # scalar types (string/number/boolean) — accept as-is

    def check_object(self, object_name, value, where):
        props = self.resolve(object_name, value, where)
        if props is None:
            return
        for key, val in value.items():
            if key == "@type":
                continue
            # references like "#create-id" are opaque cross-refs
            if isinstance(val, str) and val.startswith("#"):
                if key not in props:
                    self.err(where, f"unknown field {key!r} on {object_name}")
                continue
            fdef = props.get(key)
            if fdef is None:
                self.err(where, f"unknown field {key!r} on {object_name} "
                                f"(valid: {', '.join(sorted(props))})")
                continue
            self.check_value(fdef.get("type", {}), val, f"{where}.{key}")

    def check_op(self, op, where):
        kind = op.get("@type")
        obj = op.get("object")
        if kind not in ("create", "update", "destroy"):
            self.err(where, f"unknown op @type {kind!r}")
            return
        if obj is None:
            self.err(where, "missing 'object'")
            return
        object_name = "x:" + obj
        if object_name not in self.schemas:
            self.err(where, f"unknown object {obj!r}")
            return
        if kind == "destroy":
            return
        value = op.get("value", {})
        if kind == "update":
            # singleton: value is the field map directly
            self.check_object(object_name, value, where)
        else:  # create: value is {create-id: record}
            if not isinstance(value, dict):
                self.err(where, "create value must be a map of create-id -> record")
                return
            for cid, record in value.items():
                self.check_object(object_name, record, f"{where}[{cid}]")


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        sys.exit(2)
    schema = load_schema(sys.argv[1])
    total_errors = 0
    for plan_path in sys.argv[2:]:
        v = Validator(schema)
        for n, line in enumerate(open(plan_path), 1):
            line = line.strip()
            if not line:
                continue
            line = PLACEHOLDER.sub("__PLACEHOLDER__", line)
            try:
                op = json.loads(line)
            except json.JSONDecodeError as e:
                v.err(f"{plan_path}:{n}", f"invalid JSON: {e}")
                continue
            v.check_op(op, f"{plan_path}:{n}")
        if v.errors:
            print(f"FAIL {plan_path}:")
            for e in v.errors:
                print(f"  {e}")
            total_errors += len(v.errors)
        else:
            print(f"OK   {plan_path}")
    sys.exit(1 if total_errors else 0)


if __name__ == "__main__":
    main()
