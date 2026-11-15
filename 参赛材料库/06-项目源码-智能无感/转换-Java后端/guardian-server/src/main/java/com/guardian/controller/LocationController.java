package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.common.ViewUtil;
import com.guardian.entity.MonitorData;
import com.guardian.entity.User;
import com.guardian.mapper.AlertMapper;
import com.guardian.mapper.MonitorDataMapper;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 位置服务接口
 *
 * 路由：
 *   POST /api/location/address            逆地理（经纬度 → 地址描述）
 *   GET  /api/location/history/{userId}   轨迹历史
 *   POST /api/location/safe-zone          设置安全区
 *   POST /api/location/safe-zone-check    是否在安全区内（Haversine 球面距离）
 *   GET  /api/location/safe-zones/{userId} 安全区列表
 *
 * ★ 位置点复用 monitor_data 表（data_type='location'），
 *   经度存 value 列，纬度与地址存 metadata，避免新增表结构。
 * ★ 安全区配置存 Redis（用户维度列表），随用户隔离。
 */
@Slf4j
@RestController
@RequestMapping("/location")
@RequiredArgsConstructor
public class LocationController {

    private final MonitorDataMapper monitorMapper;
    private final AlertMapper alertMapper;
    private final AuthService authService;
    private final StringRedisTemplate redis;

    @Value("${guardian.safe-zone.default-radius-meters:500}")
    private int defaultRadius;

    @Value("${guardian.location.cache-ttl-seconds:60}")
    private long locationCacheTtl;

    private static final String TYPE_LOCATION = "location";
    private static final String ZONE_KEY = "guardian:safe-zone:";
    private static final String CURRENT_KEY = "guardian:location:current:";

    /** 地球平均半径（米） */
    private static final double EARTH_RADIUS = 6371000;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 当前定位
    // ========================================================================

    /**
     * 上报并返回当前定位
     *
     * 请求体：{ "lat": 39.9042, "lng": 116.4074, "address": "北京市东城区", "accuracy": 12 }
     *
     * ★ 会写一条 location 监测数据，并缓存当前点（供安全区快速判断）。
     */
    @PostMapping("/current")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> current(HttpServletRequest req,
                                                    @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        Double lat = ViewUtil.toDouble(body.get("lat"));
        Double lng = ViewUtil.toDouble(body.get("lng"));
        if (lat == null || lng == null) {
            throw BizException.badRequest("缺少经纬度（lat / lng）");
        }
        validateCoord(lat, lng);

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());
        String address = ViewUtil.toStr(body.get("address"));
        Double accuracy = ViewUtil.toDouble(body.get("accuracy"));

        // 落库
        MonitorData d = new MonitorData();
        d.setUserId(targetId);
        d.setDeviceId(ViewUtil.toStr(body.get("deviceId")));
        d.setDataType(TYPE_LOCATION);
        d.setValue(BigDecimal.valueOf(lng));
        d.setUnit("度");
        d.setRecordedAt(LocalDateTime.now());
        d.setMetadata(toJson(ViewUtil.map(
                "lat", lat, "lng", lng, "address", address, "accuracy", accuracy
        )));
        monitorMapper.insert(d);

        // 缓存当前点
        cacheCurrent(targetId, lat, lng, address);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("address", address == null ? guessArea(lat, lng) : address);
        data.put("area", guessArea(lat, lng));
        data.put("accuracy", accuracy);
        data.put("recordedAt", d.getRecordedAt().toString());
        data.put("id", d.getId());

        // 顺带判断是否已越界
        Map<String, Object> zoneCheck = checkAgainstZones(targetId, lat, lng);
        data.put("inSafeZone", zoneCheck.get("inSafeZone"));
        data.put("nearestZone", zoneCheck.get("nearestZone"));
        data.put("distance", zoneCheck.get("distance"));

