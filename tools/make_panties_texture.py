#!/usr/bin/env python3
"""ギャルのパンティ (gal_panties) の 16x16 テクスチャを生成します。
白いパンティに、腰のゴムの中央に赤い小さなリボン。
    python3 tools/make_panties_texture.py
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                   "resourcepack", "assets", "dbzmod", "textures", "item", "gal_panties.png")

# 形 (y: x の範囲)。腰のゴムから下に向かって細くなる三角ぎみのシルエット
ROWS = {
    3: (2, 13), 4: (2, 13), 5: (2, 13), 6: (2, 13),
    7: (3, 12), 8: (3, 12),
    9: (4, 11), 10: (5, 10), 11: (6, 9), 12: (7, 8),
}

WHITE = (255, 255, 255, 255)    # 本体
BAND = (238, 240, 248, 255)     # 腰のゴム (ほんの少し陰影)
SHADE = (214, 219, 234, 255)    # 右側の影
OUTLINE = (128, 134, 158, 255)  # 輪郭 (白が背景で沈まないよう、青みのある灰色)
RED = (226, 32, 44, 255)        # リボン
RED_DARK = (150, 14, 28, 255)   # リボンの結び目

mask = set()
for y, (x0, x1) in ROWS.items():
    for x in range(x0, x1 + 1):
        mask.add((x, y))

img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
px = img.load()

for (x, y) in mask:
    px[x, y] = BAND if y <= 4 else WHITE

# 右側に影 (輪郭のすぐ内側)
for y in range(5, 12):
    x1 = ROWS[y][1] - 1
    if (x1, y) in mask:
        px[x1, y] = SHADE

# 輪郭
for (x, y) in mask:
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if (x + dx, y + dy) not in mask:
            px[x, y] = OUTLINE
            break

# 腰のゴムの中央に赤い小さなリボン (左右の輪っか + 中央の結び目。4px幅×2px高)
for (x, y) in [(6, 3), (6, 4), (9, 3), (9, 4)]:
    px[x, y] = RED
for (x, y) in [(7, 4), (8, 4)]:
    px[x, y] = RED_DARK
# 結び目の上は、輪っかの間を明るい色で抜いて「リボンの形」に見せる
for (x, y) in [(7, 3), (8, 3)]:
    px[x, y] = BAND

os.makedirs(os.path.dirname(OUT), exist_ok=True)
img.save(OUT)
print("生成:", os.path.normpath(OUT))
