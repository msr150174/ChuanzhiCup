# -*- coding: utf-8 -*-
"""
找出「注册表还有卸载项，但程序目录已经不存在」的残留。
这类是最典型的"卸载没删干净"。
"""
import os
import subprocess

OUT = r"D:\msr150174\项目\智能无感\_orphan.txt"

L = []
L.append("=" * 78)
L.append("  注册表有记录但程序目录已不存在的（卸载残留）")
L.append("=" * 78)
L.append("")

# 用 PowerShell 读注册表
ps = r'''
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
    $loc = $_.InstallLocation
    $un  = $_.UninstallString
    $out += [PSCustomObject]@{
      Name = $_.DisplayName
      Loc  = $loc
      Un   = $un
      Size = $_.EstimatedSize
    }
  }
}
$out | ConvertTo-Json -Depth 3 -Compress
'''

r = subprocess.run(
    ["powershell", "-NoProfile", "-Command", ps],
    capture_output=True, text=True, encoding="utf-8", errors="replace"
)

import json
try:
    apps = json.loads(r.stdout)
except Exception as e:
    L.append("  读取注册表失败: " + str(e))
    apps = []

if isinstance(apps, dict):
    apps = [apps]

L.append(f"  注册表中共 {len(apps)} 个程序")
L.append("")

orphans = []
for a in apps:
    loc = a.get("Loc") or ""
    un = a.get("Un") or ""
    name = a.get("Name") or "?"

    # 从卸载命令里提取路径
    path = None
    if loc and os.path.isdir(loc):
        path = loc
        exists = True
    else:
        # 从 UninstallString 提取 exe 路径
        import re
        m = re.match(r'^"([^"]+)"', un)
        if not m:
            m = re.match(r'^(\S+\.exe)', un, re.I)
        if m:
            path = m.group(1)
            exists = os.path.exists(path)
        else:
            continue

    if not exists and path:
        sz = a.get("Size") or 0
        orphans.append((name, path, sz))

if orphans:
    L.append(f"  ★ 找到 {len(orphans)} 个残留（注册表有，程序没了）:")
    L.append("")
    for name, path, sz in sorted(orphans, key=lambda x: -(x[2] or 0)):
        mb = (sz or 0) / 1024
        L.append(f"     {mb:>8,.0f} MB  {name}")
        L.append(f"                 {path}")
else:
    L.append("  (无)")

L.append("")
L.append("=" * 78)
L.append("  说明：这些条目可以安全清理（程序已不存在）")
L.append("=" * 78)
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
