# -*- coding: utf-8 -*-
"""测量 AI 对话各阶段耗时，定位慢在哪。"""
import json
import re
import time
import urllib.request

BASE = "http://localhost:3000"
LOG = r"D:\msr150174\项目\智能无感\转换-Java后端\guardian-server\logs\guardian.log"
OUT = r"D:\msr150174\项目\智能无感\_perf.txt"
PHONE = "13800000001"


def req(p, m="GET", b=None, t=None, timeout=120):
    d = json.dumps(b).encode("utf-8") if b is not None else None
    r = urllib.request.Request(BASE + p, data=d, method=m)
    r.add_header("Content-Type", "application/json; charset=utf-8")
    if t:
        r.add_header("Authorization", "Bearer " + t)
    t0 = time.time()
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data, (time.time() - t0) * 1000
    except Exception as e:
        return {"code": -1, "msg": str(e)}, (time.time() - t0) * 1000


L = []

# 登录
r, _ = req("/api/auth/send-code", "POST", {"phone": PHONE})
code = (r.get("data") or {}).get("devCode")
L.append(f"登录验证码: {code}")
r, _ = req("/api/auth/login", "POST", {"phone": PHONE, "code": code})
token = (r.get("data") or {}).get("token")
L.append(f"登录: {'OK' if token else 'FAIL'}")
L.append("")

# 清空 AI 缓存，测真实的冷启动耗时
L.append("=" * 70)
L.append("  冷启动耗时（无缓存）")
L.append("=" * 70)

questions = [
    "你好",
    "我睡不着怎么办",
    "给我讲个笑话吧",
    "最近血压有点高要注意什么",
    "推荐一些适合老年人的运动",
]

for q in questions:
    r, ms = req("/api/ai/chat", "POST", {"message": q}, token)
    d = r.get("data") or {}
    L.append(f"  {ms:8.0f}ms  provider={str(d.get('provider')):8}  「{q}」")
    L.append(f"              {str(d.get('content', ''))[:60]}")

L.append("")
L.append("=" * 70)
L.append("  热缓存耗时（同样问题再问一次）")
L.append("=" * 70)

for q in questions[:3]:
    r, ms = req("/api/ai/chat", "POST", {"message": q}, token)
    d = r.get("data") or {}
    L.append(f"  {ms:8.0f}ms  provider={str(d.get('provider')):8}  「{q}」")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
