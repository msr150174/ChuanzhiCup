# -*- coding: utf-8 -*-
"""删除 360 残留 + 一年未动的目录（先展示，再删）。"""
import os
import shutil
import time

OUT = r"D:\msr150174\项目\智能无感\_clean-360.txt"

# 目标目录（大小已知）
TARGETS = [
    (r"D:\360MoveData", "360 搬家工具残留"),
    (r"D:\360Downloads", "360 下载残留"),
    (r"D:\360RecycleBin", "360 回收站残留"),
    (r"D:\新建文件夹 (7)", "一年未动，疑似垃圾"),
    (r"D:\WX", "一年未动"),
    (r"D:\6.1.13.7", "版本号命名的垃圾目录"),
    (r"D:\biajie", "一年未动"),
    (r"D:\phpstudy_pro", "一年未动（PHP 环境）"),
    (r"D:\ollama\OllamaSetup.exe", "Ollama 安装包"),
]

L = []
L.append("=" * 74)
L.append("  清理 360 残留 + 一年未动的目录")
L.append("=" * 74)
L.append("")

freed = 0
ok = 0
skip = 0

for path, desc in TARGETS:
    if not os.path.exists(path):
        L.append(f"  [跳过] 不存在: {path}")
        skip += 1
        continue

    # 算大小
    if os.path.isdir(path):
        size = 0
        for dp, dn, fn in os.walk(path):
            for f in fn:
                try:
                    size += os.path.getsize(os.path.join(dp, f))
                except Exception:
                    pass
        mtime = os.path.getmtime(path)
    else:
        try:
            size = os.path.getsize(path)
        except Exception:
            size = 0
        mtime = os.path.getmtime(path)

    age = (time.time() - mtime) / 86400
    mb = size / 1024 / 1024

    L.append(f"  {desc}")
    L.append(f"     {path}")
    L.append(f"     大小 {mb:,.0f} MB   最后修改 {age:.0f} 天前")

    try:
        if os.path.isdir(path):
            shutil.rmtree(path)
        else:
            os.remove(path)
        freed += size
        ok += 1
        L.append(f"     [已删除]")
    except Exception as e:
        L.append(f"     [失败] {str(e)[:60]}")
        skip += 1

    L.append("")

L.append("=" * 74)
L.append(f"  成功 {ok} 项，失败 {skip} 项")
L.append(f"  释放 {freed/1024/1024/1024:.2f} GB")
L.append("=" * 74)
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
