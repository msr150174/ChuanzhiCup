# -*- coding: utf-8 -*-
"""全面扫描 D 盘所有文件夹（两级，按大小）。"""
import os
import time

OUT = r"D:\msr150174\项目\智能无感\_dscan.txt"

L = []


def dirsize(p):
    total = 0
    files = 0
    for dp, dn, fn in os.walk(p):
        for f in fn:
            try:
                total += os.path.getsize(os.path.join(dp, f))
                files += 1
            except Exception:
                pass
    return total, files


def fmt(n):
    if n >= 1024 ** 3:
        return f"{n/1024**3:.2f} GB"
    if n >= 1024 ** 2:
        return f"{n/1024**2:.0f} MB"
    if n >= 1024:
        return f"{n/1024:.0f} KB"
    return f"{n} B"


L.append("=" * 80)
L.append("  D 盘完整扫描（顶层 + 二级）")
L.append("=" * 80)
L.append("")

# 顶层
tops = []
for name in os.listdir("D:\\"):
    full = os.path.join("D:\\", name)
    try:
        mtime = os.path.getmtime(full)
    except Exception:
        mtime = 0
    if os.path.isdir(full):
        s, c = dirsize(full)
        tops.append((s, c, name, True, mtime))
    else:
        try:
            s = os.path.getsize(full)
        except Exception:
            s = 0
        tops.append((s, 1, name, False, mtime))

tops.sort(reverse=True)

total_all = sum(t[0] for t in tops)
L.append(f"  D 盘已用合计: {fmt(total_all)}")
L.append("")
L.append("-" * 80)
L.append("  顶层目录（按大小）")
L.append("-" * 80)
L.append("")

for s, c, name, isdir, mtime in tops:
    if s < 50 * 1024 * 1024 and not isdir:
        continue
    age = (time.time() - mtime) / 86400 if mtime else -1
    agestr = f"{age:.0f}天" if age >= 0 else "?"
    kind = "[D]" if isdir else "[F]"
    L.append(f"  {fmt(s):>11}  {c:>8,} 个  {agestr:>7}  {kind} {name}")

L.append("")

# 二级展开（只展开 > 500MB 的目录）
L.append("=" * 80)
L.append("  二级明细（仅 >500MB 的目录）")
L.append("=" * 80)
L.append("")

for s, c, name, isdir, mtime in tops:
    if not isdir or s < 500 * 1024 * 1024:
        continue
    full = os.path.join("D:\\", name)
    L.append(f"  ┌─ {name}  ({fmt(s)})")
    try:
        subs = []
        for n2 in os.listdir(full):
            f2 = os.path.join(full, n2)
            if os.path.isdir(f2):
                s2, c2 = dirsize(f2)
            else:
                try:
                    s2 = os.path.getsize(f2)
                except Exception:
                    s2 = 0
                c2 = 1
            try:
                m2 = os.path.getmtime(f2)
            except Exception:
                m2 = 0
            subs.append((s2, c2, n2, os.path.isdir(f2), m2))
        subs.sort(reverse=True)
        shown = 0
        for s2, c2, n2, d2, m2 in subs:
            if s2 < 20 * 1024 * 1024 and shown > 0:
                continue
            if shown >= 15:
                rest = sum(x[0] for x in subs[shown:])
                if rest > 0:
                    L.append(f"  │     ... 其余 {len(subs)-shown} 项  {fmt(rest)}")
                break
            age2 = (time.time() - m2) / 86400 if m2 else -1
            L.append(f"  │  {fmt(s2):>10}  {c2:>7,} 个  {age2:>5.0f}天  {'[D]' if d2 else '[F]'} {n2}")
            shown += 1
    except Exception:
        L.append("  │  (读取失败)")
    L.append("  └─")
    L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
