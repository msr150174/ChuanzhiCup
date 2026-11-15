# -*- coding: utf-8 -*-
"""
准确找出"卸载残留"。

方法：
  1. 收集所有正在运行进程的程序目录（这些肯定在用）
  2. 收集注册表里已安装程序的路径
  3. 扫描常见安装位置，找出既不在注册表、也无进程运行的目录
  4. 对可疑目录检查是否含 exe/dll（确实是程序目录）
"""
import json
import os
import subprocess

OUT = r"D:\msr150174\项目\智能无感\_orphan2.txt"

L = []


def run_ps(cmd):
    r = subprocess.run(
        ["powershell", "-NoProfile", "-Command", cmd],
        capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    return r.stdout or ""


# ============ 1. 正在运行的进程路径 ============
ps1 = r'''
Get-Process | Where-Object { $_.Path } |
  Select-Object -ExpandProperty Path | Sort-Object -Unique
'''
running = set()
for line in run_ps(ps1).splitlines():
    line = line.strip()
    if line and os.path.isabs(line):
        running.add(os.path.dirname(line).lower())
        # 也加上上层
        parts = os.path.dirname(line).split(os.sep)
        for i in range(2, len(parts)):
            running.add(os.sep.join(parts[:i]).lower())

L.append(f"  运行中进程的目录: {len(running)} 个")
L.append("")

# ============ 2. 注册表里的安装路径 ============
ps2 = r'''
$keys = @(
  "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*",
  "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*",
  "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*"
)
$out = @()
foreach ($k in $keys) {
  Get-ItemProperty $k -ErrorAction SilentlyContinue |
  Where-Object { $_.DisplayName } |
  ForEach-Object {
    if ($_.InstallLocation) { $out += $_.InstallLocation }
    if ($_.UninstallString)  { $out += $_.UninstallString }
  }
}
$out -join "`n"
'''
reg_raw = run_ps(ps2)
reg_paths = set()
for line in reg_raw.splitlines():
    line = line.strip().strip('"')
    if line:
        reg_paths.add(line.lower())

L.append(f"  注册表安装路径: {len(reg_paths)} 条")
L.append("")


def in_registry(path):
    """路径是否被注册表提及"""
    low = path.lower()
    for r in reg_paths:
        if low in r or r.startswith(low):
            return True
    return False


def in_running(path):
    low = path.lower()
    return low in running


# ============ 3. 扫描常见安装位置 ============
software_roots = [
    "C:\\Program Files",
    "C:\\Program Files (x86)",
    "D:\\",
    "D:\\Program Files",
    "D:\\Program Files (x86)",
    "C:\\Users\\msr150174\\AppData\\Local\\Programs",
]

SKIP_NAMES = {
    "windows", "windowsapps", "winsxs", "temp", "tmp", "$recycle.bin",
    "system volume information", "programdata", "users", "msys64",
    "common files", "internet explorer", "windows defender",
    "windows nt", "windows photo viewer", "windows portable devices",
    "windows sidebar", "windows mail", "modifiablewindowsapps",
    "application data", "desktop", "documents", "downloads",
    "msr150174", "msr150174", "perflogs", "recovery",
}


def scan(p):
    total = 0
    exe = 0
    dll = 0
    for dp, dn, fn in os.walk(p):
        for f in fn:
            try:
                total += os.path.getsize(os.path.join(dp, f))
            except Exception:
                pass
            low = f.lower()
            if low.endswith(".exe"):
                exe += 1
            elif low.endswith(".dll"):
                dll += 1
    return total, exe, dll


L.append("=" * 78)
L.append("  疑似卸载残留（有程序文件，但注册表无记录、进程未运行）")
L.append("=" * 78)
L.append("")

suspects = []

for root in software_roots:
    if not os.path.isdir(root):
        continue
    try:
        items = os.listdir(root)
    except Exception:
        continue
    for name in items:
        full = os.path.join(root, name)
        if not os.path.isdir(full):
            continue
        if name.lower() in SKIP_NAMES:
            continue
        # $ 开头的系统目录
        if name.startswith("$") or name.startswith("."):
            continue

        if in_registry(full) or in_running(full):
            continue

        # 还在用吗？看有没有进程路径在这个目录下
        used = any(full.lower() in r for r in running)
        if used:
            continue

        try:
            total, exe, dll = scan(full)
        except Exception:
            continue

        # 只有含 exe 的才可能是程序目录
        if exe > 0 and total > 5 * 1024 * 1024:
            suspects.append((total, exe, dll, full))

if suspects:
    for total, exe, dll, p in sorted(suspects, reverse=True):
        mb = total / 1024 / 1024
        L.append(f"  {mb:>9,.1f} MB   exe={exe:<4} dll={dll:<5}  {p}")
else:
    L.append("  (无)")
    L.append("")
    L.append("  说明：所有含 exe 的目录都在注册表或进程列表里")

L.append("")
L.append("=" * 78)
L.append(f"  合计 {len(suspects)} 个可疑目录")
L.append("=" * 78)
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
