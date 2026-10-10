#!/usr/bin/env python3
"""
ギャルのパンティを頭に被ったときの見た目 (装備テクスチャ 64x32) を生成します。

被り方 (変態仮面のように、パンツを顔に被った想定。パンツの前側が顔、股があご):
  ・腰のゴムの中央にあるリボンは、アイコンのパンティと同じ位置・同じ形のまま。
    被ったときは、そのゴムの中央が「頭頂部の前半分」に来る
      (リボンの結び目の側 = パンツの体の側 = 顔の側。輪っかの側 = 後ろ側)
  ・額はゴムではなく、パンツ本体の布
  ・足を通す穴 (レッグホール) が目の穴。眉・目・目の下の頬が見える
  ・目の穴以外の顔 (鼻筋・口・あご・頬) は布で覆われ、あごに向かって細くなる
  ・布は頬から耳の手前まで回り込む。耳より後ろは素の頭
  ・頭頂部の後ろ半分・後頭部・耳より後ろは完全に透明
    → 各プレイヤー自身のスキン (髪型・後頭部など) がそのまま見える

布の無い場所はすべて完全な透明 (alpha=0) です。

頭の部分の配置 (バニラのスキンと同じ。各 8x8):
  上 (8,0)  下 (16,0)
  右 (0,8)  前 (8,8)  左 (16,8)  後 (24,8)

文字の意味:  W=布(白)  B=腰のゴム  S=ゴムの縁・縫い目の影  R=リボン(赤)  D=結び目(濃い赤)  .=透明

    python3 tools/make_panties_worn_texture.py
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "resourcepack", "assets", "dbzmod",
                   "textures", "entity", "equipment", "humanoid", "gal_panties.png")

COLORS = {
    "W": (255, 255, 255, 255),
    "B": (238, 240, 248, 255),
    "S": (205, 210, 228, 255),
    "R": (226, 32, 44, 255),
    "D": (150, 14, 28, 255),
}

# 前面: 額は布 (ゴムは頭頂部へ)。目は大きな足通し穴から。穴以外の顔は布で覆い、あごに向かって細くなる
FRONT = [
    "WWWWBWWW",
    "WWWWBWWW",
    "WWWWBWWW",
    "W..WB..W",
    "...WB...",
    "W..WB..W",
    "WWWWBWWW",
    "..WWBW..",
]

# 右側面 (キャラの右)。顔に近い側 (右端) だけ布で、頬からあごへ向かって細くなる。耳より後ろは素の頭
RIGHT = [
    "....WWWW",
    "....WWWW",
    "....WWWW",
    "....WWWW",
    "....WWWW",
    ".....WWW",
    "......WW",
    ".......W",
]

# 左側面 (キャラの左)。顔に近い側 (左端) だけ布
LEFT = [
    "WWWW....",
    "WWWW....",
    "WWWW....",
    "WWWW....",
    "WWWW....",
    "WWW.....",
    "WW......",
    "W.......",
]

# 後頭部: 全部透明 (素の後頭部が見える)
BACK = ["........"] * 8

# 頭頂部: 前半分 (下の4段) だけが布。腰のゴムが頭頂部をまたぎ、その中央にリボンが来る。
#   リボンはアイコンと同じ形 (幅4px x 高さ2px)。
#   上の段 = 輪っか2つ (赤) + 間はゴムの色、下の段 = 輪っか + 結び目 (濃い赤)。結び目の側が顔の側。
#   後ろ半分 (上の4段) は透明で、髪が見える。ゴムの下 (一番下の段) は、顔のパンツ本体につながる
TOP = [
    "........",
    "........",
    "........",
    "........",
    "SSSSSSSS",   # ゴムの縁 (外側)
    "BBRBBRBB",   # ゴム + リボンの上の段
    "BBRDDRBB",   # ゴム + リボンの下の段 (結び目)
    "WWWWWWWW",   # パンツ本体 (額へつながる)
]

# あご側: 全部透明
BOTTOM = ["........"] * 8

FACES = {
    (8, 8): FRONT,
    (0, 8): RIGHT,
    (16, 8): LEFT,
    (24, 8): BACK,
    (8, 0): TOP,
    (16, 0): BOTTOM,
}


def build():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()
    for (ox, oy), rows in FACES.items():
        assert len(rows) == 8 and all(len(r) == 8 for r in rows), (ox, oy)
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch != ".":
                    px[ox + x, oy + y] = COLORS[ch]
    return img


if __name__ == "__main__":
    image = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    image.save(OUT)
    print("生成:", os.path.normpath(OUT))
