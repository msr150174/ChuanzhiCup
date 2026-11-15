# -*- coding: utf-8 -*-
"""清除只读/隐藏属性后重试删除。"""
import os
import stat
import shutil

OUT = r"D:\msr150174\项目\智能无感\_clean-retry.txt"

TARGETS = [
    (r"D:\360MoveData", "360 搬家残留"),
    (r"D:\新建文件夹 (7)", "一年未动"),
    (r"D:\WX", "一年未动"),
    (r"D:\phpstudy_pro", "PHP 环境，一年未动"),
]

L = []
L.append("=" * 74)
L.append("  清除属性后重试删除")
L.append("=" * 74)
L.append("")


def force_del(path):
    """先清属性，再删。"""
    # 1. 清掉整棵树的只读/隐藏/系统属性
    for dp, dn, fn in os.walk(path):
        for name in fn + dn:
            full = os.path.join(dp, name)
            try:
                os.chmod(full, stat.S_IWRITE | stat.S_IREAD)
            except Exception:
                pass
    try:
        os.chmod(path, stat.S_IWRITE | stat.S_IREAD)
    except Exception:
        pass

    # 2. 用 onerror 处理顽固文件
    def onerr(func, p, exc):
        try:
            os.chmod(p, stat.S_IWRITE | stat.S_IREAD)
            func(p)
        except Exception:
            pass

    shutil.rmtree(path, onerror=onerr)


freed = 0
ok = 0
skip = 0

for path, desc in TARGETS:
    if not os.path.exists(path):
        L.append(f"  [跳过] 不存在: {path}")
        continue

    size = 0
    for dp, dn, fn in os.walk(path):
        for f in fn:
            try:
                size += os.path.getsize(os.path.join(dp, f))
            except Exception:
                pass
    mb = size / 1024 / 1024

    L.append(f"  [{desc}]  {path}   {mb:,.0f} MB")
    try:
        force_del(path)
        if os.path.exists(path):
            # 还有残留，列出
            left = sum(len(f) for _, _, f in os.walk(path))
            L.append(f"     [部分删除] 剩余 {left} 个文件")
            skip += 1
        else:
            freed += size
            ok += 1
            L.append(f"     [已删除]")
    except Exception as e:
        L.append(f"     [失败] {str(e)[:60]}")
        skip += 1
    L.append("")

L.append("=" * 74)
L.append(f"  成功 {ok} 项，未完成 {skip} 项")
L.append(f"  释放 {freed/1024/1024/1024:.2f} GB")
L.append("=" * 74)
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
