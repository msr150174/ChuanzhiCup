package com.guardian.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.Alert;
import com.guardian.entity.Device;
import com.guardian.mapper.AlertMapper;
import com.guardian.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 设备服务
 *
 * 负责智能设备（手表 / 手环 / 拐杖 / 传感器）的绑定、连接、状态上报与告警。
 *
 * 说明：
 *   - 设备元信息（电量 batteryLevel、信号 signalStrength、固件 firmwareVersion、型号 model）
 *     存放在 devices.metadata 这个 JSON 字段里，避免为每个字段单独建列。
 *   - 设备告警复用 alerts 表，alert_type 以 device_ 前缀区分设备类告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final AlertMapper alertMapper;

    /** 设备告警统一前缀，便于和跌倒 / SOS 等健康告警区分 */
    private static final String DEVICE_ALERT_PREFIX = "device_";

    /** 状态文案映射 */
    private static String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "active" -> "在线";
            case "offline" -> "离线";
            case "unbound" -> "已解绑";
            default -> "未知";
        };
    }

    // ========================================================================
    // 查询
    // ========================================================================

    /** 设备列表 */
    public Map<String, Object> list(String userId) {
        List<Device> devices = deviceMapper.selectByUser(userId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Device d : devices) {
            list.add(toView(d));
        }

        long online = list.stream().filter(m -> "active".equals(m.get("status"))).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("onlineCount", online);
        data.put("offlineCount", list.size() - online);
        return data;
    }

    /**
     * 设备详情
     *
     * 未指定 deviceId 时返回该用户最近绑定的一台设备。
     */
    public Map<String, Object> detail(String userId, String deviceId) {
        Device d = resolveDevice(userId, deviceId);
        if (d == null) {
            throw BizException.notFound("设备不存在");
        }

        Map<String, Object> view = toView(d);

        // 附上该设备的未处理告警数
        long pending = alertMapper.selectByUserAndRange(
                        userId, LocalDateTime.now().minusDays(30), LocalDateTime.now())
                .stream()
                .filter(a -> a.getAlertType() != null && a.getAlertType().startsWith(DEVICE_ALERT_PREFIX))
                .filter(a -> Boolean.FALSE.equals(a.getHandled()))
                .count();
        view.put("pendingAlerts", pending);
        return view;
    }

    // ========================================================================
    // 绑定 / 解绑 / 连接
    // ========================================================================

    /**
     * 绑定新设备
     *
     * 设备 ID 用 UUID 生成；电量与信号给默认初值，写入 metadata。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bind(String userId, String name, String type, String model) {
        if (name == null || name.isBlank()) {
            throw BizException.badRequest("请填写设备名称");
        }
        if (type == null || type.isBlank()) {
            throw BizException.badRequest("请选择设备类型");
        }

        Device d = new Device();
        d.setId(CommonUtil.uuid());
        d.setUserId(userId);
        d.setDeviceName(name.trim());
        d.setDeviceType(type.trim());
        d.setStatus("active");
        d.setLastSync(LocalDateTime.now());
        d.setMetadata(buildMetadata(null, null, "v1.0.0", model));

        deviceMapper.insert(d);
        log.info("用户 {} 绑定新设备 {}（{}）", userId, d.getDeviceName(), d.getId());

        return toView(d);
    }

    /** 解绑设备（物理删除，仅限本人设备） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unbind(String userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw BizException.badRequest("缺少设备 ID");
        }

        Device d = deviceMapper.selectById(deviceId);
        if (d == null) {
            throw BizException.notFound("设备不存在");
        }
        if (!Objects.equals(d.getUserId(), userId)) {
            throw BizException.forbidden("无权解绑该设备");
        }

        deviceMapper.deleteByIdAndUser(deviceId, userId);
        log.info("用户 {} 解绑设备 {}", userId, deviceId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("deviceId", deviceId);
        data.put("message", "设备已解绑");
        return data;
    }

    /** 连接设备：状态置为 active */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> connect(String userId, String deviceId) {
        Device d = requireOwnedDevice(userId, deviceId);

        deviceMapper.updateStatus(d.getId(), "active");
        d.setStatus("active");
        d.setLastSync(LocalDateTime.now());
        log.info("设备已连接: userId={}, deviceId={}", userId, d.getId());

        Map<String, Object> data = toView(d);
        data.put("message", "设备已连接");
        return data;
    }

    /**
     * 断开设备：状态置为 offline，并产生一条设备告警
     *
     * 原项目里断开只改状态，家属端感知不到；这里补一条告警，便于家属及时知晓。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> disconnect(String userId, String deviceId) {
        Device d = requireOwnedDevice(userId, deviceId);

        deviceMapper.updateStatus(d.getId(), "offline");
        d.setStatus("offline");
        log.info("设备已断开: userId={}, deviceId={}", userId, d.getId());

        Alert alert = new Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(userId);
        alert.setAlertType(DEVICE_ALERT_PREFIX + "disconnected");
        alert.setSeverity("medium");
        alert.setTitle("设备已断开");
        alert.setDescription(String.format("设备【%s】已断开连接", d.getDeviceName()));
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);
        alertMapper.insert(alert);

        Map<String, Object> data = toView(d);
        data.put("message", "设备已断开");
        data.put("alertId", alert.getId());
        return data;
    }

    // ========================================================================
    // 状态上报
    // ========================================================================

    /**
     * 设备状态上报
     *
     * 更新 last_sync 与 metadata（电量 / 信号 / 固件）。
     * 电量低于 20% 时自动产生 low_battery 告警。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateStatus(String userId, String deviceId,
                                            Integer batteryLevel, Integer signalStrength,
                                            String firmwareVersion, String status) {
        Device d = requireOwnedDevice(userId, deviceId);

        JSONObject meta = parseMetadata(d.getMetadata());
        if (batteryLevel != null) {
            meta.set("batteryLevel", Math.max(0, Math.min(100, batteryLevel)));
        }
        if (signalStrength != null) {
            meta.set("signalStrength", Math.max(0, Math.min(100, signalStrength)));
        }
        if (firmwareVersion != null && !firmwareVersion.isBlank()) {
            meta.set("firmwareVersion", firmwareVersion);
        }

        // 状态：显式传入优先，否则按电量 / 信号推导
        String newStatus = status;
        if (newStatus == null || newStatus.isBlank()) {
            Integer battery = meta.getInt("batteryLevel");
            if (battery != null && battery <= 0) {
                newStatus = "offline";
            } else {
                newStatus = "active";
            }
        }

        String metaJson = meta.toString();
        deviceMapper.updateMetadata(d.getId(), metaJson);
        deviceMapper.updateStatus(d.getId(), newStatus);

        d.setMetadata(metaJson);
        d.setStatus(newStatus);
        d.setLastSync(LocalDateTime.now());

        // 低电量告警
        Integer battery = meta.getInt("batteryLevel");
        if (battery != null && battery < 20) {
            Alert alert = new Alert();
            alert.setId(CommonUtil.uuid());
            alert.setUserId(userId);
            alert.setAlertType(DEVICE_ALERT_PREFIX + "low_battery");
            alert.setSeverity(battery < 10 ? "high" : "medium");
            alert.setTitle("设备电量不足");
            alert.setDescription(String.format("设备【%s】电量仅剩 %d%%，请及时充电",
                    d.getDeviceName(), battery));
            alert.setOccurredAt(LocalDateTime.now());
            alert.setHandled(false);
            alertMapper.insert(alert);
            log.warn("设备 {} 电量低: {}%", d.getId(), battery);
        }

        Map<String, Object> data = toView(d);
        data.put("message", "设备状态已更新");
        return data;
    }

    // ========================================================================
    // 告警
    // ========================================================================

    /**
     * 设备告警列表（分页）
     *
     * 只返回 alert_type 以 device_ 开头的告警，避免和健康告警混淆。
     */
    public Map<String, Object> alerts(String userId, Boolean handled, int page, int pageSize) {
        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));

        // 拉取较大范围后过滤设备类告警（alertMapper 无按类型分页的方法）
        List<Alert> rows = alertMapper.selectPage(userId, handled, 0, 200);

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Alert a : rows) {
            if (a.getAlertType() != null && a.getAlertType().startsWith(DEVICE_ALERT_PREFIX)) {
                filtered.add(toAlertView(a));
            }
        }

        long total = filtered.size();
        int from = Math.min((p - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<Map<String, Object>> pageList = filtered.subList(from, to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", pageList);
        data.put("total", total);
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", (long) p * size < total);
        return data;
    }

    /**
     * 处理设备告警
     *
     * action: resolve（已处理）/ ignore（忽略，同样标记为已处理但备注区分）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> handleAlert(String userId, String alertId, String action) {
        if (alertId == null || alertId.isBlank()) {
            throw BizException.badRequest("缺少告警 ID");
        }

        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警不存在");
        }
        if (!Objects.equals(alert.getUserId(), userId)) {
            throw BizException.forbidden("无权处理该告警");
        }
        if (Boolean.TRUE.equals(alert.getHandled())) {
            throw BizException.badRequest("该告警已处理过了");
        }

        boolean ignore = "ignore".equalsIgnoreCase(action);
        String notes = ignore ? "已忽略（设备告警）" : "已处理（设备告警）";
        alertMapper.updateHandled(alertId, userId, notes);

        log.info("设备告警 {} 已由 {} 处理，action={}", alertId, userId, action);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("alertId", alertId);
        data.put("action", ignore ? "ignore" : "resolve");
        data.put("message", ignore ? "告警已忽略" : "告警已处理");
        return data;
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 取设备并校验归属，不存在或不属于本人时抛异常 */
    private Device requireOwnedDevice(String userId, String deviceId) {
        if (deviceId == null || deviceId.isBlank()) {
            throw BizException.badRequest("缺少设备 ID");
        }
        Device d = deviceMapper.selectById(deviceId);
        if (d == null) {
            throw BizException.notFound("设备不存在");
        }
        if (!Objects.equals(d.getUserId(), userId)) {
            throw BizException.forbidden("无权操作该设备");
        }
        return d;
    }

    /** 未指定 deviceId 时回退到最近绑定的一台设备 */
    private Device resolveDevice(String userId, String deviceId) {
        if (deviceId != null && !deviceId.isBlank()) {
            Device d = deviceMapper.selectById(deviceId);
            if (d == null || !Objects.equals(d.getUserId(), userId)) {
                return null;
            }
            return d;
        }
        List<Device> devices = deviceMapper.selectByUser(userId);
        return devices.isEmpty() ? null : devices.get(0);
    }

    /** 组装 metadata JSON */
    private String buildMetadata(Integer battery, Integer signal, String firmware, String model) {
        JSONObject meta = new JSONObject();
        meta.set("batteryLevel", battery == null ? 100 : battery);
        meta.set("signalStrength", signal == null ? 95 : signal);
        meta.set("firmwareVersion", firmware == null ? "v1.0.0" : firmware);
        if (model != null) {
            meta.set("model", model);
        }
        return meta.toString();
    }

    /** 安全解析 metadata，非法 JSON 时返回空对象 */
    private JSONObject parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception e) {
            log.warn("设备 metadata 解析失败，已忽略: {}", e.getMessage());
            return new JSONObject();
        }
    }

    /** 设备转前端视图（camelCase） */
    private Map<String, Object> toView(Device d) {
        JSONObject meta = parseMetadata(d.getMetadata());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("userId", d.getUserId());
        m.put("name", d.getDeviceName());
        m.put("type", d.getDeviceType());
        m.put("typeText", deviceTypeText(d.getDeviceType()));
        m.put("mac", d.getDeviceMac());
        m.put("status", d.getStatus());
        m.put("statusText", statusText(d.getStatus()));
        m.put("batteryLevel", meta.getInt("batteryLevel", 100));
        m.put("signalStrength", meta.getInt("signalStrength", 95));
        m.put("firmwareVersion", meta.getStr("firmwareVersion", "v1.0.0"));
        m.put("model", meta.getStr("model"));
        m.put("lastSync", d.getLastSync() == null ? null : d.getLastSync().toString());
        m.put("createTime", d.getCreatedAt() == null ? null : d.getCreatedAt().toString());
        return m;
    }

    /** 设备告警转前端视图 */
    private Map<String, Object> toAlertView(Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("deviceAlertType", a.getAlertType() == null ? null
                : a.getAlertType().replace(DEVICE_ALERT_PREFIX, ""));
        m.put("type", a.getAlertType());
        m.put("severity", a.getSeverity());
        m.put("title", a.getTitle());
        m.put("message", a.getDescription());
        m.put("occurredAt", a.getOccurredAt() == null ? null : a.getOccurredAt().toString());
        m.put("handled", a.getHandled());
        m.put("handledAt", a.getHandledAt() == null ? null : a.getHandledAt().toString());
        m.put("notes", a.getNotes());
        return m;
    }

    private String deviceTypeText(String type) {
        return switch (type == null ? "" : type) {
            case "watch" -> "智能手表";
            case "band" -> "智能手环";
            case "smart_cane" -> "智能拐杖";
            case "sensor" -> "环境传感器";
            case "blood_pressure" -> "血压计";
            case "glucose" -> "血糖仪";
            default -> "其他设备";
        };
    }
}
