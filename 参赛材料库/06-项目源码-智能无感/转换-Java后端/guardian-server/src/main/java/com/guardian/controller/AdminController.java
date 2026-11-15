package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.*;
import com.guardian.mapper.*;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 后台管理接口
 *
 * 所有接口都需要管理员身份（users.is_admin = 1），
 * 非管理员访问会返回 code=403。
 *
 * 模块：
 *   数据总览   /admin/dashboard          核心指标卡片
 *              /admin/trend            近 N 天趋势
 *   用户管理   /admin/users             用户列表（分页 + 搜索 + 筛选）
 *              /admin/users/{id}        用户详情
 *              /admin/users/set-admin   设为/取消管理员
 *              /admin/users/set-active  启用/禁用
 *   告警处置   /admin/alerts            告警列表
 *              /admin/alerts/handle     处理告警
 *   反馈处理   /admin/feedback          反馈列表
 *              /admin/feedback/reply    回复反馈
 *   设备管理   /admin/devices           设备列表
 *   系统监控   /admin/system            服务状态
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final AlertMapper alertMapper;
    private final DeviceMapper deviceMapper;
    private final CheckinRecordMapper checkinMapper;
    private final MedicineRecordMapper medicineRecordMapper;
    private final AuthService authService;
    private final StringRedisTemplate redis;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    private User admin(HttpServletRequest req) {
        return authService.requireAdmin(token(req));
    }

    // ========================================================================
    // 数据总览
    // ========================================================================

    /**
     * 核心指标
     *
     * 后台首页的卡片区：用户数、活跃度、告警、设备。
     */
    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard(HttpServletRequest req) {
        admin(req);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);

        long totalUsers = userMapper.countPage(null, null, null, null);
        long elders = userMapper.countByRole("elder");
        long families = userMapper.countByRole("family");
        long admins = userMapper.countByAdmin(true);
        long newThisWeek = userMapper.countSince(weekAgo);

        // 近 7 天告警
        List<Alert> recentAlerts = alertMapper.selectByUserAndRange("__ALL__", weekAgo, LocalDateTime.now());
        long weekAlerts = recentAlerts == null ? 0 : recentAlerts.size();

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalUsers", totalUsers);
        overview.put("elders", elders);
        overview.put("families", families);
        overview.put("admins", admins);
        overview.put("newThisWeek", newThisWeek);
        overview.put("weekAlerts", weekAlerts);

        // 今日打卡人数
        long todayCheckins = 0;
        try {
            todayCheckins = checkinMapper.countDays("__ALL__", LocalDate.now(), LocalDate.now());
        } catch (Exception e) {
            log.debug("统计今日打卡失败: {}", e.getMessage());
        }
        overview.put("todayCheckins", todayCheckins);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("overview", overview);
        data.put("generatedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    /**
     * 趋势数据（供 ECharts 折线图）
     *
     * GET /admin/trend?days=7
     */
    @GetMapping("/trend")
    public ApiResponse<Map<String, Object>> trend(HttpServletRequest req,
                                                  @RequestParam(defaultValue = "7") int days) {
        admin(req);

        int d = Math.max(1, Math.min(days, 90));
        List<String> dates = new ArrayList<>();
        List<Long> registrations = new ArrayList<>();
        List<Long> alerts = new ArrayList<>();

        LocalDate today = LocalDate.now();
        for (int i = d - 1; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            dates.add(day.toString());

            long reg = 0;
            try {
                reg = userMapper.countSince(day.atStartOfDay()) -
                        userMapper.countSince(day.plusDays(1).atStartOfDay());
            } catch (Exception e) {
                // 忽略单点统计失败
            }
            registrations.add(Math.max(0, reg));

            long al = 0;
            try {
                List<Alert> list = alertMapper.selectByUserAndRange(
                        "__ALL__", day.atStartOfDay(), day.plusDays(1).atStartOfDay());
                al = list == null ? 0 : list.size();
            } catch (Exception e) {
                // 同上
            }
            alerts.add(al);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dates", dates);
        data.put("registrations", registrations);
        data.put("alerts", alerts);
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 用户管理
    // ========================================================================

    /**
     * 用户列表
     *
     * GET /admin/users?keyword=&role=&isAdmin=&isActive=&page=1&pageSize=20
     */
    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> users(
            HttpServletRequest req,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isAdmin,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        admin(req);

        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));
        int offset = (p - 1) * size;

        List<User> list = userMapper.selectPage(keyword, role, isAdmin, isActive, offset, size);
        long total = userMapper.countPage(keyword, role, isAdmin, isActive);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (User u : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("phone", u.getPhone());        // 后台需要完整手机号便于联系
            m.put("name", u.getName());
            m.put("avatar", u.getAvatar());
            m.put("role", u.getRole());
            m.put("gender", u.getGender());
            m.put("isActive", u.getIsActive());
            m.put("isAdmin", Boolean.TRUE.equals(u.getIsAdmin()));
            m.put("createTime", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
            rows.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", total);
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", (long) offset + rows.size() < total);
        return ApiResponse.ok(data);
    }

    /** 用户详情 */
    @GetMapping("/users/{id}")
    public ApiResponse<Map<String, Object>> userDetail(HttpServletRequest req,
                                                       @PathVariable String id) {
        admin(req);

        User u = userMapper.selectById(id);
        if (u == null) {
            throw BizException.notFound("用户不存在");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", u.getId());
        data.put("phone", u.getPhone());
        data.put("name", u.getName());
        data.put("avatar", u.getAvatar());
        data.put("role", u.getRole());
        data.put("gender", u.getGender());
        data.put("birthDate", u.getBirthDate() == null ? null : u.getBirthDate().toString());
        data.put("address", u.getAddress());
        data.put("emergencyPhone", u.getEmergencyPhone());
        data.put("isActive", u.getIsActive());
        data.put("isAdmin", Boolean.TRUE.equals(u.getIsAdmin()));
        data.put("createTime", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
        data.put("updateTime", u.getUpdatedAt() == null ? null : u.getUpdatedAt().toString());

        // 打卡统计
        try {
            LocalDate today = LocalDate.now();
            data.put("checkinDays", checkinMapper.countDays(id, today.minusDays(30), today));
        } catch (Exception e) {
            data.put("checkinDays", 0);
        }

        // 设备列表（后台要展示具体设备，不只给个数）
        try {
            List<Device> ds = deviceMapper.selectByUser(id);
            if (ds == null) ds = new ArrayList<>();

            List<Map<String, Object>> deviceList = new ArrayList<>();
            for (Device d : ds) {
                Map<String, Object> dm = new LinkedHashMap<>();
                dm.put("id", d.getId());
                dm.put("deviceName", d.getDeviceName());
                dm.put("deviceType", d.getDeviceType());
                dm.put("deviceMac", d.getDeviceMac());
                dm.put("status", d.getStatus());
                // 后台把 active 直接翻成 ONLINE，前端不用再判断
                dm.put("statusText", "active".equals(d.getStatus()) ? "在线" : "离线");
                dm.put("lastSync", d.getLastSync() == null ? null : d.getLastSync().toString());
                dm.put("createTime", d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
                deviceList.add(dm);
            }
            data.put("devices", deviceList);
            data.put("deviceCount", deviceList.size());
        } catch (Exception e) {
            log.warn("查询用户 {} 的设备失败: {}", id, e.getMessage());
            data.put("devices", new ArrayList<>());
            data.put("deviceCount", 0);
        }

        // 未处理告警
        try {
            List<Alert> as = alertMapper.selectByUserAndRange(
                    id, LocalDateTime.now().minusDays(30), LocalDateTime.now());
            long unhandled = as == null ? 0 : as.stream()
                    .filter(a -> Boolean.FALSE.equals(a.getHandled())).count();
            data.put("unhandledAlerts", unhandled);
        } catch (Exception e) {
            data.put("unhandledAlerts", 0);
        }

        return ApiResponse.ok(data);
    }

    /**
     * 设为 / 取消管理员
     *
     * 请求体：{ "userId": "...", "isAdmin": true }
     */
    @PostMapping("/users/set-admin")
    public ApiResponse<Map<String, Object>> setAdmin(HttpServletRequest req,
                                                     @RequestBody Map<String, Object> body) {
        User me = admin(req);

        String userId = body == null ? null : str(body.get("userId"));
        if (userId == null || userId.isBlank()) {
            throw BizException.badRequest("缺少 userId");
        }
        if (userId.equals(me.getId()) && Boolean.FALSE.equals(asBool(body.get("isAdmin")))) {
            throw BizException.badRequest("不能取消自己的管理员权限");
        }

        User target = userMapper.selectById(userId);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }

        boolean flag = Boolean.TRUE.equals(asBool(body.get("isAdmin")));
        userMapper.updateAdmin(userId, flag);
        log.info("[后台] {} 将用户 {} 的管理员权限设为 {}", me.getName(), userId, flag);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("isAdmin", flag);
        return ApiResponse.ok(flag ? "已设为管理员" : "已取消管理员", data);
    }

    /**
     * 启用 / 禁用账号
     *
     * 请求体：{ "userId": "...", "isActive": false }
     */
    @PostMapping("/users/set-active")
    public ApiResponse<Map<String, Object>> setActive(HttpServletRequest req,
                                                      @RequestBody Map<String, Object> body) {
        User me = admin(req);

        String userId = body == null ? null : str(body.get("userId"));
        if (userId == null || userId.isBlank()) {
            throw BizException.badRequest("缺少 userId");
        }
        if (userId.equals(me.getId())) {
            throw BizException.badRequest("不能禁用自己的账号");
        }

        User target = userMapper.selectById(userId);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }

        boolean flag = !Boolean.FALSE.equals(asBool(body.get("isActive")));
        userMapper.updateActive(userId, flag);

        // 禁用时清掉其登录态，立即生效
        if (!flag) {
            try {
                Set<String> keys = redis.keys("guardian:token:*");
                if (keys != null) {
                    for (String k : keys) {
                        if (userId.equals(redis.opsForValue().get(k))) {
                            redis.delete(k);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("清理登录态失败: {}", e.getMessage());
            }
        }

        log.info("[后台] {} 将用户 {} 的启用状态设为 {}", me.getName(), userId, flag);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("isActive", flag);
        return ApiResponse.ok(flag ? "已启用" : "已禁用", data);
    }

    // ========================================================================
    // 告警处置
    // ========================================================================

    /**
     * 告警列表
     *
     * GET /admin/alerts?handled=false&page=1&pageSize=20
     */
    @GetMapping("/alerts")
    public ApiResponse<Map<String, Object>> alerts(
            HttpServletRequest req,
            @RequestParam(required = false) Boolean handled,
            @RequestParam(required = false) String severity,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        admin(req);

        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));

        List<Alert> all = alertMapper.selectByUserAndRange(
                "__ALL__", LocalDateTime.now().minusDays(90), LocalDateTime.now());
        if (all == null) {
            all = new ArrayList<>();
        }

        // 过滤
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Alert a : all) {
            if (handled != null && !handled.equals(a.getHandled())) continue;
            if (severity != null && !severity.isBlank() && !severity.equals(a.getSeverity())) continue;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("userId", a.getUserId());
            m.put("alertType", a.getAlertType());
            m.put("severity", a.getSeverity());
            m.put("title", a.getTitle());
            m.put("description", a.getDescription());
            m.put("occurredAt", a.getOccurredAt() == null ? null : a.getOccurredAt().toString());
            m.put("handled", a.getHandled());
            m.put("handledAt", a.getHandledAt() == null ? null : a.getHandledAt().toString());
            m.put("handledBy", a.getHandledBy());
            m.put("notes", a.getNotes());

            User u = userMapper.selectById(a.getUserId());
            if (u != null) {
                m.put("userName", u.getName());
                m.put("userPhone", u.getPhone());
            }
            filtered.add(m);
        }

        // 按时间倒序
        filtered.sort((x, y) -> String.valueOf(y.get("occurredAt"))
                .compareTo(String.valueOf(x.get("occurredAt"))));

        int from = Math.min((p - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", filtered.subList(from, to));
        data.put("total", filtered.size());
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", to < filtered.size());
        return ApiResponse.ok(data);
    }

    /**
     * 处理告警
     *
     * 请求体：{ "alertId": "...", "notes": "已联系家属确认安全" }
     */
    @PostMapping("/alerts/handle")
    public ApiResponse<Map<String, Object>> handleAlert(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User me = admin(req);

        String alertId = body == null ? null : str(body.get("alertId"));
        if (alertId == null || alertId.isBlank()) {
            throw BizException.badRequest("缺少 alertId");
        }

        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警不存在");
        }
        if (Boolean.TRUE.equals(alert.getHandled())) {
            throw BizException.badRequest("该告警已处理");
        }

        String notes = body.get("notes") == null ? "后台已处理" : str(body.get("notes"));
        alertMapper.updateHandled(alertId, me.getId(), notes);
        log.info("[后台] {} 处理了告警 {}", me.getName(), alertId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertId", alertId);
        data.put("handledBy", me.getId());
        data.put("handledAt", LocalDateTime.now().toString());
        return ApiResponse.ok("已标记为处理完成", data);
    }

    // ========================================================================
    // 反馈处理
    // ========================================================================

    /** 反馈列表（反馈存在 alerts 表，type='feedback'） */
    @GetMapping("/feedback")
    public ApiResponse<Map<String, Object>> feedback(
            HttpServletRequest req,
            @RequestParam(required = false) Boolean handled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {

        admin(req);

        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));

        List<Alert> all = alertMapper.selectByUserAndRange(
                "__ALL__", LocalDateTime.now().minusDays(365), LocalDateTime.now());
        if (all == null) {
            all = new ArrayList<>();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Alert a : all) {
            if (!"feedback".equals(a.getAlertType())) continue;

            // ★ 按处理状态筛选（之前 backend 不支持，前端只能自己过滤）
            if (handled != null && !handled.equals(Boolean.TRUE.equals(a.getHandled()))) {
                continue;
            }

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("userId", a.getUserId());
            m.put("title", a.getTitle());
            m.put("content", a.getDescription());
            m.put("severity", a.getSeverity());
            m.put("notes", a.getNotes());
            m.put("handled", Boolean.TRUE.equals(a.getHandled()));
            m.put("handledBy", a.getHandledBy());
            m.put("handledAt", a.getHandledAt() == null ? null : a.getHandledAt().toString());
            m.put("createTime", a.getOccurredAt() == null ? null : a.getOccurredAt().toString());

            User u = userMapper.selectById(a.getUserId());
            if (u != null) {
                m.put("userName", u.getName());
                m.put("userPhone", u.getPhone());
            }
            rows.add(m);
        }

        rows.sort((x, y) -> String.valueOf(y.get("createTime"))
                .compareTo(String.valueOf(x.get("createTime"))));

        int from = Math.min((p - 1) * size, rows.size());
        int to = Math.min(from + size, rows.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows.subList(from, to));
        data.put("total", rows.size());
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", to < rows.size());
        return ApiResponse.ok(data);
    }

    /**
     * 回复反馈
     *
     * 请求体：{ "feedbackId": "...", "reply": "已修复，感谢反馈" }
     */
    @PostMapping("/feedback/reply")
    public ApiResponse<Map<String, Object>> replyFeedback(HttpServletRequest req,
                                                          @RequestBody Map<String, Object> body) {
        User me = admin(req);

        String id = body == null ? null : str(body.get("feedbackId"));
        String reply = body == null ? null : str(body.get("reply"));
        if (id == null || id.isBlank()) {
            throw BizException.badRequest("缺少 feedbackId");
        }
        if (reply == null || reply.isBlank()) {
            throw BizException.badRequest("请输入回复内容");
        }

        Alert a = alertMapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("反馈不存在");
        }

        alertMapper.updateHandled(id, me.getId(), reply);
        log.info("[后台] {} 回复了反馈 {}", me.getName(), id);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("feedbackId", id);
        data.put("reply", reply);
        return ApiResponse.ok("已回复", data);
    }

    /**
     * 新增用户
     *
     * 请求体：{ "phone": "138...", "name": "张三", "role": "elder|family",
     *          "gender": "male|female", "birthDate": "1950-01-01",
     *          "address": "...", "emergencyPhone": "...", "isAdmin": false }
     *
     * ★ 手机号是用户身份的唯一标识（唯一索引），重复会被拒绝。
     *   建出来的账号没有验证码，用户首次用手机号登录即可自助激活。
     */
    @PostMapping("/users/create")
    public ApiResponse<Map<String, Object>> createUser(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User me = admin(req);

        String phone = body == null ? null : str(body.get("phone"));
        if (!CommonUtil.isPhone(phone)) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (userMapper.selectByPhone(phone) != null) {
            throw BizException.badRequest("该手机号已存在");
        }

        String role = body.get("role") == null ? "elder" : str(body.get("role")).toLowerCase();
        if (!"elder".equals(role) && !"family".equals(role)) {
            throw BizException.badRequest("角色只能是 elder 或 family");
        }

        User u = new User();
        u.setId(CommonUtil.uuid());
        u.setPhone(phone);
        u.setName(body.get("name") == null ? "未命名" : str(body.get("name")));
        u.setRole(role);
        u.setGender(body.get("gender") == null ? null : str(body.get("gender")));
        u.setAddress(body.get("address") == null ? null : str(body.get("address")));
        u.setEmergencyPhone(body.get("emergencyPhone") == null ? null : str(body.get("emergencyPhone")));
        u.setIsActive(true);
        u.setIsAdmin(Boolean.TRUE.equals(asBool(body.get("isAdmin"))));

        // 出生日期：前端传 yyyy-MM-dd 字符串
        String birth = body.get("birthDate") == null ? null : str(body.get("birthDate"));
        if (birth != null && !birth.isBlank()) {
            try {
                u.setBirthDate(LocalDate.parse(birth.trim()));
            } catch (Exception e) {
                throw BizException.badRequest("出生日期格式应为 yyyy-MM-dd");
            }
        }

        userMapper.insert(u);
        log.info("[后台] {} 新增用户 {}（{}）", me.getName(), phone, role);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", u.getId());
        data.put("phone", u.getPhone());
        data.put("name", u.getName());
        data.put("role", u.getRole());
        data.put("isAdmin", u.getIsAdmin());
        data.put("isActive", true);
        data.put("createTime", LocalDateTime.now().toString());
        data.put("tip", "用户首次用手机号登录即可激活");
        return ApiResponse.ok("用户已创建", data);
    }

    /**
     * 批量删除用户（软删：置为禁用）
     *
     * 请求体：{ "userIds": ["id1", "id2"] }
     *
     * ★ 用禁用而不是物理删除：用户关联着打卡、告警、设备等数据，
     *   物理删除会留下孤儿记录。禁用后该用户无法登录，效果上等同删除。
     */
    @PostMapping("/users/disable-batch")
    public ApiResponse<Map<String, Object>> disableBatch(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User me = admin(req);

        Object raw = body == null ? null : body.get("userIds");
        if (!(raw instanceof List<?> ids) || ids.isEmpty()) {
            throw BizException.badRequest("请提供要禁用的用户 ID 列表");
        }

        int ok = 0;
        int skipped = 0;
        for (Object o : ids) {
            String uid = String.valueOf(o);
            if (uid.equals(me.getId())) {
                skipped++;   // 不能禁用自己
                continue;
            }
            User target = userMapper.selectById(uid);
            if (target == null) {
                skipped++;
                continue;
            }
            userMapper.updateActive(uid, false);
            ok++;
        }

        log.info("[后台] {} 批量禁用用户：成功 {} 个，跳过 {} 个", me.getName(), ok, skipped);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disabled", ok);
        data.put("skipped", skipped);
        return ApiResponse.ok("已禁用 " + ok + " 个用户", data);
    }

    // ========================================================================
    // 设备管理
    // ========================================================================

    /** 设备列表 */
    @GetMapping("/devices")
    public ApiResponse<Map<String, Object>> devices(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId) {
        admin(req);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> targets = new ArrayList<>();

        if (userId != null && !userId.isBlank()) {
            targets.add(userId);
        } else {
            List<User> users = userMapper.selectPage(null, null, null, null, 0, 200);
            for (User u : users) {
                targets.add(u.getId());
            }
        }

        for (String uid : targets) {
            List<Device> ds = deviceMapper.selectByUser(uid);
            if (ds == null) continue;

            User u = userMapper.selectById(uid);
            for (Device d : ds) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", d.getId());
                m.put("userId", uid);
                m.put("userName", u == null ? null : u.getName());
                m.put("deviceName", d.getDeviceName());
                m.put("deviceType", d.getDeviceType());
                m.put("status", d.getStatus());
                m.put("lastSync", d.getLastSync() == null ? null : d.getLastSync().toString());
                m.put("createTime", d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
                rows.add(m);
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", rows);
        data.put("total", rows.size());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 系统监控
    // ========================================================================

    /** 服务状态（后台「系统监控」页用） */
    @GetMapping("/system")
    public ApiResponse<Map<String, Object>> system(HttpServletRequest req) {
        admin(req);

        Map<String, Object> data = new LinkedHashMap<>();

        // MySQL
        Map<String, Object> mysql = new LinkedHashMap<>();
        try {
            long users = userMapper.countPage(null, null, null, null);
            mysql.put("connected", true);
            mysql.put("users", users);
        } catch (Exception e) {
            mysql.put("connected", false);
            mysql.put("error", e.getMessage());
        }
        data.put("mysql", mysql);

        // Redis
        Map<String, Object> r = new LinkedHashMap<>();
        try {
            String pong = redis.getConnectionFactory() != null ? "ok" : "unknown";
            Set<String> keys = redis.keys("guardian:*");
            r.put("connected", true);
            r.put("keyCount", keys == null ? 0 : keys.size());
            r.put("status", pong);
        } catch (Exception e) {
            r.put("connected", false);
            r.put("error", e.getMessage());
        }
        data.put("redis", r);

        // 运行时
        Runtime rt = Runtime.getRuntime();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("javaVersion", System.getProperty("java.version"));
        jvm.put("maxMemoryMB", rt.maxMemory() / 1024 / 1024);
        jvm.put("totalMemoryMB", rt.totalMemory() / 1024 / 1024);
        jvm.put("freeMemoryMB", rt.freeMemory() / 1024 / 1024);
        jvm.put("usedMemoryMB", (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024);
        jvm.put("processors", rt.availableProcessors());
        jvm.put("uptimeSeconds", java.lang.management.ManagementFactory
                .getRuntimeMXBean().getUptime() / 1000);
        data.put("jvm", jvm);

        data.put("time", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    // ---------------- 工具 ----------------

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Boolean asBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        String s = String.valueOf(o).trim();
        if ("true".equalsIgnoreCase(s) || "1".equals(s)) return true;
        if ("false".equalsIgnoreCase(s) || "0".equals(s)) return false;
        return null;
    }
}
