package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.ViewUtil;
import com.guardian.entity.ExerciseRecord;
import com.guardian.entity.MonitorData;
import com.guardian.entity.User;
import com.guardian.mapper.ExerciseRecordMapper;
import com.guardian.mapper.MonitorDataMapper;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 微信运动接口
 *
 * 路由：
 *   POST /api/werun/today    上报并返回今日步数
 *   GET  /api/werun/steps    步数查询（范围）
 *   POST /api/werun/sync     同步微信运动数据（批量）
 *   POST /api/werun/decrypt  解密微信运动数据（兼容旧调用）
 *
 * ★ 步数复用 monitor_data 表（data_type='step'），
 *   同时写入 exercise_records（exercise_type='walk'），让运动统计与健康评分同步生效。
 *   「今日步数」按天缓存，避免高频查询打数据库。
 * ★ 微信 encryptData 解密需要小程序 session_key + AES-128-CBC，
 *   服务端没有小程序密钥（也不应持有），因此这里接收的是前端已解密后的明文数组。
 */
@Slf4j
@RestController
@RequestMapping("/werun")
@RequiredArgsConstructor
public class WeRunController {

    private final MonitorDataMapper monitorMapper;
    private final ExerciseRecordMapper exerciseMapper;
    private final AuthService authService;
    private final StringRedisTemplate redis;

    private static final String TYPE_STEP = "step";
    private static final String TODAY_KEY = "guardian:werun:today:";

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 今日步数
    // ========================================================================

    /**
     * 今日步数
     *
     * POST /api/werun/today
     * 请求体（可选）：{ "steps": 8600, "userId": "...", "date": "2026-01-01" }
     *
     * 传了 steps 就是上报（会落库），不传就是纯查询。
     */
    @PostMapping("/today")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> today(HttpServletRequest req,
                                                  @RequestBody(required = false) Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());
        LocalDate date = ViewUtil.toDate(body.get("date"));
        if (date == null) date = LocalDate.now();

        Integer steps = ViewUtil.toInt(body.get("steps"));

        int current;
        if (steps != null) {
            // ---------- 上报 ----------
            if (steps < 0 || steps > 200000) {
                throw BizException.badRequest("步数超出合理范围（0 - 200000）");
            }
            current = saveSteps(targetId, date, steps,
                    ViewUtil.toStr(body.get("deviceId")),
                    ViewUtil.toStr(body.get("source"), "werun"));
            log.info("用户 {} 上报 {} 步数：{}", targetId, date, steps);
        } else {
            // ---------- 查询 ----------
            current = querySteps(targetId, date);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("date", date.toString());
        data.put("steps", current);
        data.put("calories", estimateCalories(current));
        data.put("distance", ViewUtil.round(estimateDistance(current), 2));
        data.put("activeMinutes", estimateActiveMinutes(current));
        data.put("target", 6000);
        data.put("achieved", current >= 6000);
        data.put("progress", Math.min(100, (int) Math.round(current * 100.0 / 6000)));
        data.put("level", stepLevel(current));
        data.put("source", "werun");
        data.put("updateTime", LocalDateTime.now().toString());

        return ApiResponse.ok(steps != null ? "步数已更新" : "查询成功", data);
    }

    // ========================================================================
    // ② 步数查询（范围）
    // ========================================================================

