# -*- coding: utf-8 -*-
"""分析 D:\临时文件 的内容。"""
import os
import time

OUT = r"D:\msr150174\项目\智能无感\_tmpdir.txt"
ROOT = r"D:\临时文件"

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
    for unit in ["B", "KB", "MB", "GB"]:
        if n < 1024:
            return f"{n:.0f} {unit}"
        n /= 1024
    return f"{n:.1f} TB"


L.append("=" * 78)
L.append("  D:\\临时文件  内容分析")
L.append("=" * 78)
L.append("")

if not os.path.isdir(ROOT):
    L.append("  目录不存在")
else:
    total, files = dirsize(ROOT)
    L.append(f"  总计: {fmt(total)}   文件数: {files:,}")
    L.append("")
    L.append("-" * 78)
    L.append("  顶层条目（按大小）")
    L.append("-" * 78)
    L.append("")

    items = []
    for name in os.listdir(ROOT):
        full = os.path.join(ROOT, name)
        try:
            mtime = os.path.getmtime(full)
        except Exception:
            mtime = 0
        if os.path.isdir(full):
            s, c = dirsize(full)
        else:
            try:
                s = os.path.getsize(full)
            except Exception:
                s = 0
            c = 1
        items.append((s, c, name, os.path.isdir(full), mtime))

    for s, c, name, isdir, mtime in sorted(items, reverse=True):
        age = (time.time() - mtime) / 86400 if mtime else -1
        kind = "[D]" if isdir else "[F]"
        agestr = f"{age:.0f}天前" if age >= 0 else "?"
        L.append(f"  {fmt(s):>10}  {c:>7,} 文件  {agestr:>8}  {kind} {name}")

    L.append("")

    # 对最大的几个目录再展开一层
    L.append("-" * 78)
    L.append("  最大 3 个目录的下一层")
    L.append("-" * 78)
    L.append("")

    top3 = [x for x in sorted(items, reverse=True) if x[3]][:3]
    for s, c, name, isdir, mtime in top3:
        sub = os.path.join(ROOT, name)
        L.append(f"  [{name}]  {fmt(s)}")
        try:
            subs = []
            for n2 in os.listdir(sub):
                f2 = os.path.join(sub, n2)
                if os.path.isdir(f2):
                    s2, c2 = dirsize(f2)
                else:
                    try:
                        s2 = os.path.getsize(f2)
                    except Exception:
                        s2 = 0
                    c2 = 1
                subs.append((s2, c2, n2, os.path.isdir(f2)))
            for s2, c2, n2, d2 in sorted(subs, reverse=True)[:12]:
                L.append(f"      {fmt(s2):>10}  {c2:>6,}  {'[D]' if d2 else '[F]'} {n2}")
        except Exception as e:
            L.append("      (读取失败)")
        L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
