package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.*;
import com.guardian.mapper.*;
import com.guardian.service.AuthService;
import com.guardian.service.HealthScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 居家守护 · 补充接口
 *
 * 覆盖原 NestJS 项目 guardian 模块的其余路由：
 *   GET    /api/guardian/dashboard                    守护首页汇总
 *   GET    /api/guardian/status/{userId}              指定长辈状态
 *   GET    /api/guardian/risk/{userId}                风险评估
 *   GET    /api/guardian/contacts                     紧急联系人列表
 *   POST   /api/guardian/contacts/add                 添加联系人
 *   POST   /api/guardian/contacts/update              更新联系人
 *   POST   /api/guardian/contacts/remove              删除联系人
 *   GET    /api/guardian/emergency-contacts/{userId}  紧急联系人（按用户）
 *   POST   /api/guardian/emergency-contacts/add       添加紧急联系人
 *   DELETE /api/guardian/emergency-contacts/remove    删除紧急联系人
 *   GET    /api/guardian/family/{userId}              长辈的家属列表
 *   POST   /api/guardian/family/bind                  绑定家属
 *   DELETE /api/guardian/family/unbind                解绑家属
 *   GET    /api/guardian/sos/history/{userId}         SOS 历史
 *   POST   /api/guardian/activity                     活动上报
 *   POST   /api/guardian/alerts/add                   新增告警
 *   POST   /api/guardian/alerts/resolve               解决告警
 *
 * ★ 紧急联系人没有独立表，存在 Redis（key: guardian:contacts:{userId}），
 *   因为联系人是低频小数据，不值得为它加表。
 */
@Slf4j
@RestController
@RequestMapping("/guardian")
@RequiredArgsConstructor
public class GuardianExtraController {

    private final AlertMapper alertMapper;
    private final DeviceMapper deviceMapper;
    private final CheckinRecordMapper checkinMapper;
    private final FamilyBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final HealthScoreService healthScoreService;
    private final StringRedisTemplate redis;

    private static final String CONTACTS_KEY = "guardian:contacts:";
    private static final String ACTIVITY_KEY = "guardian:activity:";

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    private User me(HttpServletRequest req) {
        return authService.requireUser(token(req));
    }

    // ========================================================================
    // 汇总
    // ========================================================================

    /**
     * 守护首页汇总
     *
     * 一次返回家属需要的全部概览数据，减少前端请求次数。
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(HttpServletRequest req) {
        User user = me(req);

        // 该用户能看到的长辈：自己 + 已绑定且已接受的
        List<String> targetIds = new ArrayList<>();
        targetIds.add(user.getId());
        for (FamilyBinding b : bindingMapper.selectByFamily(user.getId())) {
            if ("accepted".equals(b.getStatus())) {
                targetIds.add(b.getElderId());
            }
        }

        List<Map<String, Object>> elders = new ArrayList<>();
        int totalUnhandled = 0;

        for (String uid : targetIds) {
            User u = userMapper.selectById(uid);
            if (u == null) continue;

            // 今日打卡
            CheckinRecord ck = checkinMapper.selectByUserAndDate(uid, LocalDate.now());
            String status = "unknown";
            if (ck != null) {
                if (ck.getWakeTime() != null) status = "awake";
                else if (ck.getSleepTime() != null) status = "sleeping";
            }

            // 未处理告警
            List<Alert> alerts = alertMapper.selectByUserAndRange(
                    uid, LocalDateTime.now().minusDays(7), LocalDateTime.now());
            long unhandled = alerts.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count();
            totalUnhandled += unhandled;

            // 健康分
            Integer score = null;
            try {
                score = healthScoreService.calculate(uid).getTotalScore();
            } catch (Exception e) {
                // 计算失败不影响汇总
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", uid);
            m.put("name", u.getName());
            m.put("avatar", u.getAvatar());
            m.put("role", uid.equals(user.getId()) ? "self" : "family");
            m.put("status", status);
            m.put("statusText", switch (status) {
                case "sleeping" -> "休息中";
                case "awake" -> "活动中";
                default -> "今日未打卡";
            });
            m.put("healthScore", score);
            m.put("unhandledAlerts", unhandled);
            m.put("safe", unhandled == 0);
            m.put("sleepTime", ck == null ? null : ck.getSleepTime());
            m.put("wakeTime", ck == null ? null : ck.getWakeTime());
            elders.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("elders", elders);
        data.put("elderCount", elders.size());
        data.put("totalUnhandledAlerts", totalUnhandled);
        data.put("onlineDevices", deviceMapper.selectByUser(user.getId()).stream()
                .filter(d -> "active".equals(d.getStatus())).count());
        return ApiResponse.ok(data);
    }

    /** 指定长辈状态（路径参数版，与 /status?userId= 等价） */
    @GetMapping("/status/{userId}")
    public ApiResponse<Map<String, Object>> statusByPath(HttpServletRequest req,
                                                         @PathVariable String userId) {
        me(req);
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }

