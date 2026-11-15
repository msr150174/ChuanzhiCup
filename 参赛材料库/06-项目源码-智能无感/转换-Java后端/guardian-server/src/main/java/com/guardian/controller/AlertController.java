package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.common.ViewUtil;
import com.guardian.entity.Alert;
import com.guardian.entity.FamilyBinding;
import com.guardian.entity.User;
import com.guardian.mapper.AlertMapper;
import com.guardian.mapper.FamilyBindingMapper;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 告警接口（/api/alert/* 路径族）
 *
 * 路由：
 *   GET  /api/alert/history   告警历史（分页，支持类型/严重程度/处理状态过滤）
 *   GET  /api/alert/detail    告警详情
 *   POST /api/alert/handle    处理告警
 *   POST /api/alert/create    创建告警
 *   GET  /api/alert/stats     告警统计
 *
 * ★ 与 GuardianController 的 /api/guardian/alerts 是两套独立路径，两者并存：
 *   - /api/guardian/* 面向「居家守护」页，聚合本人与绑定长辈的告警
 *   - /api/alert/*    面向「消息中心」页，按类型筛选与统计更细
 */
@Slf4j
@RestController
@RequestMapping("/alert")
@RequiredArgsConstructor
public class AlertController {

    private final AlertMapper alertMapper;
    private final FamilyBindingMapper bindingMapper;
    private final AuthService authService;

    /** 可选过滤的类型集合 */
    private static final Map<String, String> TYPE_LABELS = new LinkedHashMap<>();

    static {
        TYPE_LABELS.put("fall", "跌倒警报");
        TYPE_LABELS.put("sos", "紧急求助");
        TYPE_LABELS.put("abnormal", "健康异常");
        TYPE_LABELS.put("out_of_zone", "离开安全区");
        TYPE_LABELS.put("reminder_missed", "提醒未响应");
        TYPE_LABELS.put("feedback", "用户反馈");
        TYPE_LABELS.put("device", "设备异常");
    }

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 告警历史
    // ========================================================================

    /**
     * 告警历史
     *
     * GET /api/alert/history?userId=...&type=fall&severity=critical&handled=false&page=1&pageSize=20
     *
     * ★ 不传 userId 时默认查「本人 + 已绑定长辈」的告警（家属视角）。
     */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(required = false) String type,
                                                    @RequestParam(required = false) String severity,
                                                    @RequestParam(required = false) Boolean handled,
                                                    @RequestParam(required = false) String startDate,
                                                    @RequestParam(required = false) String endDate,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        User me = authService.requireUser(token(req));

        int p = ViewUtil.clamp(page, 1, 100000);
        int size = ViewUtil.clamp(pageSize, 1, 100);

        // 查询范围：指定用户 或 本人 + 已接受绑定的长辈
        List<String> userIds = targetUserIds(me, userId);

        List<String> types = blankToNull(type) == null ? null : List.of(type.trim());

        List<Alert> rows = alertMapper.selectPageByUsers(userIds, types, handled, (p - 1) * size, size);
        long total = alertMapper.countByUsers(userIds, types, handled);

        // 严重程度与日期区间在内存中过滤（这两项查询频次低，不值得再加一个 SQL 变体）
        List<Map<String, Object>> list = new ArrayList<>();
        LocalDateTime from = startDate == null ? null
                : ViewUtil.toDate(startDate) == null ? null : ViewUtil.toDate(startDate).atStartOfDay();
        LocalDateTime to = endDate == null ? null
                : ViewUtil.toDate(endDate) == null ? null : ViewUtil.toDate(endDate).plusDays(1).atStartOfDay();

        for (Alert a : rows) {
            if (blankToNull(severity) != null && !severity.equals(a.getSeverity())) continue;
            if (from != null && (a.getOccurredAt() == null || a.getOccurredAt().isBefore(from))) continue;
            if (to != null && (a.getOccurredAt() == null || !a.getOccurredAt().isBefore(to))) continue;
            list.add(toAlertView(a));
        }

