# -*- coding: utf-8 -*-
"""清理确认的卸载残留（仅限不需要管理员权限的部分）。"""
import os
import shutil
import stat

OUT = r"D:\msr150174\项目\智能无感\_clean-leftover.txt"

TARGETS = [
    # 360 系列
    (r"C:\Users\msr150174\AppData\Roaming\360browser", "360 浏览器"),
    (r"C:\Users\msr150174\AppData\Roaming\360Game5", "360 游戏大厅"),
    (r"C:\Users\msr150174\AppData\Roaming\360se6", "360 安全浏览器"),
    (r"C:\Users\msr150174\AppData\Roaming\360mobilemgr", "360 手机助手"),
    (r"C:\Users\msr150174\AppData\Roaming\360Quarant", "360 隔离区"),
    (r"C:\Users\msr150174\AppData\Roaming\360Login", "360 登录组件"),
    (r"C:\Users\msr150174\AppData\Local\360Safe", "360 安全卫士"),
    (r"C:\ProgramData\360SD", "360 杀毒"),
    (r"C:\ProgramData\360zip", "360 压缩"),
    # MuMu / 网易 残留
    (r"C:\Users\msr150174\AppData\Local\MuMuPlayer", "MuMu 模拟器数据"),
    (r"C:\ProgramData\.MUMUVMM", "MuMu 虚拟化配置"),
    (r"C:\ProgramData\Netease", "网易残留"),
    # SAI 绘图安装包（放在 QQFile 里，命名误导）
    (r"D:\QQFile", "SAI 绘图安装包"),
]

L = []
L.append("=" * 74)
L.append("  清理卸载残留")
L.append("=" * 74)
L.append("")

freed = 0.0
ok = 0
skip = 0


def calc(p):
    t = 0
    for dp, dn, fn in os.walk(p):
        for f in fn:
            try:
                t += os.path.getsize(os.path.join(dp, f))
            except Exception:
                pass
    return t


def force_del(path):
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

    def onerr(func, p, exc):
        try:
            os.chmod(p, stat.S_IWRITE | stat.S_IREAD)
            func(p)
        except Exception:
            pass

    shutil.rmtree(path, onerror=onerr)


for path, desc in TARGETS:
    if not os.path.exists(path):
        L.append(f"  [跳过] 不存在  {path}")
        skip += 1
        continue

    size = calc(path) if os.path.isdir(path) else os.path.getsize(path)
    mb = size / 1024 / 1024

    try:
        if os.path.isdir(path):
            force_del(path)
        else:
            os.remove(path)

        if os.path.exists(path):
            left = sum(len(f) for _, _, f in os.walk(path)) if os.path.isdir(path) else 1
            L.append(f"  [部分] {mb:7,.1f} MB  {desc}  (剩 {left} 个文件)")
            skip += 1
        else:
            L.append(f"  [删除] {mb:7,.1f} MB  {desc}")
            freed += size
            ok += 1
    except Exception as e:
        L.append(f"  [失败] {mb:7,.1f} MB  {desc}  -- {str(e)[:45]}")
        skip += 1

L.append("")
L.append("=" * 74)
L.append(f"  成功 {ok} 项，未完成 {skip} 项")
L.append(f"  释放 {freed/1024/1024:.1f} MB")
L.append("=" * 74)
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