        CheckinRecord ck = checkinMapper.selectByUserAndDate(userId, LocalDate.now());
        List<Alert> alerts = alertMapper.selectByUserAndRange(
                userId, LocalDateTime.now().minusDays(7), LocalDateTime.now());
        long unhandled = alerts.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count();

        String status = "unknown";
        if (ck != null) {
            if (ck.getWakeTime() != null) status = "awake";
            else if (ck.getSleepTime() != null) status = "sleeping";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("name", target.getName());
        data.put("status", status);
        data.put("statusText", switch (status) {
            case "sleeping" -> "休息中";
            case "awake" -> "活动中";
            default -> "今日未打卡";
        });
        data.put("sleepTime", ck == null ? null : ck.getSleepTime());
        data.put("wakeTime", ck == null ? null : ck.getWakeTime());
        data.put("sleepScore", ck == null ? null : ck.getSleepScore());
        data.put("unhandledAlerts", unhandled);
        data.put("safe", unhandled == 0);
        return ApiResponse.ok(data);
    }

    /**
     * 风险评估
     *
     * 综合打卡规律、告警数量、设备在线情况给出低/中/高风险。
     */
    @GetMapping("/risk/{userId}")
    public ApiResponse<Map<String, Object>> risk(HttpServletRequest req,
                                                 @PathVariable String userId) {
        me(req);
        User target = userMapper.selectById(userId);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }

        List<String> factors = new ArrayList<>();
        int riskScore = 0;   // 0-100，越高越危险

        // 1. 近期告警
        List<Alert> alerts = alertMapper.selectByUserAndRange(
                userId, LocalDateTime.now().minusDays(7), LocalDateTime.now());
        long unhandled = alerts.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count();
        long critical = alerts.stream().filter(a -> "critical".equals(a.getSeverity())).count();
        if (critical > 0) {
            riskScore += 40;
            factors.add("近 7 天有 " + critical + " 次紧急告警");
        } else if (unhandled > 0) {
            riskScore += 20;
            factors.add("有 " + unhandled + " 条未处理告警");
        }

        // 2. 打卡规律
        LocalDate today = LocalDate.now();
        int weekDays = checkinMapper.countDays(userId, today.minusDays(6), today);
        if (weekDays == 0) {
            riskScore += 30;
            factors.add("近 7 天没有打卡记录");
        } else if (weekDays < 4) {
            riskScore += 15;
            factors.add("近 7 天仅打卡 " + weekDays + " 天");
        }

        // 3. 设备在线
        List<Device> devices = deviceMapper.selectByUser(userId);
        long online = devices.stream().filter(d -> "active".equals(d.getStatus())).count();
        if (!devices.isEmpty() && online == 0) {
            riskScore += 20;
            factors.add("所有设备离线");
        }

        // 4. 健康分
        Integer health = null;
        try {
            health = healthScoreService.calculate(userId).getTotalScore();
            if (health < 60) {
                riskScore += 10;
                factors.add("健康评分偏低（" + health + " 分）");
            }
        } catch (Exception e) {
            // 忽略
        }

