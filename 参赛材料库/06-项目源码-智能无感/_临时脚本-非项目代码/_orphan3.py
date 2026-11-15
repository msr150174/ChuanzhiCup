# -*- coding: utf-8 -*-
"""对可疑目录逐个验证：是不是真的残留。"""
import os
import subprocess

OUT = r"D:\msr150174\项目\智能无感\_orphan3.txt"

CANDIDATES = [
    (r"C:\Program Files\MuMuVMMVbox", "MuMu 模拟器虚拟化组件"),
    (r"C:\Program Files (x86)\NVIDIA Corporation", "NVIDIA 组件"),
    (r"C:\Program Files (x86)\EVCapture", "EVCapture 录屏"),
    (r"C:\Program Files (x86)\Baidu", "百度组件"),
    (r"C:\Program Files (x86)\Tencent", "腾讯组件"),
    (r"C:\Program Files\Microsoft Office 15", "Office 15 旧版"),
    (r"C:\Program Files (x86)\Microsoft SDKs", "Microsoft SDKs"),
    (r"C:\Program Files\Steam++", "Steam++"),
    (r"C:\Program Files\JetBrains", "JetBrains IDE"),
    (r"D:\PCL 正式版 2.10.9", "PCL 我的世界启动器"),
    (r"D:\Bongo Cat Mver爱音", "Bongo Cat"),
    (r"D:\qq", "QQ"),
    (r"D:\kugo", "酷狗"),
    (r"D:\cpolar", "cpolar 内网穿透"),
    (r"D:\QQFile", "QQ 文件"),
    (r"D:\c++", "C++ 相关"),
    (r"D:\压缩", "压缩工具"),
    (r"D:\工具集", "工具集"),
    (r"D:\运行文件", "运行文件"),
    (r"D:\VCPchat", "VCPchat"),
    (r"D:\newapi", "newapi"),
    (r"D:\SDK", "SDK"),
    (r"D:\VirtuslEnvironment", "虚拟环境"),
    (r"D:\Program Files\Go", "Go 语言"),
]

L = []
L.append("=" * 80)
L.append("  逐个验证可疑目录")
L.append("=" * 80)
L.append("")

total_suspect = 0

for path, desc in CANDIDATES:
    if not os.path.isdir(path):
        L.append(f"  [不存在] {path}")
        continue

    size = 0
    exes = []
    has_uninst = False
    for dp, dn, fn in os.walk(path):
        for f in fn:
            fp = os.path.join(dp, f)
            try:
                size += os.path.getsize(fp)
            except Exception:
                pass
            low = f.lower()
            if low.endswith(".exe"):
                exes.append(f)
            if "uninst" in low or low == "unins000.exe":
                has_uninst = True

    mb = size / 1024 / 1024

    # 判断
    verdict = "?"
    if has_uninst:
        verdict = "有卸载器 → 可能已装（可能绿色版）"
    elif not exes:
        verdict = "无 exe → 数据目录"
    else:
        # 看有没有对应的进程
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             f"Get-Process | Where-Object {{ $_.Path -like '{path}\\*' }} | Select-Object -First 1 -ExpandProperty ProcessName"],
            capture_output=True, text=True, encoding="utf-8", errors="replace"
        )
        proc = (r.stdout or "").strip()
        if proc:
            verdict = f"进程运行中: {proc}"
        else:
            verdict = "★ 无进程 + 有 exe → 疑似残留"
            total_suspect += size

    L.append(f"  {mb:>9,.1f} MB   {desc}")
    L.append(f"      {path}")
    L.append(f"      exe={len(exes)}  卸载器={'有' if has_uninst else '无'}")
    L.append(f"      → {verdict}")
    if exes:
        L.append(f"      主要 exe: {', '.join(exes[:4])}")
    L.append("")

L.append("=" * 80)
L.append(f"  疑似残留合计: {total_suspect/1024/1024/1024:.2f} GB")
L.append("=" * 80)
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
