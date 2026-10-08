#!/usr/bin/env python3
"""
resourcepack/ の中身 (モデル・アイテム定義・言語ファイル・pack.mcmeta) を生成します。
テクスチャ (resourcepack/assets/dbzmod/textures/item/*.png) は別途用意してください。

球の大きさを変えたい場合は RADIUS と、DISPLAY の scale を調整して再実行します。
    python3 tools/generate_pack.py
"""
import json
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack")
NS = os.path.join(ROOT, "assets", "dbzmod")

RADIUS = 5            # 球の半径 (モデル上のピクセル数)。直径 = RADIUS * 2
FILL = 4.9            # 球の中に入れるかどうかの判定半径 (少し小さくすると角が丸くなる)
CENTER = 8            # モデル空間 (0..16) の中心

# 手に持ったとき・落としたとき・額縁に入れたときの見え方。実機で見ながら調整してください。
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [1.13, 3.2, 1.13], "scale": [1.0, 1.0, 1.0]},
    "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [1.13, 3.2, 1.13], "scale": [1.0, 1.0, 1.0]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.8, 0.8, 0.8]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1.0, 1.0, 1.0]},
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [1.4, 1.4, 1.4]},
}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def voxelize():
    n = RADIUS * 2
    filled = set()
    for i in range(n):
        for j in range(n):
            for k in range(n):
                dx, dy, dz = i + 0.5 - RADIUS, j + 0.5 - RADIUS, k + 0.5 - RADIUS
                if dx * dx + dy * dy + dz * dz <= FILL * FILL:
                    filled.add((i, j, k))
    return filled


def merge_boxes(filled):
    """ボクセルを、できるだけ大きな直方体にまとめる。"""
    used = set()
    boxes = []
    n = RADIUS * 2
    for k in range(n):
        for j in range(n):
            for i in range(n):
                if (i, j, k) not in filled or (i, j, k) in used:
                    continue
                i2 = i
                while (i2 + 1, j, k) in filled and (i2 + 1, j, k) not in used:
                    i2 += 1
                j2 = j
                while all((x, j2 + 1, k) in filled and (x, j2 + 1, k) not in used for x in range(i, i2 + 1)):
                    j2 += 1
                k2 = k
                while all((x, y, k2 + 1) in filled and (x, y, k2 + 1) not in used
                          for x in range(i, i2 + 1) for y in range(j, j2 + 1)):
                    k2 += 1
                for x in range(i, i2 + 1):
                    for y in range(j, j2 + 1):
                        for z in range(k, k2 + 1):
                            used.add((x, y, z))
                boxes.append((i, j, k, i2, j2, k2))
    return boxes