    /**
     * 步数查询
     *
     * GET /api/werun/steps?userId=...&days=7
     *
     * 返回每天的总步数与区间汇总。
     */
    @GetMapping("/steps")
    public ApiResponse<Map<String, Object>> steps(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "7") int days) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        int d = ViewUtil.clamp(days, 1, 90);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1L);

        // 从 monitor_data 按天汇总
        List<Map<String, Object>> rows = monitorMapper.sumGroupByDate(
                targetId, TYPE_STEP, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        Map<String, Integer> byDate = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String key = ViewUtil.str(row.get("date"));
            if (key != null) {
                byDate.put(key.length() > 10 ? key.substring(0, 10) : key,
                        ViewUtil.toInt(row.get("total"), 0));
            }
        }

        // 缺失日期补 0
        List<Map<String, Object>> list = new ArrayList<>();
        long totalSteps = 0;
        int activeDays = 0;
        int maxSteps = 0;

        for (int i = 0; i < d; i++) {
            LocalDate cur = start.plusDays(i);
            int s = byDate.getOrDefault(cur.toString(), 0);
            totalSteps += s;
            if (s > 0) activeDays++;
            maxSteps = Math.max(maxSteps, s);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", cur.toString());
            item.put("steps", s);
            item.put("calories", estimateCalories(s));
            item.put("distance", ViewUtil.round(estimateDistance(s), 2));
            item.put("achieved", s >= 6000);
            item.put("level", stepLevel(s));
            list.add(item);
        }

        int avgSteps = (int) (totalSteps / d);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("from", start.toString());
        data.put("to", end.toString());
        data.put("days", d);
        data.put("list", list);
        data.put("totalSteps", totalSteps);
        data.put("avgSteps", avgSteps);
        data.put("maxSteps", maxSteps);
        data.put("activeDays", activeDays);
        data.put("targetDays", list.stream().filter(m -> Boolean.TRUE.equals(m.get("achieved"))).count());
        data.put("todaySteps", byDate.getOrDefault(end.toString(), 0));
        data.put("summary", String.format("近 %d 天共走 %d 步，日均 %d 步", d, totalSteps, avgSteps));
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ③ 同步微信运动
    // ========================================================================

    /**
     * 同步微信运动数据
     *
     * POST /api/werun/sync
     * 请求体：
     * {
     *   "userId": "...",                                  // 可选
     *   "steps": [                                        // 数组形式
     *     { "date": "2026-01-01", "steps": 8600 },
     *     { "date": "2026-01-02", "steps": 10200 }
     *   ]
     * }
     *
     * 也兼容 { "stepInfoList": [ { "timestamp": 1735689600, "step": 8600 } ] } 的微信原始结构。
     * 返回逐条同步结果，重复日期以最后一次为准（覆盖写）。
     */
    @PostMapping("/sync")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> sync(HttpServletRequest req,
                                                 @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) throw BizException.badRequest("请求体不能为空");

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());

        // 兼容三种字段名
        Object raw = body.get("steps");
        if (raw == null) raw = body.get("stepInfoList");
        if (raw == null) raw = body.get("list");

        if (!(raw instanceof List<?> items) || items.isEmpty()) {
            throw BizException.badRequest("请提供步数数组（steps / stepInfoList）");
        }
        if (items.size() > 90) {
            throw BizException.badRequest("单次最多同步 90 天数据");
        }

        String source = ViewUtil.toStr(body.get("source"), "werun");
        String deviceId = ViewUtil.toStr(body.get("deviceId"));

        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        int skipped = 0;
        long totalSteps = 0;

        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) {
                skipped++;
                continue;
            }

            // ---------- 解析日期：支持 date 字符串或 timestamp 秒 ----------
            LocalDate date = ViewUtil.toDate(m.get("date"));
            if (date == null) {
                Long ts = ViewUtil.toLong(m.get("timestamp"));
                if (ts != null) {
                    // 微信返回的是秒级时间戳
                    date = java.time.Instant.ofEpochSecond(ts)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                }
            }

            // ---------- 解析步数 ----------
            Integer s = ViewUtil.toInt(m.get("steps"));
            if (s == null) s = ViewUtil.toInt(m.get("step"));
            if (s == null) s = ViewUtil.toInt(m.get("value"));

            if (date == null || s == null) {
                skipped++;
                continue;
            }
            if (s < 0 || s > 200000) {
                skipped++;
                continue;
            }
            // 不接受未来日期
            if (date.isAfter(LocalDate.now())) {
                skipped++;
                continue;
            }

            try {
                int saved = saveSteps(targetId, date, s, deviceId, source);
                totalSteps += s;
                success++;

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("date", date.toString());
                r.put("steps", saved);
                r.put("ok", true);
                results.add(r);
            } catch (Exception e) {
                skipped++;
                log.warn("同步 {} 的步数失败: {}", date, e.getMessage());

                Map<String, Object> r = new LinkedHashMap<>();
                r.put("date", date.toString());
                r.put("ok", false);
                r.put("error", e.getMessage());
                results.add(r);
            }
        }

        log.info("用户 {} 同步微信运动：成功 {} 条，跳过 {} 条，共 {} 步",
                targetId, success, skipped, totalSteps);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("total", items.size());
        data.put("success", success);
        data.put("skipped", skipped);
        data.put("totalSteps", totalSteps);
        data.put("results", results);
        data.put("syncAt", LocalDateTime.now().toString());
        data.put("message", String.format("同步完成：成功 %d 条，跳过 %d 条", success, skipped));
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ④ 解密（兼容）
    // ========================================================================

    /**
     * 解密微信运动数据
     *
     * POST /api/werun/decrypt
     * 请求体：{ "encryptedData": "...", "iv": "...", "sessionKey": "..." }
     *
     * ★ 微信 encryptData 使用 AES-128-CBC（PKCS#7）解密，密钥来自小程序的 session_key。
     *   服务端不持有小程序密钥，也不应以明文接收 session_key，
     *   因此这里不做真实解密：若请求体已带 stepInfoList/明文数据则直接转发给同步逻辑，
     *   否则返回明确的状态说明，由前端在调用 wx.login + wx.getWeRunData 后自行解密再走 /sync。
     */
    @PostMapping("/decrypt")
    public ApiResponse<Map<String, Object>> decrypt(HttpServletRequest req,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        // 已经带了明文数据 → 直接当同步处理
        Object plain = body.get("stepInfoList");
        if (plain == null) plain = body.get("steps");
        if (plain == null) plain = body.get("data");

        if (plain != null) {
            log.info("decrypt 接口收到明文数据，直接走同步流程");
            return sync(req, body);
        }

        String encrypted = ViewUtil.toStr(body.get("encryptedData"));
        if (encrypted == null) {
            throw BizException.badRequest("请提供 encryptedData，或直接提供已解密的 steps / stepInfoList");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("decrypted", false);
        data.put("reason", "server-side-decrypt-unsupported");
        data.put("encryptedLength", encrypted.length());
        data.put("message", "服务端不持有小程序 session_key，无法解密微信加密数据。" +
                "请在小程序端调用 wx.getWeRunData 后，用 session_key 完成 AES-128-CBC 解密，" +
                "再把明文数组提交到 POST /api/werun/sync。");
        data.put("nextStep", "POST /api/werun/sync");
        data.put("hint", "明文结构示例：{ \"steps\": [ { \"date\": \"2026-01-01\", \"steps\": 8600 } ] }");

        return ApiResponse.ok("需要在小程序端完成解密", data);
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /**
     * 保存步数
     *
     * 同时写 monitor_data（细粒度时序）与 exercise_records（运动统计/健康评分）。
     * exercise_records 里同一天可能有多条，这里做「同天同类型只保留一条」的合并。
     */
    private int saveSteps(String userId, LocalDate date, int steps, String deviceId, String source) {
        // ---------- 写 monitor_data ----------
        MonitorData d = new MonitorData();
        d.setUserId(userId);
        d.setDeviceId(deviceId);
        d.setDataType(TYPE_STEP);
        d.setValue(BigDecimal.valueOf(steps));
        d.setUnit("步");
        // 用当天 23:59 作为记录时间，保证「当天最后一次上报」排在最前
        d.setRecordedAt(date.atTime(LocalTime.of(23, 59)));
        d.setMetadata(toStringJson(ViewUtil.map("source", source, "reportedAt", LocalDateTime.now().toString())));
        monitorMapper.insert(d);

        // ---------- 写 exercise_records（合并当天 walk 记录） ----------
        try {
            List<ExerciseRecord> sameDay = exerciseMapper.selectByUserAndDate(userId, date);
            ExerciseRecord walk = null;
            for (ExerciseRecord e : sameDay) {
                if ("walk".equals(e.getExerciseType())) {
                    walk = e;
                    break;
                }
            }

            if (walk == null) {
                ExerciseRecord e = new ExerciseRecord();
                e.setId(com.guardian.common.CommonUtil.uuid());
                e.setUserId(userId);
                e.setDate(date);
                e.setExerciseType("walk");
                e.setSteps(steps);
                e.setDuration(estimateActiveMinutes(steps));
                e.setCalories(estimateCalories(steps));
                e.setDistance(BigDecimal.valueOf(estimateDistance(steps)));
                e.setIntensity(steps >= 10000 ? "high" : steps >= 6000 ? "moderate" : "low");
                e.setNotes("微信运动同步");
                exerciseMapper.insert(e);
            }
            // 已存在则不重复插入：monitor_data 已记录最新值，运动统计以首次同步为准，
            // 避免同一天步数被反复叠加导致虚高。
        } catch (Exception e) {
            log.warn("写入运动记录失败（步数已存入监测数据）: {}", e.getMessage());
        }

        // ---------- 更新今日缓存 ----------
        try {
            redis.opsForValue().set(TODAY_KEY + userId + ":" + date, String.valueOf(steps), 7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.debug("步数缓存写入失败（忽略）: {}", e.getMessage());
        }

        return steps;
    }

    /** 查询某天步数：先看缓存，再查库 */
    private int querySteps(String userId, LocalDate date) {
        try {
            String cached = redis.opsForValue().get(TODAY_KEY + userId + ":" + date);
            Integer v = ViewUtil.toInt(cached);
            if (v != null) return v;
        } catch (Exception e) {
            log.debug("步数缓存读取失败（回退查库）: {}", e.getMessage());
        }

        try {
            MonitorData d = monitorMapper.selectLatestOne(userId, TYPE_STEP);
            if (d != null && d.getValue() != null
                    && d.getRecordedAt() != null
                    && d.getRecordedAt().toLocalDate().equals(date)) {
                return d.getValue().intValue();
            }
        } catch (Exception e) {
            log.debug("步数查询失败: {}", e.getMessage());
        }
        return 0;
    }

    /** 步数 → 估算热量（千卡）：约 0.04 kcal/步（60kg 成人慢走） */
    private int estimateCalories(int steps) {
        return (int) Math.round(steps * 0.04);
    }

    /** 步数 → 估算距离（公里）：约 0.65 米/步 */
    private double estimateDistance(int steps) {
        return steps * 0.65 / 1000.0;
    }

    /** 步数 → 估算有效活动时长（分钟）：约 100 步/分钟 */
    private int estimateActiveMinutes(int steps) {
        return Math.max(0, steps / 100);
    }

    private String stepLevel(int steps) {
        if (steps <= 0) return "无数据";
        if (steps >= 12000) return "非常活跃";
        if (steps >= 10000) return "运动充足";
        if (steps >= 6000) return "达标";
        if (steps >= 3000) return "偏少";
        return "明显不足";
    }

    /** 平铺 Map 转 JSON */
    private String toStringJson(Map<String, Object> m) {
        if (m == null) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) sb.append(v);
            else sb.append('"').append(String.valueOf(v).replace("\"", "\\\"")).append('"');
        }
        return sb.append('}').toString();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
