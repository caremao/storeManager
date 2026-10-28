"""Convierte el SRD 2014 de 5e-bits/5e-database al formato compacto que importa StoreManager.

Uso: python -I convert_srd.py <Equipment.json> <Magic-Items.json> <salida.json>
"""
import json
import sys

UNIT_CP = {"cp": 1, "sp": 10, "ep": 50, "gp": 100, "pp": 1000}
RARITY = {
    "Common": "COMMON", "Uncommon": "UNCOMMON", "Rare": "RARE",
    "Very Rare": "VERY_RARE", "Legendary": "LEGENDARY", "Artifact": "ARTIFACT",
}
# Precios que el SRD trae en la tabla de equipo de aventurero pero 5e-database no asocia al objeto mágico.
MAGIC_OVERRIDES = {"potion-of-healing-common": {"valueCp": 5000, "weight": 0.5}}
CONSUMABLE_HINTS =("(vial)", "(flask)", "rations", "antitoxin", "holy water", "oil", "ball bearings", "caltrops")


def text(desc):
    return "\n\n".join(desc) if desc else None


def equipment_type(e):
    cat = e["equipment_category"]["name"]
    if cat in ("Weapon", "Armor"):
        return "EQUIPMENT"
    gear = (e.get("gear_category") or {}).get("name")
    name = e["name"].lower()
    if gear == "Ammunition" or any(h in name for h in CONSUMABLE_HINTS):
        return "CONSUMABLE"
    return "MISC"


def equipment_props(e):
    p = {}
    if e.get("category_range"):
        p["weaponCategory"] = e["category_range"]
    if e.get("damage"):
        p["damage"] = f'{e["damage"]["damage_dice"]} {e["damage"]["damage_type"]["name"]}'
    if e.get("two_handed_damage"):
        p["twoHandedDamage"] = f'{e["two_handed_damage"]["damage_dice"]} {e["two_handed_damage"]["damage_type"]["name"]}'
    if e.get("properties"):
        p["properties"] = [x["name"] for x in e["properties"]]
    if e.get("range") and e.get("weapon_range") == "Ranged":
        r = e["range"]
        p["range"] = f'{r["normal"]}/{r["long"]}' if r.get("long") else str(r["normal"])
    if e.get("throw_range"):
        p["throwRange"] = f'{e["throw_range"]["normal"]}/{e["throw_range"]["long"]}'
    if e.get("armor_category"):
        p["armorCategory"] = e["armor_category"]
    if e.get("armor_class"):
        ac = e["armor_class"]
        base = str(ac["base"])
        if ac.get("dex_bonus"):
            base += " + Des" + (f' (máx. {ac["max_bonus"]})' if ac.get("max_bonus") else "")
        p["armorClass"] = base
    if e.get("str_minimum"):
        p["strMinimum"] = e["str_minimum"]
    if e.get("stealth_disadvantage"):
        p["stealthDisadvantage"] = True
    if e.get("speed"):
        p["speed"] = f'{e["speed"]["quantity"]} {e["speed"]["unit"]}'
    if e.get("capacity"):
        p["capacity"] = e["capacity"]
    if e.get("contents"):
        p["contents"] = [f'{c["quantity"]}× {c["item"]["name"]}' for c in e["contents"]]
    return p or None


def magic_type(m):
    cat = m["equipment_category"]["name"]
    return {"Potion": "POTION", "Scroll": "SCROLL", "Weapon": "EQUIPMENT", "Armor": "EQUIPMENT",
            "Ammunition": "CONSUMABLE"}.get(cat, "MISC")


def main(eq_path, mi_path, out_path):
    with open(eq_path, encoding="utf-8") as f:
        equipment = json.load(f)
    with open(mi_path, encoding="utf-8") as f:
        magic = json.load(f)

    out = []
    seen = set()
    for e in equipment:
        cost = e.get("cost")
        out.append({
            "index": e["index"],
            "name": e["name"],
            "source": "EQUIPMENT",
            "type": equipment_type(e),
            "category": e["equipment_category"]["name"],
            "rarity": "COMMON",
            "weight": e.get("weight"),
            "valueCp": cost["quantity"] * UNIT_CP[cost["unit"]] if cost else None,
            "description": text(e.get("desc")),
            "properties": equipment_props(e),
        })
        seen.add(e["index"])
    for m in magic:
        index = m["index"] if m["index"] not in seen else "magic-" + m["index"]
        out.append({
            "index": index,
            "name": m["name"],
            "source": "MAGIC_ITEM",
            "type": magic_type(m),
            "category": m["equipment_category"]["name"],
            "rarity": RARITY.get(m["rarity"]["name"]),
            "weight": None,
            "valueCp": None,
            "description": text(m.get("desc")),
            "properties": None,
        })
        out[-1].update(MAGIC_OVERRIDES.get(m["index"], {}))
        seen.add(index)

    out.sort(key=lambda x: x["name"].lower())
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(out, f, ensure_ascii=False, indent=0, separators=(",", ":"))
        f.write("\n")
    print(f"{len(out)} items -> {out_path}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