        Map<String, Object> data = ViewUtil.page(list, total, p, size);
        data.put("userIds", userIds);
        data.put("filters", ViewUtil.map(
                "type", blankToNull(type),
                "severity", blankToNull(severity),
                "handled", handled,
                "startDate", blankToNull(startDate),
                "endDate", blankToNull(endDate)
        ));
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ② 告警详情
    // ========================================================================

    /**
     * 告警详情
     *
     * GET /api/alert/detail?id=...
     *
     * ★ 兼容按 userId 查询最新一条的旧调用方式。
     */
    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest req,
                                                   @RequestParam(required = false) String id,
                                                   @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));

        if (blankToNull(id) == null && blankToNull(userId) == null) {
            throw BizException.badRequest("请提供告警 ID（id）或用户 ID（userId）");
        }

        Alert alert;
        if (blankToNull(id) != null) {
            alert = alertMapper.selectById(id);
            if (alert == null) {
                throw BizException.notFound("告警不存在");
            }
        } else {
            // 取该用户最新一条
            List<Alert> rows = alertMapper.selectPage(userId, null, 0, 1);
            if (rows.isEmpty()) {
                throw BizException.notFound("该用户暂无告警记录");
            }
            alert = rows.get(0);
        }

        // 权限：本人或已绑定长辈
        List<String> allowed = targetUserIds(me, null);
        if (!allowed.contains(alert.getUserId())) {
            throw BizException.forbidden("无权查看该告警");
        }

        Map<String, Object> data = toAlertView(alert);
        data.put("canHandle", !Boolean.TRUE.equals(alert.getHandled()));

        // 处理人信息
        if (alert.getHandledBy() != null) {
            data.put("handledByMe", alert.getHandledBy().equals(me.getId()));
        }

        // 同类告警近 7 天次数，帮助判断是否反复发生
        List<Alert> same = alertMapper.selectByTypesAndRange(
                alert.getUserId(),
                alert.getAlertType() == null ? null : List.of(alert.getAlertType()),
                LocalDateTime.now().minusDays(7), LocalDateTime.now());
        data.put("sameTypeLast7Days", same.size());

        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ③ 处理告警
    // ========================================================================

    /**
     * 处理告警
     *
     * 请求体：{ "id": "...", "notes": "已电话确认", "status": "resolved" }
     *
     * status 支持 resolved（默认，已完成）/ ignored（忽略）/ processing（跟进中）。
     * 只有 resolved 才把 handled 置为 1；其余状态写入 notes 便于跟踪。
     */
    @PostMapping("/handle")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> handle(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String alertId = ViewUtil.toStr(body.get("id"));
        if (alertId == null) alertId = ViewUtil.toStr(body.get("alertId"));
        if (alertId == null) {
            throw BizException.badRequest("缺少告警 ID");
        }

        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警不存在");
        }

        List<String> allowed = targetUserIds(me, null);
        if (!allowed.contains(alert.getUserId())) {
            throw BizException.forbidden("无权处理该告警");
        }
        if (Boolean.TRUE.equals(alert.getHandled())) {
            throw BizException.badRequest("该告警已处理过了");
        }

        String notes = ViewUtil.toStr(body.get("notes"), "已处理");
        String status = ViewUtil.toStr(body.get("status"), "resolved");

        String mergedNotes = switch (status) {
            case "ignored" -> "[已忽略] " + notes;
            case "processing" -> "[跟进中] " + notes;
            default -> notes;
        };

        // 只有 resolved / ignored 才真正闭环
        if ("processing".equals(status)) {
            log.info("告警 {} 标记为跟进中（处理人 {}）：{}", alertId, me.getId(), notes);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", true);
            data.put("id", alertId);
            data.put("status", status);
            data.put("handled", false);
            data.put("notes", mergedNotes);
            return ApiResponse.ok("已标记为跟进中", data);
        }

        alertMapper.updateHandled(alertId, me.getId(), mergedNotes);
        log.info("告警 {} 已由 {} 处理，状态={}，备注={}", alertId, me.getId(), status, notes);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", alertId);
        data.put("status", status);
        data.put("handled", true);
        data.put("handledBy", me.getId());
        data.put("handledAt", LocalDateTime.now().toString());
        data.put("notes", mergedNotes);
        return ApiResponse.ok("ignored".equals(status) ? "告警已忽略" : "告警已处理", data);
    }

    // ========================================================================
    // ④ 创建告警
    // ========================================================================

    /**
     * 创建告警
     *
     * 请求体：
     * {
     *   "alertType": "fall",
     *   "severity": "high",
     *   "title": "跌倒警报",
     *   "description": "...",
     *   "userId": "...",              // 可选，默认自己
     *   "occurredAt": "2026-01-01 10:00:00"   // 可选，默认现在
     * }
     */
    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> create(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String type = ViewUtil.toStr(body.get("alertType"), "abnormal");
        String severity = normalizeSeverity(ViewUtil.toStr(body.get("severity"), "medium"));
        String title = ViewUtil.toStr(body.get("title"), TYPE_LABELS.getOrDefault(type, "健康异常"));
        String description = ViewUtil.toStr(body.get("description"));
        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());

        LocalDateTime occurredAt = ViewUtil.toDateTime(body.get("occurredAt"));
        if (occurredAt == null) occurredAt = LocalDateTime.now();

        Alert alert = new Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(targetId);
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setOccurredAt(occurredAt);
        alert.setHandled(false);
        alert.setNotes(ViewUtil.toStr(body.get("notes")));

        alertMapper.insert(alert);

        log.info("创建告警 {}：用户 {}，类型 {}，级别 {}", alert.getId(), targetId, type, severity);

        Map<String, Object> data = toAlertView(alert);
        data.put("created", true);
        return ApiResponse.ok("告警已创建", data);
    }

    // ========================================================================
    // ⑤ 告警统计
    // ========================================================================

    /**
     * 告警统计
     *
     * GET /api/alert/stats?userId=...&days=30
     *
     * 返回总数、未处理数、按类型/级别分布、按天趋势。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "30") int days) {
        User me = authService.requireUser(token(req));

        int d = ViewUtil.clamp(days, 1, 365);
        List<String> userIds = targetUserIds(me, userId);

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(d);

        // 汇总区间内所有相关告警
        List<Alert> all = new ArrayList<>();
        for (String uid : userIds) {
            all.addAll(alertMapper.selectByUserAndRange(uid, from, to));
        }

        long total = alertMapper.countByUsers(userIds, null, null);
        long unhandled = alertMapper.countByUsers(userIds, null, false);

        // ---------- 按类型 ----------
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, String> byTypeText = new LinkedHashMap<>();
        for (Alert a : all) {
            String t = a.getAlertType() == null ? "unknown" : a.getAlertType();
            byType.merge(t, 1L, Long::sum);
            byTypeText.putIfAbsent(t, TYPE_LABELS.getOrDefault(t, t));
        }

        // ---------- 按级别 ----------
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (String s : List.of("critical", "high", "medium", "low")) {
            bySeverity.put(s, all.stream().filter(a -> s.equals(a.getSeverity())).count());
        }

        // ---------- 按天趋势（缺失日期补 0） ----------
        Map<String, Long> byDateRaw = new LinkedHashMap<>();
        for (Alert a : all) {
            if (a.getOccurredAt() == null) continue;
            String key = a.getOccurredAt().toLocalDate().toString();
            byDateRaw.merge(key, 1L, Long::sum);
        }

        List<String> labels = new ArrayList<>();
        List<Long> trend = new ArrayList<>();
        LocalDate start = LocalDate.now().minusDays(d - 1L);
        for (int i = 0; i < d; i++) {
            LocalDate cur = start.plusDays(i);
            labels.add(cur.toString());
            trend.add(byDateRaw.getOrDefault(cur.toString(), 0L));
        }

        // ---------- 本周 / 本月 / 未处理 ----------
        LocalDateTime weekAgo = to.minusDays(7);
        LocalDateTime monthAgo = to.minusDays(30);
        long weekTotal = all.stream()
                .filter(a -> a.getOccurredAt() != null && !a.getOccurredAt().isBefore(weekAgo)).count();
        long monthTotal = all.stream()
                .filter(a -> a.getOccurredAt() != null && !a.getOccurredAt().isBefore(monthAgo)).count();

        // 处理率
        long handledInRange = all.stream().filter(a -> Boolean.TRUE.equals(a.getHandled())).count();
        int handledRate = all.isEmpty() ? 100 : (int) Math.round(handledInRange * 100.0 / all.size());

        // 最近一条未处理（用于前端红点提示）
        Alert latestPending = all.stream()
                .filter(a -> Boolean.FALSE.equals(a.getHandled()))
                .max(Comparator.comparing(Alert::getOccurredAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", blankToNull(userId));
        data.put("userIds", userIds);
        data.put("days", d);
        data.put("from", from.toString());
        data.put("to", to.toString());

        data.put("total", total);
        data.put("unhandled", unhandled);
        data.put("handled", total - unhandled);
        data.put("rangeTotal", all.size());
        data.put("handledRate", handledRate);

        data.put("weekTotal", weekTotal);
        data.put("monthTotal", monthTotal);

        data.put("byType", byType);
        data.put("byTypeText", byTypeText);
        data.put("bySeverity", bySeverity);

        data.put("trend", ViewUtil.map("labels", labels, "values", trend));

        data.put("latestPending", latestPending == null ? null : toAlertView(latestPending));
        data.put("generatedAt", to.toString());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /**
     * 计算查询范围
     *
     * 指定 userId 时只看该用户；否则返回「本人 + 所有已接受绑定关系的长辈」。
     */
    private List<String> targetUserIds(User me, String userId) {
        if (blankToNull(userId) != null) {
            return new ArrayList<>(List.of(userId));
        }

        List<String> ids = new ArrayList<>();
        ids.add(me.getId());

        for (FamilyBinding b : bindingMapper.selectByFamily(me.getId())) {
            if ("accepted".equals(b.getStatus()) && b.getElderId() != null) {
                if (!ids.contains(b.getElderId())) ids.add(b.getElderId());
            }
        }
        return ids;
    }

    /** 转告警视图（camelCase + 中文标签） */
    private Map<String, Object> toAlertView(Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("userId", a.getUserId());
        m.put("type", a.getAlertType());
        m.put("typeText", TYPE_LABELS.getOrDefault(
                a.getAlertType() == null ? "" : a.getAlertType(),
                a.getAlertType() == null ? "未知" : a.getAlertType()));
        m.put("severity", a.getSeverity());
        m.put("severityText", severityText(a.getSeverity()));
        m.put("title", a.getTitle());
        m.put("description", a.getDescription());
        m.put("occurredAt", ViewUtil.dateTimeStr(a.getOccurredAt()));
        m.put("handled", a.getHandled());
        m.put("handledBy", a.getHandledBy());
        m.put("handledAt", ViewUtil.dateTimeStr(a.getHandledAt()));
        m.put("notes", a.getNotes());
        m.put("createTime", ViewUtil.dateTimeStr(a.getCreatedAt()));
        m.put("status", Boolean.TRUE.equals(a.getHandled()) ? "handled" : "pending");
        return m;
    }

    private String severityText(String s) {
        if (s == null) return "提示";
        return switch (s) {
            case "critical" -> "紧急";
            case "high" -> "严重";
            case "medium" -> "注意";
            default -> "提示";
        };
    }

    private String normalizeSeverity(String s) {
        if (s == null) return "medium";
        return switch (s) {
            case "critical", "high", "medium", "low" -> s;
            default -> "medium";
        };
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