        riskScore = Math.min(100, riskScore);
        String level = riskScore >= 60 ? "high" : riskScore >= 30 ? "medium" : "low";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("name", target.getName());
        data.put("riskScore", riskScore);
        data.put("level", level);
        data.put("levelText", switch (level) {
            case "high" -> "高风险";
            case "medium" -> "需注意";
            default -> "状态良好";
        });
        data.put("factors", factors);
        data.put("healthScore", health);
        data.put("weekCheckinDays", weekDays);
        data.put("onlineDevices", online);
        data.put("suggestion", factors.isEmpty()
                ? "各项指标正常，继续保持"
                : "建议关注：" + String.join("；", factors));
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 紧急联系人（存 Redis）
    // ========================================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readContacts(String userId) {
        try {
            Object raw = redis.opsForHash().get(CONTACTS_KEY + userId, "list");
            if (raw == null) return new ArrayList<>();
            String json = String.valueOf(raw);
            if (json.isBlank() || "[]".equals(json)) return new ArrayList<>();

            // 极简 JSON 数组解析（避免引入额外的序列化依赖）
            List<Map<String, Object>> out = new ArrayList<>();
            for (String item : json.split("\\},\\s*\\{")) {
                Map<String, Object> m = new LinkedHashMap<>();
                String s = item.replace("[", "").replace("]", "").replace("{", "").replace("}", "");
                for (String kv : s.split(",")) {
                    String[] p = kv.split(":", 2);
                    if (p.length == 2) {
                        String k = p[0].trim().replace("\"", "");
                        String v = p[1].trim().replace("\"", "");
                        m.put(k, v);
                    }
                }
                if (!m.isEmpty()) out.add(m);
            }
            return out;
        } catch (Exception e) {
            log.warn("读取紧急联系人失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeContacts(String userId, List<Map<String, Object>> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("{");
            int j = 0;
            for (Map.Entry<String, Object> e : list.get(i).entrySet()) {
                if (j++ > 0) sb.append(",");
                sb.append("\"").append(e.getKey()).append("\":\"")
                  .append(String.valueOf(e.getValue()).replace("\"", "")).append("\"");
            }
            sb.append("}");
        }
        sb.append("]");
        redis.opsForHash().put(CONTACTS_KEY + userId, "list", sb.toString());
        redis.expire(CONTACTS_KEY + userId, 365, TimeUnit.DAYS);
    }

    /** 紧急联系人列表 */
    @GetMapping("/contacts")
    public ApiResponse<Map<String, Object>> contacts(HttpServletRequest req) {
        User user = me(req);
        List<Map<String, Object>> list = readContacts(user.getId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return ApiResponse.ok(data);
    }

    /** 按用户查紧急联系人 */
    @GetMapping("/emergency-contacts/{userId}")
    public ApiResponse<Map<String, Object>> emergencyContacts(HttpServletRequest req,
                                                              @PathVariable String userId) {
        me(req);
        List<Map<String, Object>> list = readContacts(userId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("list", list);
        data.put("total", list.size());
        return ApiResponse.ok(data);
    }

    /** 添加紧急联系人 */
    @PostMapping({"/contacts/add", "/emergency-contacts/add"})
    public ApiResponse<Map<String, Object>> addContact(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User user = me(req);

        String name = body == null ? null : str(body.get("name"));
        String phone = body == null ? null : str(body.get("phone"));
        if (name == null || name.isBlank()) {
            throw BizException.badRequest("请填写联系人姓名");
        }
        if (!CommonUtil.isPhone(phone)) {
            throw BizException.badRequest("手机号格式不正确");
        }

        List<Map<String, Object>> list = readContacts(user.getId());
        if (list.size() >= 10) {
            throw BizException.badRequest("最多添加 10 位紧急联系人");
        }

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", CommonUtil.uuid().substring(0, 8));
        c.put("name", name);
        c.put("phone", phone);
        c.put("relation", body.get("relation") == null ? "家属" : str(body.get("relation")));
        c.put("primary", list.isEmpty() ? "true" : "false");
        list.add(c);
        writeContacts(user.getId(), list);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contact", c);
        data.put("total", list.size());
        return ApiResponse.ok("联系人已添加", data);
    }

    /** 更新紧急联系人 */
    @PostMapping({"/contacts/update"})
    public ApiResponse<Map<String, Object>> updateContact(HttpServletRequest req,
                                                          @RequestBody Map<String, Object> body) {
        User user = me(req);
        String id = body == null ? null : str(body.get("id"));
        if (id == null) {
            throw BizException.badRequest("缺少联系人 ID");
        }

        List<Map<String, Object>> list = readContacts(user.getId());
        boolean found = false;
        for (Map<String, Object> c : list) {
            if (id.equals(c.get("id"))) {
                if (body.get("name") != null) c.put("name", str(body.get("name")));
                if (body.get("phone") != null) {
                    String p = str(body.get("phone"));
                    if (!CommonUtil.isPhone(p)) {
                        throw BizException.badRequest("手机号格式不正确");
                    }
                    c.put("phone", p);
                }
                if (body.get("relation") != null) c.put("relation", str(body.get("relation")));
                found = true;
                break;
            }
        }
        if (!found) {
            throw BizException.notFound("联系人不存在");
        }
        writeContacts(user.getId(), list);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return ApiResponse.ok("联系人已更新", data);
    }

    /** 删除紧急联系人 */
    @PostMapping({"/contacts/remove"})
    public ApiResponse<Map<String, Object>> removeContact(HttpServletRequest req,
                                                          @RequestBody Map<String, Object> body) {
        User user = me(req);
        String id = body == null ? null : str(body.get("id"));
        if (id == null) {
            throw BizException.badRequest("缺少联系人 ID");
        }

        List<Map<String, Object>> list = readContacts(user.getId());
        boolean removed = list.removeIf(c -> id.equals(c.get("id")));
        if (!removed) {
            throw BizException.notFound("联系人不存在");
        }
        writeContacts(user.getId(), list);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return ApiResponse.ok("联系人已删除", data);
    }

    /** 删除紧急联系人（DELETE 版） */
    @DeleteMapping("/emergency-contacts/remove")
    public ApiResponse<Map<String, Object>> removeContactByDelete(HttpServletRequest req,
                                                                  @RequestParam(required = false) String id) {
        Map<String, Object> body = new HashMap<>();
        body.put("id", id);
        return removeContact(req, body);
    }

    // ========================================================================
    // 家庭绑定
    // ========================================================================

    /** 某长辈的家属列表 */
    @GetMapping("/family/{userId}")
    public ApiResponse<Map<String, Object>> familyList(HttpServletRequest req,
                                                       @PathVariable String userId) {
        me(req);

        List<Map<String, Object>> list = new ArrayList<>();
        for (FamilyBinding b : bindingMapper.selectByElder(userId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bindingId", b.getId());
            m.put("userId", b.getFamilyId());
            m.put("status", b.getStatus());

            User fam = userMapper.selectById(b.getFamilyId());
            if (fam != null) {
                m.put("name", fam.getName());
                m.put("phone", CommonUtil.maskPhone(fam.getPhone()));
                m.put("avatar", fam.getAvatar());
            }
            m.put("createTime", b.getCreatedAt() == null ? null : b.getCreatedAt().toString());
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("elderId", userId);
        data.put("list", list);
        data.put("total", list.size());
        return ApiResponse.ok(data);
    }

    /** 绑定家属 */
    @PostMapping("/family/bind")
    public ApiResponse<Map<String, Object>> familyBind(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User user = me(req);

        String inviteCode = body == null ? null : str(body.get("inviteCode"));
        if (inviteCode == null || inviteCode.isBlank()) {
            throw BizException.badRequest("请填写邀请码");
        }

        FamilyBinding binding = bindingMapper.selectByInviteCode(inviteCode.trim().toUpperCase());
        if (binding == null) {
            throw BizException.notFound("邀请码无效或已过期");
        }
        if ("accepted".equals(binding.getStatus())) {
            throw BizException.badRequest("该邀请码已被使用");
        }
        if (binding.getElderId().equals(user.getId())) {
            throw BizException.badRequest("不能绑定自己");
        }

        bindingMapper.updateStatus(binding.getId(), "accepted");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bindingId", binding.getId());
        data.put("elderId", binding.getElderId());
        data.put("status", "accepted");
        return ApiResponse.ok("绑定成功", data);
    }

    /** 解绑家属 */
    @DeleteMapping("/family/unbind")
    public ApiResponse<Map<String, Object>> familyUnbind(HttpServletRequest req,
                                                         @RequestParam(required = false) String id,
                                                         @RequestParam(required = false) String familyId) {
        User user = me(req);

        String target = id;
        if (target == null && familyId != null) {
            // 按 familyId 找绑定关系
            for (FamilyBinding b : bindingMapper.selectByElder(user.getId())) {
                if (familyId.equals(b.getFamilyId())) {
                    target = b.getId();
                    break;
                }
            }
        }
        if (target == null) {
            throw BizException.badRequest("缺少绑定 ID 或家属 ID");
        }

        bindingMapper.deleteById(target);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        return ApiResponse.ok("已解除绑定", data);
    }

    // ========================================================================
    // SOS 历史 / 活动 / 告警
    // ========================================================================

    /** SOS 历史 */
    @GetMapping("/sos/history/{userId}")
    public ApiResponse<Map<String, Object>> sosHistory(HttpServletRequest req,
                                                       @PathVariable String userId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize) {
        me(req);

        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));

        List<Alert> all = alertMapper.selectByUserAndRange(
                userId, LocalDateTime.now().minusYears(1), LocalDateTime.now());

        List<Map<String, Object>> sosList = new ArrayList<>();
        for (Alert a : all) {
            if (!"sos".equals(a.getAlertType())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("title", a.getTitle());
            m.put("description", a.getDescription());
            m.put("severity", a.getSeverity());
            m.put("occurredAt", a.getOccurredAt() == null ? null : a.getOccurredAt().toString());
            m.put("handled", a.getHandled());
            m.put("handledAt", a.getHandledAt() == null ? null : a.getHandledAt().toString());
            sosList.add(m);
        }

        sosList.sort((x, y) -> String.valueOf(y.get("occurredAt")).compareTo(String.valueOf(x.get("occurredAt"))));

        int from = Math.min((p - 1) * size, sosList.size());
        int to = Math.min(from + size, sosList.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("list", sosList.subList(from, to));
        data.put("total", sosList.size());
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", to < sosList.size());
        return ApiResponse.ok(data);
    }

    /** 活动上报（前端定时上报，用于判断是否长时间未移动） */
    @PostMapping("/activity")
    public ApiResponse<Map<String, Object>> activity(HttpServletRequest req,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        User user = me(req);

        String type = body == null || body.get("type") == null ? "move" : str(body.get("type"));
        long now = System.currentTimeMillis();

        redis.opsForHash().put(ACTIVITY_KEY + user.getId(), "last", String.valueOf(now));
        redis.opsForHash().put(ACTIVITY_KEY + user.getId(), "type", type);
        redis.expire(ACTIVITY_KEY + user.getId(), 7, TimeUnit.DAYS);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", user.getId());
        data.put("type", type);
        data.put("reportedAt", now);
        return ApiResponse.ok(data);
    }

    /** 新增告警（路径别名，与 /alert/create 等价） */
    @PostMapping("/alerts/add")
    public ApiResponse<Map<String, Object>> alertsAdd(HttpServletRequest req,
                                                      @RequestBody Map<String, Object> body) {
        User user = me(req);

        Alert alert = new Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(body != null && body.get("userId") != null
                ? str(body.get("userId")) : user.getId());
        alert.setAlertType(body == null || body.get("alertType") == null
                ? "abnormal" : str(body.get("alertType")));
        alert.setSeverity(body == null || body.get("severity") == null
                ? "medium" : str(body.get("severity")));
        alert.setTitle(body == null || body.get("title") == null
                ? "健康提醒" : str(body.get("title")));
        alert.setDescription(body == null ? null : str(body.get("description")));
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);
        alertMapper.insert(alert);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", alert.getId());
        data.put("created", true);
        return ApiResponse.ok("告警已创建", data);
    }

    /** 解决告警（路径别名，与 /alert/handle 等价） */
    @PostMapping("/alerts/resolve")
    public ApiResponse<Map<String, Object>> alertsResolve(HttpServletRequest req,
                                                          @RequestBody Map<String, Object> body) {
        User user = me(req);

        String alertId = body == null ? null : str(body.get("id"));
        if (alertId == null || alertId.isBlank()) {
            throw BizException.badRequest("缺少告警 ID");
        }

        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警不存在");
        }
        if (Boolean.TRUE.equals(alert.getHandled())) {
            throw BizException.badRequest("该告警已处理过了");
        }

        alertMapper.updateHandled(alertId, user.getId(),
                body.get("notes") == null ? "已确认安全" : str(body.get("notes")));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", alertId);
        return ApiResponse.ok("已标记为处理完成", data);
    }

    // ---------------- 工具 ----------------

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
