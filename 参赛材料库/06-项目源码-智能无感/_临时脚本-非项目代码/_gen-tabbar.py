# -*- coding: utf-8 -*-
"""
生成 tabBar 图标。

微信小程序 tabBar 图标要求：
  - PNG 格式
  - 建议 81x81 px
  - 未选中：灰色线条
  - 选中：主题色

设计约定：每个 draw_xxx(color) 直接返回一张画好的 RGBA 图，
不在外层再做任何 alpha 处理 —— 之前就是因为外层多挖了一次，
把月亮整片挖没了。
"""
import os
from PIL import Image, ImageDraw

OUT = r"D:\msr150174\项目\智能无感\转换-uniapp前端\guardian-uniapp\src\static\tabbar"

SIZE = 81
GRAY = (125, 116, 107, 255)      # --text-2 未选中
GREEN = (143, 166, 142, 255)     # --primary 选中
W = 5                            # 线宽


def blank():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def ring(d, cx, cy, r, color, width=W):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=width)


def dot(d, cx, cy, r, color):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)


# ------------------------------------------------------------ 月亮（节律守护）
def draw_moon(color):
    """
    月牙：一个圆，右边被另一张遮罩裁掉，形成弯月。

    做法是先在遮罩上画「保留区」，再用它把实心圆贴出来。
    """
    img, d = blank()
    d.ellipse([14, 10, 68, 64], fill=color)

    keep = Image.new("L", (SIZE, SIZE), 255)
    kd = ImageDraw.Draw(keep)
    # 内侧的圆挖空 —— 这就是月牙的凹口
    kd.ellipse([30, 4, 84, 58], fill=0)
    # 右下角也裁一点，让月牙更明显
    kd.ellipse([46, 30, 90, 74], fill=0)

    img.putalpha(Image.composite(img.getchannel("A"),
                                Image.new("L", (SIZE, SIZE), 0), keep))
    return img


# ------------------------------------------------------------ 盾牌（居家守护）
def draw_shield(color):
    img, d = blank()
    pts = [(40, 12), (64, 22), (64, 42), (40, 68), (16, 42), (16, 22)]
    d.polygon(pts, outline=color, width=W)
    d.line([(29, 39), (37, 48), (53, 30)], fill=color, width=W, joint="curve")
    return img


# ------------------------------------------------------------ 心形（健康监测）
def draw_heart(color):
    img, d = blank()
    ring(d, 29, 30, 13, color)
    ring(d, 52, 30, 13, color)
    # 下半部的 V，用两条线画，保持线条风格统一
    d.line([(17, 33), (40, 64)], fill=color, width=W)
    d.line([(64, 33), (40, 64)], fill=color, width=W)
    return img


# ------------------------------------------------------------ 对话气泡（智能助手）
def draw_chat(color):
    img, d = blank()
    d.rounded_rectangle([12, 18, 69, 54], radius=12, outline=color, width=W)
    d.polygon([(26, 52), (26, 66), (40, 52)], fill=color)
    for x in (27, 40, 53):
        dot(d, x, 36, 3, color)
    return img


# ------------------------------------------------------------ 人形（个人中心）
def draw_user(color):
    img, d = blank()
    ring(d, 40, 26, 13, color)
    d.arc([14, 44, 66, 86], start=180, end=360, fill=color, width=W)
    return img


ICONS = {
    "moon":   draw_moon,
    "shield": draw_shield,
    "heart":  draw_heart,
    "chat":   draw_chat,
    "user":   draw_user,
}


os.makedirs(OUT, exist_ok=True)
print(f"{'file':<26}{'size':>8}")
print("-" * 36)

total = 0
for name, fn in ICONS.items():
    for suffix, color in (("", GRAY), ("-active", GREEN)):
        img = fn(color)
        p = os.path.join(OUT, f"{name}{suffix}.png")
        img.save(p, "PNG")
        s = os.path.getsize(p)
        total += s
        print(f"{name + suffix + '.png':<26}{s:>6} B")

print("-" * 36)
print(f"{'10 files':<26}{total:>6} B")
