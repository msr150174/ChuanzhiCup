package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.common.ViewUtil;
import com.guardian.entity.Alert;
import com.guardian.entity.User;
import com.guardian.mapper.AlertMapper;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 用户反馈接口
 *
 * 路由：
 *   POST /api/feedback/submit    提交反馈
 *   GET  /api/feedback/history   我的反馈列表
 *   GET  /api/feedback/faq       常见问题
 *
 * ★ 数据库没有 feedback 表，反馈记录写入 alerts 表：
 *   alert_type = 'feedback'，severity = 'low'（用户评价可升为 medium/high）。
 *   这样家属端与告警列表天然可见，无需改表结构。
 *   反馈正文存 description，分类/联系方式/评分打包进 notes。
 */
@Slf4j
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final AlertMapper alertMapper;
    private final AuthService authService;

    /** 反馈分类 */
    private static final Map<String, String> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("bug", "功能异常");
        CATEGORIES.put("suggestion", "功能建议");
        CATEGORIES.put("experience", "使用体验");
        CATEGORIES.put("content", "内容纠错");
        CATEGORIES.put("other", "其他");
    }

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 提交反馈
    // ========================================================================

    /**
     * 提交反馈
     *
     * 请求体：
     * {
     *   "content": "希望增加血压记录功能",   // 必填
     *   "category": "suggestion",           // 可选，默认 other
     *   "contact": "13800000001",           // 可选，方便回访
     *   "rating": 4,                        // 可选，1-5 星
     *   "images": ["url1","url2"],          // 可选，截图链接
     *   "userId": "..."                     // 可选，默认自己
     * }
     */
    @PostMapping("/submit")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> submit(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String content = ViewUtil.toStr(body.get("content"));
        if (content == null) {
            content = ViewUtil.toStr(body.get("message"));
        }
        if (content == null) {
            throw BizException.badRequest("请填写反馈内容");
        }
        if (content.length() > 2000) {
            throw BizException.badRequest("反馈内容过长（最多 2000 字）");
        }

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());
        String category = normalizeCategory(ViewUtil.toStr(body.get("category")));
        String contact = ViewUtil.toStr(body.get("contact"));
        Integer rating = ViewUtil.toInt(body.get("rating"));

        if (rating != null && (rating < 1 || rating > 5)) {
            throw BizException.badRequest("评分需在 1 - 5 之间");
        }

        List<String> images = ViewUtil.toStrList(body.get("images"));

        // 低分反馈提升严重程度，便于优先处理
        String severity = "low";
        if (rating != null && rating <= 2) severity = "medium";

        Alert alert = new Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(targetId);
        alert.setAlertType("feedback");
        alert.setSeverity(severity);
        alert.setTitle("用户反馈 · " + CATEGORIES.getOrDefault(category, "其他"));
        alert.setDescription(content);
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);
        // 分类 / 联系方式 / 评分 / 截图 打包进 notes
        alert.setNotes(buildNotes(category, contact, rating, images));

        alertMapper.insert(alert);

        log.info("收到用户 {} 的反馈[{}]，评分={}，长度={} 字",
                targetId, category, rating, content.length());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", alert.getId());
        data.put("category", category);
        data.put("categoryText", CATEGORIES.getOrDefault(category, "其他"));
        data.put("severity", severity);
        data.put("rating", rating);
        data.put("submittedAt", alert.getOccurredAt().toString());
        data.put("message", "感谢你的反馈！我们会尽快处理 💙");

        return ApiResponse.ok("反馈已提交，感谢你的建议！", data);
    }

    // ========================================================================
    // ② 我的反馈列表
    // ========================================================================

    /**
     * 反馈历史
     *
     * GET /api/feedback/history?userId=...&page=1&pageSize=20
     */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        int p = ViewUtil.clamp(page, 1, 100000);
        int size = ViewUtil.clamp(pageSize, 1, 100);

        List<String> types = List.of("feedback");
        List<Alert> rows = alertMapper.selectPageByTypes(targetId, types, null, (p - 1) * size, size);
        long total = alertMapper.countByTypes(targetId, types, null);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Alert a : rows) {
            list.add(toFeedbackView(a));
        }

        Map<String, Object> data = ViewUtil.page(list, total, p, size);

        // 处理进度汇总，方便前端显示"已处理 N 条"
        long handled = alertMapper.countByTypes(targetId, types, true);
        data.put("handled", handled);
        data.put("pending", total - handled);
        return ApiResponse.ok(data);
    }

    /**
     * 反馈详情
     *
     * GET /api/feedback/detail?id=...
     */
    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest req,
                                                   @RequestParam String id) {
        User me = authService.requireUser(token(req));

        Alert a = alertMapper.selectById(id);
        if (a == null || !"feedback".equals(a.getAlertType())) {
            throw BizException.notFound("反馈不存在");
        }
        if (!me.getId().equals(a.getUserId())) {
            throw BizException.forbidden("无权查看他人的反馈");
        }

        return ApiResponse.ok(toFeedbackView(a));
    }

    // ========================================================================
    // ③ 常见问题
    // ========================================================================

    /**
     * 常见问题（静态内容，不查库）
     *
     * GET /api/feedback/faq
     */
    @GetMapping("/faq")
    public ApiResponse<Map<String, Object>> faq() {
        List<Map<String, Object>> list = new ArrayList<>();

        list.add(faq("如何绑定家人？", "绑定",
                "在「个人中心 → 家庭成员」点击「生成邀请码」，把 8 位邀请码告诉家人；"
                        + "家人登录后在「绑定长辈」页输入邀请码即可完成绑定。绑定后家人可以查看你的健康状态和位置。"));
        list.add(faq("怎么设置吃药提醒？", "提醒",
                "进入「用药管理 → 添加提醒」，填写药品名称、剂量和提醒时间（可添加多个时间点），"
                        + "保存后到点手机会提醒你。也可以在「提醒设置」里调整声音和震动。"));
        list.add(faq("忘记打卡了怎么办？", "打卡",
                "打卡按天记录，同一天可以多次打卡，后一次会覆盖前一次。"
                        + "如果忘记点「睡觉打卡」，可以在次日补点，但睡眠时长会不准确。建议开启打卡提醒。"));
        list.add(faq("跌倒检测是怎么工作的？", "监测",
                "摄像头分析画面中的人体姿态，判断是否发生跌倒。检测到跌倒会立刻生成紧急告警并通知家人。"
                        + "为避免误报，置信度较低的检测只记录不告警。"));
        list.add(faq("安全区是什么？", "位置",
                "安全区是以某个地点为中心的一个圆形范围（如家、小区）。当定位离开所有安全区时，"
                        + "系统会提醒家人。在「位置 → 安全区域」可以新增或修改。"));
        list.add(faq("SOS 怎么用？", "紧急",
                "在「居家守护」页面长按 SOS 按钮 3 秒即可发起求助，系统会立即通知所有已绑定的家人，"
                        + "并记录当前时间和位置。如遇危急情况请直接拨打 120。"));
        list.add(faq("健康评分是怎么算的？", "评分",
                "健康评分满分 100 分，由五个维度加权：作息规律 30%、用药依从 25%、运动量 20%、"
                        + "饮食均衡 15%、监测异常 10%。数据记录得越完整，评分越准确。"));
        list.add(faq("数据会不会泄露？", "隐私",
                "所有健康数据仅你自己和已绑定的家人可见，手机号在接口返回时做了脱敏处理（138****1234）。"
                        + "AI 对话内容仅用于生成回复，不会用于其他用途。"));
        list.add(faq("收不到提醒怎么办？", "提醒",
                "请检查：① 手机是否开启了通知权限；② 「提醒设置」中的总开关是否打开；"
                        + "③ 是否处于免打扰时段（默认 22:30 - 06:30）。"));
        list.add(faq("怎么修改个人信息？", "账号",
                "在「个人中心 → 个人资料」可以修改姓名、头像、性别、出生日期和地址；"
                        + "紧急联系电话也可以在同一个页面设置。"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("categories", new ArrayList<>(CATEGORIES.keySet()));
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    private Map<String, Object> faq(String question, String category, String answer) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("question", question);
        m.put("answer", answer);
        m.put("category", category);
        return m;
    }

    /** 反馈视图：从 notes 还原分类 / 联系方式 / 评分 */
    private Map<String, Object> toFeedbackView(Alert a) {
        Map<String, Object> meta = parseNotes(a.getNotes());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("userId", a.getUserId());
        m.put("content", a.getDescription());
        m.put("category", meta.getOrDefault("category", "other"));
        m.put("categoryText", CATEGORIES.getOrDefault(
                String.valueOf(meta.getOrDefault("category", "other")), "其他"));
        m.put("rating", meta.get("rating"));
        m.put("contact", meta.get("contact"));
        m.put("images", meta.getOrDefault("images", new ArrayList<>()));
        m.put("severity", a.getSeverity());
        m.put("handled", a.getHandled());
        m.put("handledAt", ViewUtil.dateTimeStr(a.getHandledAt()));
        m.put("reply", meta.get("reply"));
        m.put("submittedAt", ViewUtil.dateTimeStr(a.getOccurredAt()));
        m.put("status", Boolean.TRUE.equals(a.getHandled()) ? "已处理" : "处理中");
        return m;
    }

    /** 把反馈附加字段序列化进 notes（复用 JSON 列） */
    private String buildNotes(String category, String contact, Integer rating, List<String> images) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"category\":\"").append(escape(category)).append('"');
        if (contact != null) {
            sb.append(",\"contact\":\"").append(escape(contact)).append('"');
        }
        if (rating != null) {
            sb.append(",\"rating\":").append(rating);
        }
        if (images != null && !images.isEmpty()) {
            sb.append(",\"images\":[");
            for (int i = 0; i < images.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(escape(images.get(i))).append('"');
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }

    /** 解析 notes 中的附加字段 */
    private Map<String, Object> parseNotes(String notes) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (notes == null || notes.isBlank()) return m;

        String s = notes.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        int i = 0;
        while (i < s.length()) {
            int ks = s.indexOf('"', i);
            if (ks < 0) break;
            int ke = s.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = s.substring(ks + 1, ke);

            int colon = s.indexOf(':', ke);
            if (colon < 0) break;

            int vs = colon + 1;
            while (vs < s.length() && s.charAt(vs) == ' ') vs++;

            int next;
            if (vs < s.length() && s.charAt(vs) == '"') {
                int ve = vs + 1;
                StringBuilder sb = new StringBuilder();
                while (ve < s.length()) {
                    char c = s.charAt(ve);
                    if (c == '\\' && ve + 1 < s.length()) {
                        sb.append(s.charAt(ve + 1));
                        ve += 2;
                        continue;
                    }
                    if (c == '"') break;
                    sb.append(c);
                    ve++;
                }
                m.put(key, sb.toString());
                next = ve + 1;
            } else if (vs < s.length() && s.charAt(vs) == '[') {
                // 数组：取到配对的 ]
                int depth = 0, ve = vs;
                boolean inStr = false;
                for (; ve < s.length(); ve++) {
                    char c = s.charAt(ve);
                    if (inStr) {
                        if (c == '\\') { ve++; continue; }
                        if (c == '"') inStr = false;
                        continue;
                    }
                    if (c == '"') inStr = true;
                    else if (c == '[') depth++;
                    else if (c == ']') {
                        depth--;
                        if (depth == 0) { ve++; break; }
                    }
                }
                List<String> arr = new ArrayList<>();
                for (String part : s.substring(vs + 1, Math.max(vs + 1, ve - 1)).split(",")) {
                    String v = part.trim();
                    if (v.startsWith("\"")) v = v.substring(1);
                    if (v.endsWith("\"")) v = v.substring(0, v.length() - 1);
                    if (!v.isEmpty()) arr.add(v);
                }
                m.put(key, arr);
                next = ve;
            } else {
                int ve = s.indexOf(',', vs);
                if (ve < 0) ve = s.length();
                String raw = s.substring(vs, ve).trim();
                m.put(key, parseScalar(raw));
                next = ve;
            }

            int comma = s.indexOf(',', next);
            if (comma < 0) break;
            i = comma + 1;
        }
        return m;
    }

    private Object parseScalar(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
        if ("true".equalsIgnoreCase(raw)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(raw)) return Boolean.FALSE;
        try {
            double d = Double.parseDouble(raw);
            return d == Math.floor(d) ? (Object) (int) d : (Object) d;
        } catch (Exception e) {
            return raw;
        }
    }

    private String normalizeCategory(String c) {
        if (c == null) return "other";
        return CATEGORIES.containsKey(c) ? c : "other";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
