# -*- coding: utf-8 -*-
"""诊断小程序 AI 对话问题。"""
import json
import re
import time
import urllib.request
import urllib.error

BASE = "http://localhost:3000"
LOG = r"D:\msr150174\项目\智能无感\转换-Java后端\guardian-server\logs\guardian.log"
OUT = r"D:\msr150174\项目\智能无感\_ai-diag.txt"
PHONE = "13800000001"


def req(p, m="GET", b=None, t=None, timeout=90):
    d = json.dumps(b).encode("utf-8") if b is not None else None
    r = urllib.request.Request(BASE + p, data=d, method=m)
    r.add_header("Content-Type", "application/json; charset=utf-8")
    if t:
        r.add_header("Authorization", "Bearer " + t)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": e.code, "msg": "HTTP " + str(e.code)}
    except Exception as e:
        return {"code": -1, "msg": str(e)}


L = []

# 先看有没有现成的有效 token
r = req("/api/auth/send-code", "POST", {"phone": PHONE})
L.append(f"send-code: code={r.get('code')} msg={r.get('msg')} devMode={(r.get('data') or {}).get('devMode')}")

# devCode 直接返回了，不用去日志找
code = (r.get("data") or {}).get("devCode")
if not code:
    time.sleep(3)
    with open(LOG, encoding="utf-8", errors="ignore") as f:
        for line in f.readlines()[-300:]:
            m = re.search(r"验证码[：:]\s*(\d{6})", line)
            if m:
                code = m.group(1)

L.append(f"验证码: {code}")

r = req("/api/auth/login", "POST", {"phone": PHONE, "code": code})
d = r.get("data") or {}
token = d.get("token")
user = d.get("user") or {}
L.append(f"登录: code={r.get('code')} msg={r.get('msg')}")
L.append(f"  isAdmin={user.get('isAdmin')}  name={user.get('name')}")
L.append("")

if not token:
    L.append("❌ 登录失败")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L))
    print("done")
    raise SystemExit(0)

L.append("=" * 70)
L.append("  AI 对话接口测试")
L.append("=" * 70)

for msg in ["你好", "我睡不着怎么办", "今天适合运动吗"]:
    r = req("/api/ai/chat", "POST", {"message": msg}, token)
    d = r.get("data") or {}
    L.append(f"  问: {msg}")
    L.append(f"    code={r.get('code')}  provider={d.get('provider')}  {d.get('responseTime')}ms")
    L.append(f"    答: {str(d.get('content', ''))[:100]}")
    L.append("")

# 增强对话
L.append("增强对话 /api/ai/chat/enhanced:")
r = req("/api/ai/chat/enhanced", "POST", {"message": "你好"}, token, timeout=60)
d = r.get("data") or {}
L.append(f"  code={r.get('code')}  provider={d.get('provider')}")
L.append(f"  答: {str(d.get('content', ''))[:80]}")
L.append("")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