def build_elements():
    filled = voxelize()
    boxes = merge_boxes(filled)
    lo = CENTER - RADIUS          # 球の外接立方体の最小座標
    hi = CENTER + RADIUS
    s = 16.0 / (RADIUS * 2)       # 1モデルpx あたりのテクスチャpx

    def r(v):
        return round(v, 3)

    elements = []
    for (i, j, k, i2, j2, k2) in boxes:
        x0, y0, z0 = lo + i, lo + j, lo + k
        x1, y1, z1 = lo + i2 + 1, lo + j2 + 1, lo + k2 + 1

        def covered(cells):
            return all(c in filled for c in cells)

        faces = {}
        # south (+Z)
        if not covered([(x, y, k2 + 1) for x in range(i, i2 + 1) for y in range(j, j2 + 1)]):
            faces["south"] = {"uv": [r((x0 - lo) * s), r((hi - y1) * s), r((x1 - lo) * s), r((hi - y0) * s)], "texture": "#ball"}
        # north (-Z)
        if not covered([(x, y, k - 1) for x in range(i, i2 + 1) for y in range(j, j2 + 1)]):
            faces["north"] = {"uv": [r((hi - x1) * s), r((hi - y1) * s), r((hi - x0) * s), r((hi - y0) * s)], "texture": "#ball"}
        # east (+X)
        if not covered([(i2 + 1, y, z) for y in range(j, j2 + 1) for z in range(k, k2 + 1)]):
            faces["east"] = {"uv": [r((hi - z1) * s), r((hi - y1) * s), r((hi - z0) * s), r((hi - y0) * s)], "texture": "#ball"}
        # west (-X)
        if not covered([(i - 1, y, z) for y in range(j, j2 + 1) for z in range(k, k2 + 1)]):
            faces["west"] = {"uv": [r((z0 - lo) * s), r((hi - y1) * s), r((z1 - lo) * s), r((hi - y0) * s)], "texture": "#ball"}
        # up (+Y)
        if not covered([(x, j2 + 1, z) for x in range(i, i2 + 1) for z in range(k, k2 + 1)]):
            faces["up"] = {"uv": [r((x0 - lo) * s), r((z0 - lo) * s), r((x1 - lo) * s), r((z1 - lo) * s)], "texture": "#ball"}
        # down (-Y)
        if not covered([(x, j - 1, z) for x in range(i, i2 + 1) for z in range(k, k2 + 1)]):
            faces["down"] = {"uv": [r((x0 - lo) * s), r((hi - z1) * s), r((x1 - lo) * s), r((hi - z0) * s)], "texture": "#ball"}

        if faces:
            elements.append({"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces})
    return elements


def main():
    elements = build_elements()

    # 立体モデルの共通ジオメトリ
    write(os.path.join(NS, "models", "item", "dragonball_base_3d.json"), {
        "textures": {"particle": "#ball"},
        "elements": elements,
        "display": DISPLAY,
    })

    ball_names = ["一星球", "二星球", "三星球", "四星球", "五星球", "六星球", "七星球"]
    ball_names_en = ["One-Star", "Two-Star", "Three-Star", "Four-Star", "Five-Star", "Six-Star", "Seven-Star"]

    for n in range(1, 8):
        # インベントリ用の平らなアイコン
        write(os.path.join(NS, "models", "item", f"dragonball_{n}.json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"dbzmod:item/dragonball_{n}"},
        })
        # 手持ち・落下用の立体モデル
        write(os.path.join(NS, "models", "item", f"dragonball_{n}_3d.json"), {
            "parent": "dbzmod:item/dragonball_base_3d",
            "textures": {"ball": f"dbzmod:item/dragonball_{n}"},
        })
        # GUI (インベントリ・ホットバー) では平らな絵、それ以外は立体
        write(os.path.join(NS, "items", f"dragonball_{n}.json"), {
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:display_context",
                "cases": [
                    {"when": ["gui"], "model": {"type": "minecraft:model", "model": f"dbzmod:item/dragonball_{n}"}}
                ],
                "fallback": {"type": "minecraft:model", "model": f"dbzmod:item/dragonball_{n}_3d"},
            }
        })

    # ドラゴンレーダー
    write(os.path.join(NS, "models", "item", "dragon_radar.json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "dbzmod:item/dragon_radar"},
    })
    for k in range(32):
        write(os.path.join(NS, "models", "item", f"dragon_radar_{k:02d}.json"), {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"dbzmod:item/dragon_radar_{k:02d}"},
        })
    frames = [(16 + i) % 32 for i in range(32)]  # バニラのコンパスと同じ並び (16 が北)
    entries = []
    for i, f in enumerate(frames):
        entries.append({
            "model": {"type": "minecraft:model", "model": f"dbzmod:item/dragon_radar_{f:02d}"},
            "threshold": 0.0 if i == 0 else i - 0.5,
        })
    entries.append({"model": {"type": "minecraft:model", "model": "dbzmod:item/dragon_radar_16"}, "threshold": 31.5})
    write(os.path.join(NS, "items", "dragon_radar.json"), {
        "model": {
            "type": "minecraft:condition",
            "property": "minecraft:has_component",
            "component": "minecraft:lodestone_tracker",
            "on_true": {
                "type": "minecraft:range_dispatch",
                "property": "minecraft:compass",
                "target": "lodestone",
                "scale": 32.0,
                "entries": entries,
            },
            "on_false": {"type": "minecraft:model", "model": "dbzmod:item/dragon_radar"},
        }
    })

    # 言語ファイル
    ja = {f"item.dbzmod.dragonball_{i + 1}": ball_names[i] for i in range(7)}
    ja["item.dbzmod.dragon_radar"] = "ドラゴンレーダー"
    en = {f"item.dbzmod.dragonball_{i + 1}": f"{ball_names_en[i]} Dragon Ball" for i in range(7)}
    en["item.dbzmod.dragon_radar"] = "Dragon Radar"
    write(os.path.join(NS, "lang", "ja_jp.json"), ja)
    write(os.path.join(NS, "lang", "en_us.json"), en)

    # pack.mcmeta (形式84 = 26.1〜26.1.2、88 = 26.2、97 = 26.3)。上限を広めに取り、将来の版でも警告が出にくくする
    write(os.path.join(ROOT, "pack.mcmeta"), {
        "pack": {
            "description": "Dragon Ball Server Pack (Dragon Balls + Dragon Radar)",
            "min_format": 84,
            "max_format": 127,
        }
    })

    # pack アイコン
    icon = Image.open(os.path.join(NS, "textures", "item", "dragonball_4.png")).convert("RGBA")
    icon.resize((128, 128), Image.NEAREST).save(os.path.join(ROOT, "pack.png"))

    print(f"生成完了: 立体モデルの直方体 {len(elements)} 個")


if __name__ == "__main__":
    main()
