# -*- coding: utf-8 -*-
"""
最终全链路验证：前端定义的 99 个接口逐个真实调用。

策略：
  1. 登录一次，token 复用（跳过 logout —— 它会注销 token）
  2. 先创建依赖资源（设备、提醒、联系人、安全区、家庭成员）
  3. 再用真实 ID 调用依赖这些资源的接口
"""
import json
import random
import re
import sys
import time
import urllib.request
import urllib.error

BASE = "http://localhost:3000"
FE_API = r"D:\msr150174\项目\智能无感\转换-uniapp前端\guardian-uniapp\src\services\api.ts"
LOG = r"D:\msr150174\项目\智能无感\转换-Java后端\guardian-server\logs\fam2.log"
OUT = r"D:\msr150174\项目\智能无感\_e2e-final.txt"
PHONE = "131" + str(random.randint(10000000, 99999999))
PHONE2 = "132" + str(random.randint(10000000, 99999999))


def req(path, method="GET", body=None, token=None, timeout=45):
    url = BASE + path
    data = json.dumps(body).encode("utf-8") if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": e.code, "msg": "HTTP " + str(e.code)}
    except Exception as e:
        return {"code": -1, "msg": str(e)}


def find_code(phone):
    with open(LOG, encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()[-1500:]
    code = None
    for line in lines:
        if phone in line:
            m = re.search(r"验证码为\s*(\d{6})", line)
            if m:
                code = m.group(1)
    return code


def login(phone):
    req("/api/auth/send-code", "POST", {"phone": phone})
    time.sleep(3)
    code = find_code(phone)
    if not code:
        return None, {}
    r = req("/api/auth/login", "POST", {"phone": phone, "code": code})
    d = r.get("data") or {}
    return d.get("token"), d.get("user") or {}


L = []
L.append("=" * 76)
L.append("  最终全链路验证")
L.append("=" * 76)
L.append(f"  主账号: {PHONE}")
L.append(f"  副账号: {PHONE2}")
L.append("")

token, user = login(PHONE)
if not token:
    L.append("❌ 登录失败")
    with open(OUT, "w", encoding="utf-8") as f:
        f.write("\n".join(L))
    print("done")
    sys.exit(0)

uid = user.get("id", "")
L.append(f"✅ 主账号登录  {user.get('name')}  id={uid[:12]}...")

# 副账号（测家庭成员）
token2, user2 = login(PHONE2)
L.append(f"{'✅' if token2 else '⚠️'} 副账号登录  {user2.get('name', '失败')}")
L.append("")

# ============================================================================
# 第一步：创建依赖资源
# ============================================================================
L.append("─" * 76)
L.append("准备依赖资源")
L.append("─" * 76)

RES = {}

# 设备
r = req("/api/device/bind", "POST",
        {"deviceName": "测试手环", "deviceType": "band", "deviceMac": "AA:BB:CC:DD:EE:FF"}, token)
d = r.get("data") or {}
RES["deviceId"] = d.get("id") or d.get("deviceId")
L.append(f"  设备: {r.get('code')}  id={str(RES['deviceId'])[:12]}")

# 用药提醒（后端要 times 数组，不是 time 字符串）
r = req("/api/medicine/reminder", "POST",
        {"medicineName": "阿莫西林", "dosage": "2粒", "frequency": "daily",
         "times": ["08:00", "20:00"], "startDate": "2026-10-01"}, token)
d = r.get("data") or {}
RES["reminderId"] = d.get("id") or d.get("reminderId")
L.append(f"  提醒: {r.get('code')}  id={str(RES['reminderId'])[:12]}")

# 紧急联系人
r = req("/api/guardian/contacts/add", "POST",
        {"name": "张三", "phone": "13800000011", "relation": "儿子"}, token)
d = r.get("data") or {}
RES["contactId"] = (d.get("contact") or {}).get("id")
L.append(f"  联系人: {r.get('code')}  id={str(RES['contactId'])[:12]}")

# 安全区
r = req("/api/location/safe-zone", "POST",
        {"lat": 39.9, "lng": 116.4, "radius": 500, "name": "家"}, token)
d = r.get("data") or {}
RES["zoneId"] = d.get("id") or d.get("zoneId")
L.append(f"  安全区: {r.get('code')}  id={str(RES['zoneId'])[:12]}")

# 告警
r = req("/api/guardian/alerts/add", "POST",
        {"alertType": "test", "severity": "low", "title": "测试告警"}, token)
d = r.get("data") or {}
RES["alertId"] = d.get("id")
L.append(f"  告警: {r.get('code')}  id={str(RES['alertId'])[:12]}")

# 家庭绑定
r = req("/api/family/add", "POST", {"phone": PHONE2, "relation": "儿子"}, token)
d = r.get("data") or {}
RES["bindingId"] = d.get("bindingId")
L.append(f"  家庭绑定: {r.get('code')}  {d.get('message', '')}  id={str(RES['bindingId'])[:12]}")

L.append("")

# ============================================================================
# 第二步：读前端定义并逐个调用
# ============================================================================
with open(FE_API, encoding="utf-8") as f:
    src = f.read()

calls = []
for m in re.finditer(r"http\.(get|post|put|del)\s*(?:<[^>]*>)?\s*\(\s*[`'\"]([^`'\"\s]+)", src):
    method = m.group(1).upper().replace("DEL", "DELETE")
    path = m.group(2).strip()
    if path.startswith("/api"):
        calls.append((method, path))

seen, uniq = set(), []
for m, p in calls:
    k = (m, re.sub(r"\$\{[^}]+\}", "{}", p))
    if k not in seen:
        seen.add(k)
        uniq.append((m, p))

# 路径变量替换
def resolve(p):
    p = p.replace("${userId}", uid).replace("${id}", str(RES.get("reminderId") or "x"))
    p = p.replace("${deviceId}", str(RES.get("deviceId") or "x"))
    p = p.replace("${bindingId}", str(RES.get("bindingId") or "x"))
    p = re.sub(r"\{[^}]+\}", uid, p)
    return p

# 请求体
BODIES = {
    "/api/checkin/check": {"type": "sleep"},
    "/api/ai/chat": {"message": "你好"},
    "/api/ai/chat/enhanced": {"message": "你好"},
    "/api/ai/analyze": {},
    "/api/ai/tts": {"text": "你好"},
    "/api/ai/sleep-analysis": {"sleepHours": 7.5},
    "/api/guardian/sos": {"reason": "测试"},
    "/api/guardian/activity": {"type": "move"},
    "/api/guardian/alert/handle": {"id": RES.get("alertId"), "notes": "已处理"},
    "/api/guardian/contacts/update": {"id": RES.get("contactId"), "name": "张三丰"},
    "/api/guardian/contacts/remove": {"id": RES.get("contactId")},
    "/api/guardian/alerts/resolve": {"id": RES.get("alertId"), "notes": "ok"},
    "/api/guardian/family/bind": {"inviteCode": "TESTCODE"},
    "/api/monitor/text-analyze": {"text": "最近睡不好"},
    "/api/monitor/voice-analyze": {"text": "有点头晕"},
    "/api/monitor/image-analyze": {"description": "老人在客厅走动"},
    "/api/monitor/fall-detect": {"detected": False, "confidence": 10},
    "/api/monitor/analyze-frame": {"description": "测试帧"},
    "/api/diet/record": {"date": "2026-10-14", "mealType": "breakfast",
                         "mealTime": "08:00", "foods": ["粥"], "calories": 200},
    "/api/exercise/record": {"date": "2026-10-14", "exerciseType": "walk",
                             "duration": 30, "steps": 3000},
    "/api/medicine/record": {"medicineName": "阿莫西林", "status": "taken"},
    "/api/medicine/reminder": {"medicineName": "布洛芬", "dosage": "1粒", "times": ["20:00"], "startDate": "2026-10-01"},
    "/api/medicine/reminder/update": {"id": RES.get("reminderId"), "medicineName": "阿莫西林胶囊"},
    "/api/device/bind": {"deviceName": "测试手表", "deviceType": "watch"},
    "/api/device/connect": {"deviceId": RES.get("deviceId")},
    "/api/device/disconnect": {"deviceId": RES.get("deviceId")},
    "/api/device/status": {"deviceId": RES.get("deviceId")},
    "/api/family/add": {"phone": PHONE2, "relation": "女儿"},
    "/api/family/accept": {"id": RES.get("bindingId")},
    "/api/family/reject": {"id": RES.get("bindingId")},
    "/api/family/permission": {"id": RES.get("bindingId"), "level": "limited"},
    "/api/reminder/configs/create": {"medicineName": "维生素", "times": ["09:00"], "type": "medicine"},
    "/api/reminder/configs/update": {"id": RES.get("reminderId"), "time": "10:00"},
    "/api/reminder/toggle": {"id": RES.get("reminderId"), "enabled": True},
    "/api/location/safe-zone": {"lat": 39.9, "lng": 116.4, "radius": 300},
    "/api/location/current": {"lat": 39.9, "lng": 116.4},
    "/api/location/safe-zone-check": {"lat": 39.9, "lng": 116.4},
    "/api/feedback/submit": {"content": "测试反馈"},
    "/api/map/regeo": {"lat": 39.9, "lng": 116.4},
    "/api/map/geocode": {"address": "北京市朝阳区"},
    "/api/map/distance": {"lat1": 39.9, "lng1": 116.4, "lat2": 39.91, "lng2": 116.41},
    "/api/image/fall-detect": {"description": "老人躺在客厅地上"},
    "/api/werun/sync": {"steps": [{"date": "2026-10-14", "steps": 5000}]},
}

SKIP = (
    "/api/auth/send-code", "/api/auth/login", "/api/auth/register",
    "/api/auth/verify-code", "/api/auth/bind-family", "/api/auth/unbind-family",
    "/api/auth/generate-invite", "/api/auth/verify-invite",
    "/api/auth/logout",          # 会注销 token
    "/api/image/ocr", "/api/image/analyze",
    "/api/admin/",
)

ok = fail = skip = 0
results = []

for method, path in uniq:
    if any(path.startswith(p) for p in SKIP):
        skip += 1
        continue

    real = resolve(path)

    # ★ 有些 DELETE 接口用 @RequestParam 读参数（从查询串取），
    #   放在 body 里读不到，必须拼到 URL 上。
    QUERY_ID = {
        "/api/device/unbind": ("deviceId", RES.get("deviceId")),
        "/api/family/remove": ("bindingId", RES.get("bindingId")),
        "/api/location/safe-zone": ("id", RES.get("zoneId") or "test"),
    }
    body = None
    if method in ("POST", "PUT", "DELETE"):
        body = BODIES.get(path, {})

    if path in QUERY_ID and method == "DELETE":
        k, v = QUERY_ID[path]
        if v:
            real = real + ("&" if "?" in real else "?") + f"{k}={v}"
        body = None

    r = req(real, method, body, token)
    c = r.get("code")
    msg = str(r.get("msg", ""))

    if c == 200:
        ok += 1
        results.append(("OK", method, path, ""))
    else:
        fail += 1
        results.append(("FAIL", method, path, f"code={c} {msg[:42]}"))

L.append("─" * 76)
L.append(f"结果：通过 {ok} / 失败 {fail} / 跳过 {skip}    共 {len(uniq)}")
L.append("─" * 76)
L.append("")

if fail:
    L.append("❌ 失败清单：")
    for st, m, p, msg in results:
        if st == "FAIL":
            L.append(f"  {m:6} {p:52} {msg}")
    L.append("")

L.append("全部结果：")
for st, m, p, msg in results:
    L.append(f"  [{st:4}] {m:6} {p:52} {msg}")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L))
print("done")
