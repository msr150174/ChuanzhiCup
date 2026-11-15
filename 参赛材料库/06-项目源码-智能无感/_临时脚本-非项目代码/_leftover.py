# -*- coding: utf-8 -*-
"""
扫描"卸载残留"——已卸载或已删除的软件留下的空目录/孤立文件。

判据：
  1. 空目录（或只含 desktop.ini / thumbs.db 等系统文件）
  2. 目录名像软件名，但目录里没有可执行文件（可能被删了一半）
  3. 用户 AppData / ProgramData 下孤立的数据目录
"""
import os
import time

OUT = r"D:\msr150174\项目\智能无感\_leftover.txt"

L = []


def fmt(n):
    if n >= 1024 ** 3:
        return f"{n/1024**3:.2f} GB"
    if n >= 1024 ** 2:
        return f"{n/1024**2:.1f} MB"
    if n >= 1024:
        return f"{n/1024:.0f} KB"
    return f"{n} B"


def scan_dir(p):
    """返回 (总大小, 文件数, 是否有exe, 是否有dll)"""
    total = 0
    files = 0
    has_exe = False
    has_dll = False
    for dp, dn, fn in os.walk(p):
        for f in fn:
            try:
                total += os.path.getsize(os.path.join(dp, f))
                files += 1
            except Exception:
                pass
            low = f.lower()
            if low.endswith(".exe"):
                has_exe = True
            if low.endswith(".dll"):
                has_dll = True
    return total, files, has_exe, has_dll


# ============ 1. 空目录扫描 ============
L.append("=" * 78)
L.append("  一、空目录（0 文件，或仅剩系统文件）")
L.append("=" * 78)
L.append("")

roots = ["D:\\", "C:\\Users\\msr150174\\AppData\\Local",
         "C:\\Users\\msr150174\\AppData\\Roaming",
         "C:\\ProgramData"]

SKIP = ["Windows", "WinSxS", "node_modules", ".git", "$Recycle", "System Volume",
        "Microsoft", "Packages", "Temp", "Installer", "Application Data"]

empties = []
for root in roots:
    if not os.path.isdir(root):
        continue
    for dp, dn, fn in os.walk(root):
        low = dp.lower()
        if any(s.lower() in low for s in SKIP):
            dn[:] = []
            continue
        # 按深度控制，太深不看
        if dp.count(os.sep) - root.count(os.sep) > 3:
            dn[:] = []
            continue
        real = [f for f in fn if f.lower() not in ("desktop.ini", "thumbs.db", ".ds_store")]
        if not real and not dn:
            # 完全空的目录
            empties.append((dp, len(fn)))

if empties:
    L.append(f"  找到 {len(empties)} 个空目录:")
    L.append("")
    for p, n in sorted(empties)[:60]:
        L.append(f"     {p}" + (f"   (含 {n} 个系统文件)" if n else ""))
else:
    L.append("  (无)")
L.append("")

# ============ 2. 半删残留（有目录无 exe） ============
L.append("=" * 78)
L.append("  二、疑似半删残留（目录存在，但无可执行文件）")
L.append("=" * 78)
L.append("")

half = []
soft_roots = [
    "D:\\", "C:\\Program Files", "C:\\Program Files (x86)",
    "C:\\Users\\msr150174\\AppData\\Local",
    "C:\\Users\\msr150174\\AppData\\Roaming",
]

for root in soft_roots:
    if not os.path.isdir(root):
        continue
    try:
        entries = [e for e in os.listdir(root) if os.path.isdir(os.path.join(root, e))]
    except Exception:
        continue
    for name in entries:
        if any(s.lower() in name.lower() for s in SKIP):
            continue
        full = os.path.join(root, name)
        try:
            total, files, has_exe, has_dll = scan_dir(full)
        except Exception:
            continue
        # 有 dll 但没 exe，且体积 > 10MB → 可能删了一半
        if files > 0 and not has_exe and total > 10 * 1024 * 1024:
            half.append((total, files, full))

if half:
    L.append(f"  找到 {len(half)} 个可疑目录:")
    L.append("")
    for total, files, p in sorted(half, reverse=True)[:40]:
        L.append(f"     {fmt(total):>10}  {files:>6,} 个文件  {p}")
else:
    L.append("  (无)")
L.append("")

# ============ 3. 已卸载软件的数据残留 ============
L.append("=" * 78)
L.append("  三、AppData 里孤立的数据目录（软件已不在）")
L.append("=" * 78)
L.append("")

KNOWN_GONE = [
    "360", "MuMu", "Netease", "YXArk", "Navicat Premium 12",
    "phpstudy", "biajie", "YXArkNights",
]

appdata_dirs = [
    "C:\\Users\\msr150174\\AppData\\Local",
    "C:\\Users\\msr150174\\AppData\\Roaming",
    "C:\\ProgramData",
]

found3 = []
for root in appdata_dirs:
    if not os.path.isdir(root):
        continue
    try:
        entries = os.listdir(root)
    except Exception:
        continue
    for name in entries:
        for k in KNOWN_GONE:
            if k.lower() in name.lower():
                full = os.path.join(root, name)
                if os.path.isdir(full):
                    t, f, e, d = scan_dir(full)
                    found3.append((t, f, full))

if found3:
    for t, f, p in sorted(found3, reverse=True):
        L.append(f"     {fmt(t):>10}  {f:>6,} 个文件  {p}")
else:
    L.append("  (无)")
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
