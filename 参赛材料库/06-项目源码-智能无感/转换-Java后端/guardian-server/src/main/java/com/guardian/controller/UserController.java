package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.ViewUtil;
import com.guardian.entity.*;
import com.guardian.mapper.*;
import com.guardian.service.AuthService;
import com.guardian.service.HealthScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 用户接口
 *
 * 路由：
 *   GET  /api/user/profile    用户资料
 *   GET  /api/user/info       用户信息（与 profile 同源，便于前端兼容）
 *   POST /api/user/settings   更新用户设置
 *   GET  /api/user/stats      用户统计（打卡数 / 绑定家属数 / 记录数等）
 *   GET  /api/user/settings   读取用户设置
 */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;
    private final CheckinRecordMapper checkinMapper;
    private final ExerciseRecordMapper exerciseMapper;
    private final DietRecordMapper dietMapper;
    private final MedicineRecordMapper medicineRecordMapper;
    private final MedicineReminderMapper reminderMapper;
    private final AlertMapper alertMapper;
    private final FamilyBindingMapper bindingMapper;
    private final ElderInfoMapper elderInfoMapper;
    private final MonitorDataMapper monitorMapper;
    private final AuthService authService;
    private final HealthScoreService healthScoreService;
    private final StringRedisTemplate redis;

    private static final String SETTINGS_KEY = "guardian:user:settings:";

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 用户信息
    // ========================================================================

    /**
     * 用户资料
     *
     * GET /api/user/profile?userId=...
     *
     * 不传 userId 取自己；传了则取目标用户（家属可查看已绑定长辈）。
     */
    @GetMapping("/profile")
    public ApiResponse<Map<String, Object>> profile(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        User target = userMapper.selectById(uid);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }
        assertVisible(me, uid);

        Map<String, Object> data = authService.toUserView(target);
        data.put("isSelf", uid.equals(me.getId()));

        // 健康档案
        ElderInfo info = elderInfoMapper.selectByUser(uid);
        data.put("elderInfo", toElderInfoView(info));

        // 每日健康评分，方便资料页直接展示
        try {
            HealthScoreService.HealthScore s = healthScoreService.calculate(uid);
            data.put("healthScore", s.getTotalScore());
            data.put("healthLevel", s.getLevel());
        } catch (Exception e) {
            log.debug("资料页健康评分计算失败: {}", e.getMessage());
            data.put("healthScore", null);
            data.put("healthLevel", null);
        }

        return ApiResponse.ok(data);
    }

    /** 用户信息（与 profile 同源） */
    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info(HttpServletRequest req,
                                                 @RequestParam(required = false) String userId) {
        return profile(req, userId);
    }

    // ========================================================================
    // ② 更新资料 / 设置
    // ========================================================================

    /**
     * 更新用户设置
     *
     * POST /api/user/settings
     *
     * 请求体字段均可选：
     * {
     *   "name": "张爷爷", "avatar": "url", "gender": "male",
     *   "birthDate": "1950-01-01", "address": "...", "emergencyPhone": "13800000001",
     *   // 健康档案
     *   "bloodType": "A", "height": 170, "weight": 65,
     *   "chronicDiseases": ["高血压"], "allergies": ["海鲜"],
     *   "wakeTimeTarget": "06:30", "sleepTimeTarget": "22:00",
     *   "exerciseFrequency": "daily", "smoking": false, "drinking": false,
     *   // 应用设置
     *   "fontSize": "large", "voiceEnabled": true, "theme": "light"
     * }
     */
    @PostMapping("/settings")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> updateSettings(HttpServletRequest req,
                                                           @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) throw BizException.badRequest("请求体不能为空");

        // ---------- 基础资料 ----------
        boolean profileChanged = body.containsKey("name") || body.containsKey("avatar")
                || body.containsKey("gender") || body.containsKey("birthDate")
                || body.containsKey("address") || body.containsKey("emergencyPhone");

        if (profileChanged) {
            User patch = new User();
            patch.setId(me.getId());

            if (body.containsKey("name")) {
                String name = ViewUtil.toStr(body.get("name"));
                if (name == null || name.length() > 128) {
                    throw BizException.badRequest("姓名不能为空且不超过 128 字");
                }
                patch.setName(name);
            }
            if (body.containsKey("avatar")) patch.setAvatar(ViewUtil.toStr(body.get("avatar")));
            if (body.containsKey("gender")) {
                String g = ViewUtil.toStr(body.get("gender"));
                if (g != null && !List.of("male", "female", "other").contains(g)) {
                    throw BizException.badRequest("性别取值应为 male / female / other");
                }
                patch.setGender(g);
            }
            if (body.containsKey("birthDate")) {
                LocalDate bd = ViewUtil.toDate(body.get("birthDate"));
                if (bd != null && bd.isAfter(LocalDate.now())) {
                    throw BizException.badRequest("出生日期不能晚于今天");
                }
                patch.setBirthDate(bd);
            }
            if (body.containsKey("address")) patch.setAddress(ViewUtil.toStr(body.get("address")));

            if (body.containsKey("emergencyPhone")) {
                String phone = ViewUtil.toStr(body.get("emergencyPhone"));
                if (phone != null && !phone.isBlank()
                        && !com.guardian.common.CommonUtil.isPhone(phone)) {
                    throw BizException.badRequest("紧急联系电话格式不正确");
                }
                patch.setEmergencyPhone(phone);
            }

            userMapper.update(patch);
        }

        // ---------- 健康档案 ----------
        boolean elderChanged = body.containsKey("bloodType") || body.containsKey("height")
                || body.containsKey("weight") || body.containsKey("chronicDiseases")
                || body.containsKey("allergies") || body.containsKey("medications")
                || body.containsKey("wakeTimeTarget") || body.containsKey("sleepTimeTarget")
                || body.containsKey("exerciseFrequency") || body.containsKey("dietPreference")
                || body.containsKey("smoking") || body.containsKey("drinking");

        if (elderChanged) {
            ElderInfo exist = elderInfoMapper.selectByUser(me.getId());

            if (exist == null) {
                ElderInfo e = new ElderInfo();
                e.setId(com.guardian.common.CommonUtil.uuid());
                e.setUserId(me.getId());
                applyElderPatch(e, body);
                elderInfoMapper.insert(e);
            } else {
                ElderInfo e = new ElderInfo();
                e.setUserId(me.getId());
                applyElderPatch(e, body);
                elderInfoMapper.update(e);
            }
        }

        // ---------- 应用设置（Redis） ----------
        Map<String, Object> appSettings = readAppSettings(me.getId());
        boolean appChanged = false;
        for (String k : List.of("fontSize", "voiceEnabled", "theme", "notificationEnabled",
                "language", "reminderSound", "reminderVibrate", "autoPlayVoice")) {
            if (body.containsKey(k)) {
                appSettings.put(k, body.get(k));
                appChanged = true;
            }
        }
        if (appChanged) {
            appSettings.put("updatedAt", LocalDateTime.now().toString());
            writeAppSettings(me.getId(), appSettings);
        }

        log.info("用户 {} 更新设置：资料={}，健康档案={}，应用设置={}",
                me.getId(), profileChanged, elderChanged, appChanged);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("profileChanged", profileChanged);
        data.put("elderInfoChanged", elderChanged);
        data.put("appSettingsChanged", appChanged);
        data.put("user", authService.toUserView(userMapper.selectById(me.getId())));
        data.put("elderInfo", toElderInfoView(elderInfoMapper.selectByUser(me.getId())));
        data.put("appSettings", appSettings);

        return ApiResponse.ok("设置已保存", data);
    }

    /** 读取用户应用设置 */
    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> getSettings(HttpServletRequest req) {
        User me = authService.requireUser(token(req));

        Map<String, Object> data = readAppSettings(me.getId());
        data.put("userId", me.getId());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ③ 用户统计
    // ========================================================================

    /**
     * 用户统计
     *
     * GET /api/user/stats
     *
     * 打卡总数 / 连续天数 / 绑定的家属数 / 各类记录数 / 健康评分。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        User target = userMapper.selectById(uid);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }
        assertVisible(me, uid);

        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);
        LocalDate monthAgo = today.minusDays(29);
        LocalDateTime monthStart = monthAgo.atStartOfDay();

        // ---------- 打卡 ----------
        List<LocalDate> recentDates = checkinMapper.selectRecentDates(uid);
        int totalCheckins = recentDates.size();
        int weekCheckins = checkinMapper.countDays(uid, weekAgo, today);
        int monthCheckins = checkinMapper.countDays(uid, monthAgo, today);
        int continuous = calcContinuous(recentDates, today);

        Map<String, Object> checkinStats = new LinkedHashMap<>();
        checkinStats.put("totalDays", totalCheckins);
        checkinStats.put("weekDays", weekCheckins);
        checkinStats.put("monthDays", monthCheckins);
        checkinStats.put("continuousDays", continuous);
        checkinStats.put("checkedToday", recentDates.contains(today));

        // ---------- 家属绑定 ----------
        List<FamilyBinding> asElder = bindingMapper.selectByElder(uid);
        List<FamilyBinding> asFamily = bindingMapper.selectByFamily(uid);

        long acceptedFamily = asElder.stream().filter(b -> "accepted".equals(b.getStatus())).count();
        long acceptedElders = asFamily.stream().filter(b -> "accepted".equals(b.getStatus())).count();

        Map<String, Object> familyStats = new LinkedHashMap<>();
        familyStats.put("familyCount", acceptedFamily);          // 绑定了多少家属
        familyStats.put("elderCount", acceptedElders);           // 作为家属绑定了多少长辈
        familyStats.put("pendingCount",
                asElder.stream().filter(b -> "pending".equals(b.getStatus())).count()
              + asFamily.stream().filter(b -> "pending".equals(b.getStatus())).count());
        familyStats.put("hasEmergencyPhone", target.getEmergencyPhone() != null);

        // ---------- 业务记录数 ----------
        int exerciseCount = exerciseMapper.selectByUserAndRange(uid, weekAgo, today).size();
        int dietCount = dietMapper.selectByUserAndRange(uid, weekAgo, today).size();
        int medicineCount = medicineRecordMapper.selectByUserAndRange(uid, weekAgo, today).size();
        int reminderCount = reminderMapper.selectByUser(uid, true).size();
        long monitorCount = monitorMapper.countByUserAndType(uid, null);

        List<Alert> monthAlerts = alertMapper.selectByUserAndRange(uid, monthStart, LocalDateTime.now());

        Map<String, Object> recordStats = new LinkedHashMap<>();
        recordStats.put("exerciseThisWeek", exerciseCount);
        recordStats.put("dietThisWeek", dietCount);
        recordStats.put("medicineThisWeek", medicineCount);
        recordStats.put("activeReminders", reminderCount);
        recordStats.put("monitorDataCount", monitorCount);
        recordStats.put("alertsThisMonth", monthAlerts.size());
        recordStats.put("unhandledAlerts",
                monthAlerts.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count());

        // ---------- 健康评分 ----------
        Integer totalScore = null;
        String level = null;
        try {
            HealthScoreService.HealthScore s = healthScoreService.calculate(uid);
            totalScore = s.getTotalScore();
            level = s.getLevel();
        } catch (Exception e) {
            log.debug("用户统计：健康评分计算失败 {}", e.getMessage());
        }

        // ---------- 活跃度评分（简单合成，用于个人页进度条） ----------
        int activityScore = 0;
        if (weekCheckins > 0) activityScore += Math.min(40, weekCheckins * 6);
        if (exerciseCount > 0) activityScore += Math.min(30, exerciseCount * 5);
        if (dietCount > 0) activityScore += Math.min(20, dietCount * 2);
        if (medicineCount > 0) activityScore += Math.min(10, medicineCount * 2);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", uid);
        data.put("name", target.getName());
        data.put("role", target.getRole());
        data.put("registerDays", target.getCreatedAt() == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(
                        target.getCreatedAt().toLocalDate(), today));
        data.put("checkin", checkinStats);
        data.put("family", familyStats);
        data.put("records", recordStats);
        data.put("healthScore", totalScore);
        data.put("healthLevel", level);
        data.put("activityScore", ViewUtil.clamp(activityScore, 0, 100));
        data.put("generatedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 把请求体里的健康档案字段填进实体 */
    private void applyElderPatch(ElderInfo e, Map<String, Object> body) {
        if (body.containsKey("bloodType")) e.setBloodType(ViewUtil.toStr(body.get("bloodType")));

        if (body.containsKey("height")) {
            java.math.BigDecimal h = ViewUtil.toDecimal(body.get("height"));
            if (h != null && (h.doubleValue() < 50 || h.doubleValue() > 250)) {
                throw BizException.badRequest("身高需在 50 - 250 cm 之间");
            }
            e.setHeight(h);
        }
        if (body.containsKey("weight")) {
            java.math.BigDecimal w = ViewUtil.toDecimal(body.get("weight"));
            if (w != null && (w.doubleValue() < 20 || w.doubleValue() > 300)) {
                throw BizException.badRequest("体重需在 20 - 300 kg 之间");
            }
            e.setWeight(w);
        }

        if (body.containsKey("chronicDiseases")) e.setChronicDiseases(toJsonArray(body.get("chronicDiseases")));
        if (body.containsKey("allergies")) e.setAllergies(toJsonArray(body.get("allergies")));
        if (body.containsKey("medications")) e.setMedications(toJsonArray(body.get("medications")));
        if (body.containsKey("dietPreference")) e.setDietPreference(toJsonArray(body.get("dietPreference")));

        if (body.containsKey("wakeTimeTarget")) e.setWakeTimeTarget(ViewUtil.toStr(body.get("wakeTimeTarget")));
        if (body.containsKey("sleepTimeTarget")) e.setSleepTimeTarget(ViewUtil.toStr(body.get("sleepTimeTarget")));
        if (body.containsKey("exerciseFrequency")) e.setExerciseFrequency(ViewUtil.toStr(body.get("exerciseFrequency")));
        if (body.containsKey("smoking")) e.setSmoking(ViewUtil.toBool(body.get("smoking"), false));
        if (body.containsKey("drinking")) e.setDrinking(ViewUtil.toBool(body.get("drinking"), false));
    }

    /** 健康档案视图 */
    private Map<String, Object> toElderInfoView(ElderInfo e) {
        if (e == null) return null;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("userId", e.getUserId());
        m.put("bloodType", e.getBloodType());
        m.put("height", e.getHeight());
        m.put("weight", e.getWeight());
        m.put("chronicDiseases", parseJsonArray(e.getChronicDiseases()));
        m.put("allergies", parseJsonArray(e.getAllergies()));
        m.put("medications", parseJsonArray(e.getMedications()));
        m.put("wakeTimeTarget", e.getWakeTimeTarget());
        m.put("sleepTimeTarget", e.getSleepTimeTarget());
        m.put("exerciseFrequency", e.getExerciseFrequency());
        m.put("dietPreference", parseJsonArray(e.getDietPreference()));
        m.put("smoking", e.getSmoking());
        m.put("drinking", e.getDrinking());
        m.put("updateTime", ViewUtil.dateTimeStr(e.getUpdatedAt()));

        // BMI 顺带算出来
        if (e.getHeight() != null && e.getWeight() != null
                && e.getHeight().doubleValue() > 0) {
            double hm = e.getHeight().doubleValue() / 100.0;
            double bmi = e.getWeight().doubleValue() / (hm * hm);
            m.put("bmi", ViewUtil.round(bmi, 1));
            m.put("bmiLevel", bmi < 18.5 ? "偏瘦" : bmi < 24 ? "正常" : bmi < 28 ? "偏胖" : "肥胖");
        } else {
            m.put("bmi", null);
            m.put("bmiLevel", null);
        }
        return m;
    }

    /** 只能看自己，或家属看已接受绑定的长辈 */
    private void assertVisible(User me, String targetId) {
        if (me.getId().equals(targetId)) return;

        for (FamilyBinding b : bindingMapper.selectByFamily(me.getId())) {
            if ("accepted".equals(b.getStatus()) && targetId.equals(b.getElderId())) {
                return;
            }
        }
        for (FamilyBinding b : bindingMapper.selectByElder(me.getId())) {
            if ("accepted".equals(b.getStatus()) && targetId.equals(b.getFamilyId())) {
                return;
            }
        }
        throw BizException.forbidden("无权查看该用户的信息");
    }

    private int calcContinuous(List<LocalDate> dates, LocalDate today) {
        if (dates == null || dates.isEmpty()) return 0;
        Set<LocalDate> set = new HashSet<>(dates);
        LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
        if (!set.contains(cursor)) return 0;

        int count = 0;
        while (set.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    // ---------------- 应用设置（Redis） ----------------

    private Map<String, Object> readAppSettings(String userId) {
        Map<String, Object> def = defaultAppSettings();
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(SETTINGS_KEY + userId);
            if (raw == null || raw.isEmpty()) return def;
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object dv = def.get(k);
                String v = String.valueOf(e.getValue());
                if (dv instanceof Boolean) def.put(k, ViewUtil.toBool(v, true));
                else def.put(k, v);
            }
        } catch (Exception e) {
            log.warn("用户设置读取失败（Redis 不可用，返回默认值）: {}", e.getMessage());
        }
        return def;
    }

    private void writeAppSettings(String userId, Map<String, Object> settings) {
        try {
            Map<String, String> flat = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : settings.entrySet()) {
                if (e.getValue() != null) flat.put(e.getKey(), String.valueOf(e.getValue()));
            }
            redis.opsForHash().putAll(SETTINGS_KEY + userId, flat);
        } catch (Exception e) {
            log.warn("用户设置写入失败（Redis 不可用）: {}", e.getMessage());
        }
    }

    private Map<String, Object> defaultAppSettings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fontSize", "normal");        // small / normal / large / xlarge
        m.put("theme", "light");            // light / dark / auto
        m.put("language", "zh-CN");
        m.put("voiceEnabled", true);
        m.put("autoPlayVoice", false);
        m.put("notificationEnabled", true);
        m.put("reminderSound", true);
        m.put("reminderVibrate", true);
        m.put("updatedAt", null);
        return m;
    }

    // ---------------- JSON 数组处理 ----------------

    /** 任意对象转 JSON 数组字符串（支持 List 与逗号分隔字符串） */
    private String toJsonArray(Object o) {
        List<String> list = ViewUtil.toStrList(o);
        if (list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    private List<String> parseJsonArray(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        String s = json.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);

        for (String part : s.split(",")) {
            String v = part.trim();
            if (v.startsWith("\"")) v = v.substring(1);
            if (v.endsWith("\"")) v = v.substring(0, v.length() - 1);
            v = v.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