        return ApiResponse.ok("定位已更新", data);
    }

    /**
     * 逆地理编码（经纬度 → 地址）
     *
     * 请求体：{ "lat": 39.9042, "lng": 116.4074 }
     *
     * ★ 没有接入地图服务商的逆地理 API，这里返回基于坐标的行政区域推断
     *   与相对安全区的方位描述，并标注 resolved=false 供前端决定是否降级展示。
     */
    @PostMapping("/address")
    public ApiResponse<Map<String, Object>> address(HttpServletRequest req,
                                                    @RequestBody Map<String, Object> body) {
        authService.requireUser(token(req));

        Double lat = ViewUtil.toDouble(body == null ? null : body.get("lat"));
        Double lng = ViewUtil.toDouble(body == null ? null : body.get("lng"));
        if (lat == null || lng == null) {
            throw BizException.badRequest("缺少经纬度（lat / lng）");
        }
        validateCoord(lat, lng);

        String clientAddress = ViewUtil.toStr(body.get("address"));
        String area = guessArea(lat, lng);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("address", clientAddress == null ? area : clientAddress);
        data.put("area", area);
        data.put("province", provinceOf(lat, lng));
        data.put("resolved", clientAddress != null);
        data.put("resolver", clientAddress != null ? "client" : "coordinate-heuristic");
        data.put("tip", clientAddress == null
                ? "未接入地图服务商逆地理接口，地址为根据坐标推断的参考值"
                : null);
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ② 轨迹历史
    // ========================================================================

    /**
     * 轨迹历史
     *
     * GET /api/location/history/{userId}?hours=24&limit=500
     *
     * 返回按时间正序的轨迹点，并计算总里程与停留点。
     */
    @GetMapping("/history/{userId}")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @PathVariable String userId,
                                                    @RequestParam(defaultValue = "24") int hours,
                                                    @RequestParam(defaultValue = "500") int limit) {
        authService.requireUser(token(req));

        int h = ViewUtil.clamp(hours, 1, 24 * 30);
        int lim = ViewUtil.clamp(limit, 1, 2000);

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(h);

        List<MonitorData> rows = monitorMapper.selectByRange(userId, TYPE_LOCATION, from, to, lim);

        List<Map<String, Object>> points = new ArrayList<>();
        double totalDistance = 0;
        Double prevLat = null, prevLng = null;

        // 查询结果倒序，翻正
        for (int i = rows.size() - 1; i >= 0; i--) {
            MonitorData d = rows.get(i);
            Map<String, Object> meta = parseJson(d.getMetadata());

            Double lat = ViewUtil.toDouble(meta.get("lat"));
            Double lng = ViewUtil.toDouble(meta.get("lng"));
            // 兜底：metadata 缺失时用 value 当经度
            if (lng == null && d.getValue() != null) lng = d.getValue().doubleValue();

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", d.getId());
            p.put("lat", lat);
            p.put("lng", lng);
            p.put("address", meta.get("address"));
            p.put("accuracy", meta.get("accuracy"));
            p.put("recordedAt", ViewUtil.dateTimeStr(d.getRecordedAt()));
            points.add(p);

            if (lat != null && lng != null) {
                if (prevLat != null && prevLng != null) {
                    totalDistance += distance(prevLat, prevLng, lat, lng);
                }
                prevLat = lat;
                prevLng = lng;
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("list", points);
        data.put("total", points.size());
        data.put("from", from.toString());
        data.put("to", to.toString());
        data.put("hours", h);
        data.put("totalDistance", ViewUtil.round(totalDistance, 1));
        data.put("totalDistanceText", distanceText(totalDistance));
        data.put("hasMore", rows.size() >= lim);
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ③ 安全区
    // ========================================================================

    /**
     * 设置安全区
     *
     * 请求体：
     * {
     *   "name": "家",
     *   "lat": 39.9042, "lng": 116.4074,
     *   "radius": 500,           // 米，不传用配置默认值
     *   "userId": "...",         // 可选，默认自己
     *   "address": "北京市东城区..."
     * }
     *
     * 传了 id 则为更新，否则新建。
     */
    @PostMapping("/safe-zone")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> setSafeZone(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());

        Double lat = ViewUtil.toDouble(body.get("lat"));
        Double lng = ViewUtil.toDouble(body.get("lng"));
        if (lat == null || lng == null) {
            throw BizException.badRequest("请提供安全区中心点经纬度（lat / lng）");
        }
        validateCoord(lat, lng);

        int radius = ViewUtil.toInt(body.get("radius"), defaultRadius);
        if (radius < 50 || radius > 50000) {
            throw BizException.badRequest("安全区半径需在 50 - 50000 米之间");
        }

        String name = ViewUtil.toStr(body.get("name"), "安全区");
        String id = ViewUtil.toStr(body.get("id"));

        List<Map<String, Object>> zones = readZones(targetId);

        Map<String, Object> zone;
        boolean created;
        if (id == null) {
            // 新建
            zone = new LinkedHashMap<>();
            zone.put("id", CommonUtil.uuid());
            created = true;
        } else {
            // 更新：找到原记录
            zone = null;
            for (Map<String, Object> z : zones) {
                if (id.equals(String.valueOf(z.get("id")))) {
                    zone = z;
                    break;
                }
            }
            if (zone == null) {
                throw BizException.notFound("安全区不存在");
            }
            created = false;
        }

        zone.put("name", name);
        zone.put("lat", lat);
        zone.put("lng", lng);
        zone.put("radius", radius);
        zone.put("address", ViewUtil.toStr(body.get("address")));
        zone.put("enabled", ViewUtil.toBool(body.get("enabled"), true));
        zone.put("alertOnExit", ViewUtil.toBool(body.get("alertOnExit"), true));
        zone.put("updateTime", LocalDateTime.now().toString());

        if (created) {
            zones.add(zone);
        }
        writeZones(targetId, zones);

        log.info("用户 {} {}安全区「{}」，中心 ({}, {})，半径 {} 米",
                targetId, created ? "新建" : "更新", name, lat, lng, radius);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        data.put("zone", zone);
        data.put("total", zones.size());
        return ApiResponse.ok(created ? "安全区已创建" : "安全区已更新", data);
    }

    /** 安全区列表 */
    @GetMapping("/safe-zones/{userId}")
    public ApiResponse<Map<String, Object>> safeZones(HttpServletRequest req,
                                                      @PathVariable String userId) {
        authService.requireUser(token(req));

        List<Map<String, Object>> zones = readZones(userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("list", zones);
        data.put("total", zones.size());
        data.put("defaultRadius", defaultRadius);
        return ApiResponse.ok(data);
    }

    /**
     * 安全区检查
     *
     * 请求体：{ "lat": 39.9, "lng": 116.4, "userId": "...", "zoneId": "可选" }
     *
     * ★ 使用 Haversine 公式计算球面距离，与安全区半径比较。
     *   不在任何启用中的安全区内时，产生 low 级告警（离开安全区）。
     */
    @PostMapping("/safe-zone-check")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> checkSafeZone(HttpServletRequest req,
                                                          @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());

        Double lat = ViewUtil.toDouble(body.get("lat"));
        Double lng = ViewUtil.toDouble(body.get("lng"));

        // 没传坐标时取缓存的当前点
        if (lat == null || lng == null) {
            Map<String, Object> cur = currentPoint(targetId);
            if (cur == null) {
                throw BizException.badRequest("缺少经纬度，且没有可用的最近定位记录");
            }
            lat = ViewUtil.toDouble(cur.get("lat"));
            lng = ViewUtil.toDouble(cur.get("lng"));
        }
        validateCoord(lat, lng);

        String zoneId = ViewUtil.toStr(body.get("zoneId"));

        List<Map<String, Object>> zones = readZones(targetId);
        if (zones.isEmpty()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("lat", lat);
            data.put("lng", lng);
            data.put("inSafeZone", true);       // 没设安全区视为无约束
            data.put("checked", false);
            data.put("message", "尚未设置安全区，已跳过检查");
            data.put("zones", new ArrayList<>());
            return ApiResponse.ok(data);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        boolean inAny = false;
        Map<String, Object> nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Map<String, Object> z : zones) {
            if (zoneId != null && !zoneId.equals(String.valueOf(z.get("id")))) continue;
            if (!ViewUtil.toBool(z.get("enabled"), true)) continue;

            Double zlat = ViewUtil.toDouble(z.get("lat"));
            Double zlng = ViewUtil.toDouble(z.get("lng"));
            int zr = ViewUtil.toInt(z.get("radius"), defaultRadius);

            if (zlat == null || zlng == null) continue;

            double dist = distance(lat, lng, zlat, zlng);
            boolean inside = dist <= zr;
            if (inside) inAny = true;

            if (dist < nearestDistance) {
                nearestDistance = dist;
                nearest = z;
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("zoneId", z.get("id"));
            r.put("name", z.get("name"));
            r.put("inside", inside);
            r.put("distance", ViewUtil.round(dist, 1));
            r.put("distanceText", distanceText(dist));
            r.put("radius", zr);
            r.put("zoneLat", zlat);
            r.put("zoneLng", zlng);
            r.put("gap", ViewUtil.round(inside ? 0 : dist - zr, 1));
            results.add(r);
        }

        // 越界告警
        String alertId = null;
        if (!inAny && !results.isEmpty()) {
            String name = nearest == null ? "安全区" : ViewUtil.str(nearest.get("name"));
            alertId = createAlert(targetId, "out_of_zone", "low", "离开安全区",
                    String.format("当前位置 (%.6f, %.6f) 已离开「%s」，距离约 %s",
                            lat, lng, name, distanceText(nearestDistance)));
            log.info("用户 {} 离开安全区「{}」，距离 {} 米", targetId, name, Math.round(nearestDistance));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("inSafeZone", inAny);
        data.put("checked", true);
        data.put("zones", results);
        data.put("zoneCount", results.size());
        data.put("alertId", alertId);
        data.put("distance", results.isEmpty() ? null : ViewUtil.round(nearestDistance, 1));
        data.put("message", inAny
                ? "当前在安全区内"
                : String.format("当前已离开安全区，最近距离约 %s", distanceText(nearestDistance)));
        return ApiResponse.ok(data);
    }

    /**
     * 删除安全区
     *
     * DELETE /api/location/safe-zone?id=...
     */
    @DeleteMapping("/safe-zone")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> deleteSafeZone(HttpServletRequest req,
                                                           @RequestParam String id,
                                                           @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        List<Map<String, Object>> zones = readZones(targetId);
        boolean removed = zones.removeIf(z -> id.equals(String.valueOf(z.get("id"))));
        if (!removed) {
            throw BizException.notFound("安全区不存在");
        }
        writeZones(targetId, zones);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", id);
        data.put("total", zones.size());
        return ApiResponse.ok("安全区已删除", data);
    }

    // ========================================================================
    // 距离计算
    // ========================================================================

    /**
     * Haversine 球面距离（米）
     *
     * 先求两点纬度差与经度差的弧度值，再代入半正矢公式，
     * 用 atan2 反解中心角，最后乘地球半径得到米数。
     */
    private double distance(double lat1, double lng1, double lat2, double lng2) {
        double R = EARTH_RADIUS;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 距离文案：<1000 米显示米，否则显示公里 */
    private String distanceText(double meters) {
        if (meters < 1000) return Math.round(meters) + " 米";
        return ViewUtil.round(meters / 1000, 2) + " 公里";
    }

    /** 经纬度合法性校验 */
    private void validateCoord(double lat, double lng) {
        if (lat < -90 || lat > 90) throw BizException.badRequest("纬度需在 -90 ~ 90 之间");
        if (lng < -180 || lng > 180) throw BizException.badRequest("经度需在 -180 ~ 180 之间");
    }

    /** 用当前点批量判断各安全区（供 current 接口复用） */
    private Map<String, Object> checkAgainstZones(String userId, double lat, double lng) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> zones = readZones(userId);

        boolean inAny = zones.isEmpty();
        Map<String, Object> nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Map<String, Object> z : zones) {
            if (!ViewUtil.toBool(z.get("enabled"), true)) continue;
            Double zlat = ViewUtil.toDouble(z.get("lat"));
            Double zlng = ViewUtil.toDouble(z.get("lng"));
            if (zlat == null || zlng == null) continue;

            int zr = ViewUtil.toInt(z.get("radius"), defaultRadius);
            double dist = distance(lat, lng, zlat, zlng);
            if (dist <= zr) inAny = true;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = z;
            }
        }

        out.put("inSafeZone", inAny);
        out.put("distance", nearest == null ? null : ViewUtil.round(nearestDist, 1));
        if (nearest != null) {
            out.put("nearestZone", ViewUtil.map(
                    "id", nearest.get("id"),
                    "name", nearest.get("name"),
                    "radius", nearest.get("radius")
            ));
        } else {
            out.put("nearestZone", null);
        }
        return out;
    }

    // ========================================================================
    // 安全区与当前点存储（Redis）
    // ========================================================================

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readZones(String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            String raw = redis.opsForValue().get(ZONE_KEY + userId);
            if (raw == null || raw.isBlank()) return out;

            // 顶层数组
            String s = raw.trim();
            if (!s.startsWith("[")) return out;

            for (String item : splitTopLevel(s.substring(1, s.length() - 1))) {
                Map<String, Object> m = parseJson(item);
                if (!m.isEmpty()) out.add(m);
            }
        } catch (Exception e) {
            log.warn("安全区读取失败（Redis 不可用）: {}", e.getMessage());
        }
        return out;
    }

    private void writeZones(String userId, List<Map<String, Object>> zones) {
        try {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < zones.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(toJson(zones.get(i)));
            }
            sb.append(']');
            redis.opsForValue().set(ZONE_KEY + userId, sb.toString());
        } catch (Exception e) {
            log.warn("安全区写入失败（Redis 不可用）: {}", e.getMessage());
        }
    }

    private void cacheCurrent(String userId, double lat, double lng, String address) {
        try {
            redis.opsForValue().set(CURRENT_KEY + userId,
                    toJson(ViewUtil.map("lat", lat, "lng", lng, "address", address,
                            "at", LocalDateTime.now().toString())),
                    locationCacheTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("当前位置缓存失败（忽略）: {}", e.getMessage());
        }
    }

    private Map<String, Object> currentPoint(String userId) {
        try {
            String raw = redis.opsForValue().get(CURRENT_KEY + userId);
            if (raw == null || raw.isBlank()) return null;
            Map<String, Object> m = parseJson(raw);
            return m.isEmpty() ? null : m;
        } catch (Exception e) {
            log.debug("当前位置读取失败（忽略）: {}", e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // 轻量 JSON 处理
    // ========================================================================

    /** 按顶层逗号切分数组元素（括号深度为 0 时才算分隔） */
    private List<String> splitTopLevel(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isBlank()) return out;

        int depth = 0;
        boolean inStr = false;
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                cur.append(c);
                if (c == '\\' && i + 1 < s.length()) {
                    cur.append(s.charAt(++i));
                } else if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> { inStr = true; cur.append(c); }
                case '{', '[' -> { depth++; cur.append(c); }
                case '}', ']' -> { depth--; cur.append(c); }
                case ',' -> {
                    if (depth == 0) {
                        out.add(cur.toString().trim());
                        cur.setLength(0);
                    } else {
                        cur.append(c);
                    }
                }
                default -> cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    /** 平铺 Map 转 JSON */
    private String toJson(Map<String, Object> m) {
        if (m == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"')
                  .append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\""))
                  .append('"');
            }
        }
        return sb.append('}').toString();
    }

    /** 解析平铺 JSON 对象 */
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return m;

        String s = json.trim();
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

            Object value;
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
                value = sb.toString();
                next = ve + 1;
            } else {
                int ve = s.indexOf(',', vs);
                if (ve < 0) ve = s.length();
                value = parseScalar(s.substring(vs, ve).trim());
                next = ve;
            }

            m.put(key, value);

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
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    // ========================================================================
    // 地址推断（无地图 API 时的启发式实现）
    // ========================================================================

    /** 按经纬度粗判行政区域（中国范围内的大区划分） */
    private String guessArea(double lat, double lng) {
        String province = provinceOf(lat, lng);
        if (province == null) {
            return String.format("未知区域 (%.4f, %.4f)", lat, lng);
        }
        return province + "附近";
    }

    /**
     * 粗粒度省份推断
     *
     * ★ 仅按经纬度落在哪个大致矩形范围内判断，精度有限，
     *   真实地址请由前端调用地图 SDK 后通过 address 字段传入。
     */
    private String provinceOf(double lat, double lng) {
        // 北京
        if (lat >= 39.4 && lat <= 41.1 && lng >= 115.4 && lng <= 117.5) return "北京市";
        // 天津
        if (lat >= 38.5 && lat <= 40.3 && lng >= 116.7 && lng <= 118.1) return "天津市";
        // 上海
        if (lat >= 30.6 && lat <= 31.9 && lng >= 120.8 && lng <= 122.2) return "上海市";
        // 重庆
        if (lat >= 28.1 && lat <= 32.3 && lng >= 105.2 && lng <= 110.2) return "重庆市";
        // 广东
        if (lat >= 20.1 && lat <= 25.6 && lng >= 109.6 && lng <= 117.3) return "广东省";
        // 江苏
        if (lat >= 30.7 && lat <= 35.2 && lng >= 116.3 && lng <= 121.9) return "江苏省";
        // 浙江
        if (lat >= 27.0 && lat <= 31.5 && lng >= 118.0 && lng <= 123.0) return "浙江省";
        // 山东
        if (lat >= 34.3 && lat <= 38.5 && lng >= 114.8 && lng <= 122.7) return "山东省";
        // 四川
        if (lat >= 26.0 && lat <= 34.3 && lng >= 97.3 && lng <= 108.6) return "四川省";
        // 湖北
        if (lat >= 29.0 && lat <= 33.3 && lng >= 108.3 && lng <= 116.2) return "湖北省";
        // 湖南
        if (lat >= 24.6 && lat <= 30.1 && lng >= 108.7 && lng <= 114.3) return "湖南省";
        // 河南
        if (lat >= 31.3 && lat <= 36.4 && lng >= 110.3 && lng <= 116.7) return "河南省";
        // 河北
        if (lat >= 36.0 && lat <= 42.7 && lng >= 113.4 && lng <= 119.9) return "河北省";
        // 陕西
        if (lat >= 31.7 && lat <= 39.6 && lng >= 105.4 && lng <= 111.3) return "陕西省";
        // 福建
        if (lat >= 23.5 && lat <= 28.4 && lng >= 115.8 && lng <= 120.7) return "福建省";
        // 辽宁
        if (lat >= 38.7 && lat <= 43.5 && lng >= 118.8 && lng <= 125.8) return "辽宁省";
        // 黑龙江
        if (lat >= 43.4 && lat <= 53.6 && lng >= 121.1 && lng <= 135.1) return "黑龙江省";
        // 吉林
        if (lat >= 40.8 && lat <= 46.3 && lng >= 121.6 && lng <= 131.3) return "吉林省";
        // 安徽
        if (lat >= 29.4 && lat <= 34.6 && lng >= 114.8 && lng <= 119.7) return "安徽省";
        // 江西
        if (lat >= 24.5 && lat <= 30.1 && lng >= 113.5 && lng <= 118.5) return "江西省";
        // 广西
        if (lat >= 20.8 && lat <= 26.4 && lng >= 104.4 && lng <= 112.1) return "广西壮族自治区";
        // 云南
        if (lat >= 21.1 && lat <= 29.3 && lng >= 97.5 && lng <= 106.2) return "云南省";
        // 贵州
        if (lat >= 24.6 && lat <= 29.2 && lng >= 103.6 && lng <= 109.6) return "贵州省";
        // 山西
        if (lat >= 34.5 && lat <= 40.8 && lng >= 110.2 && lng <= 114.6) return "山西省";
        // 甘肃
        if (lat >= 32.1 && lat <= 42.8 && lng >= 92.1 && lng <= 108.7) return "甘肃省";
        // 内蒙古
        if (lat >= 37.4 && lat <= 53.4 && lng >= 97.2 && lng <= 126.1) return "内蒙古自治区";
        // 新疆
        if (lat >= 34.3 && lat <= 49.2 && lng >= 73.5 && lng <= 96.4) return "新疆维吾尔自治区";
        // 西藏
        if (lat >= 26.8 && lat <= 36.5 && lng >= 78.4 && lng <= 99.1) return "西藏自治区";
        // 青海
        if (lat >= 31.6 && lat <= 39.2 && lng >= 89.4 && lng <= 103.1) return "青海省";
        // 宁夏
        if (lat >= 35.2 && lat <= 39.4 && lng >= 104.2 && lng <= 107.7) return "宁夏回族自治区";
        // 海南
        if (lat >= 18.1 && lat <= 20.2 && lng >= 108.6 && lng <= 111.1) return "海南省";
        // 台湾
        if (lat >= 21.8 && lat <= 25.4 && lng >= 119.3 && lng <= 122.1) return "台湾省";
        // 香港
        if (lat >= 22.1 && lat <= 22.6 && lng >= 113.8 && lng <= 114.5) return "香港特别行政区";
        // 澳门
        if (lat >= 22.0 && lat <= 22.3 && lng >= 113.4 && lng <= 113.7) return "澳门特别行政区";
        return null;
    }

    // ========================================================================
    // 杂项
    // ========================================================================

    private String createAlert(String userId, String type, String severity,
                               String title, String description) {
        com.guardian.entity.Alert alert = new com.guardian.entity.Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(userId);
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);
        alertMapper.insert(alert);
        return alert.getId();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
