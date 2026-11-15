# -*- coding: utf-8 -*-
"""分析 MuMu 模拟器占用。"""
import os
import subprocess

OUT = r"D:\msr150174\项目\智能无感\_emu.txt"

L = []


def dirsize(p):
    total = 0
    for dp, dn, fn in os.walk(p):
        for f in fn:
            try:
                total += os.path.getsize(os.path.join(dp, f))
            except Exception:
                pass
    return total


def fm(mb):
    return f"{mb:,.0f} MB"


L.append("=" * 74)
L.append("  MuMu 模拟器占用分析")
L.append("=" * 74)
L.append("")

# 1. 可能的安装位置
cands = [
    r"D:\Program Files\YXArkNights-12.0",
    r"D:\Program Files\MuMuPlayer-12.0",
    r"D:\Program Files\Netease\MuMuPlayer-12.0",
    r"C:\Program Files\Netease\MuMuPlayer-12.0",
    r"D:\MuMuPlayer-12.0",
    r"D:\MuMu",
    r"D:\Program Files\YXArkNights-12.0",
]

L.append("  安装目录:")
found_dirs = []
for p in cands:
    if os.path.isdir(p):
        s = dirsize(p) / 1024 / 1024
        found_dirs.append((s, p))
        L.append(f"     {fm(s):>14}   {p}")

# 扫 D:\Program Files 找其他模拟器
pf = r"D:\Program Files"
if os.path.isdir(pf):
    for d in os.listdir(pf):
        if any(k in d for k in ["MuMu", "Nemu", "YXArk", "Player"]):
            full = os.path.join(pf, d)
            if os.path.isdir(full) and not any(full == p for _, p in found_dirs):
                s = dirsize(full) / 1024 / 1024
                found_dirs.append((s, full))
                L.append(f"     {fm(s):>14}   {full}")

L.append("")

# 2. 虚拟磁盘
L.append("  虚拟磁盘文件:")
allvdi = []
for _, p in found_dirs:
    for dp, dn, fn in os.walk(p):
        for f in fn:
            if f.lower().endswith((".vdi", ".vmdk", ".qcow2", ".img")):
                full = os.path.join(dp, f)
                try:
                    sz = os.path.getsize(full) / 1024 / 1024
                except Exception:
                    sz = 0
                allvdi.append((sz, full))
for sz, p in sorted(allvdi, reverse=True)[:10]:
    L.append(f"     {fm(sz):>14}   {p}")
if not allvdi:
    L.append("     (无)")
L.append("")

# 3. 用户数据
L.append("  用户数据目录:")
userdirs = [
    os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "MuMu共享文件夹"),
    os.path.join(os.environ.get("USERPROFILE", ""), "Documents", "MuMu文件共享"),
    os.path.join(os.environ.get("APPDATA", ""), "Netease"),
    os.path.join(os.environ.get("LOCALAPPDATA", ""), "Netease"),
    r"D:\MuMu共享文件夹",
]
for p in userdirs:
    if os.path.isdir(p):
        s = dirsize(p) / 1024 / 1024
        L.append(f"     {fm(s):>14}   {p}")
L.append("")

# 4. 运行中的进程
L.append("  运行中的进程:")
try:
    r = subprocess.run(
        ["tasklist", "/fo", "csv", "/nh"],
        capture_output=True, text=True, encoding="gbk", errors="replace"
    )
    hits = [l for l in r.stdout.splitlines()
            if any(k.lower() in l.lower() for k in ["mumu", "nemu", "yxark", "dnplayer"])]
    if hits:
        for h in hits:
            parts = h.split('","')
            L.append("     " + parts[0].strip('"') + "  PID " + parts[1].strip('"'))
    else:
        L.append("     (未运行)")
except Exception as e:
    L.append("     查询失败: " + str(e))

L.append("")

# 5. 汇总
total = sum(s for s, _ in found_dirs)
L.append("=" * 74)
L.append(f"  模拟器目录合计: {total/1024:.2f} GB")
L.append("=" * 74)
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
