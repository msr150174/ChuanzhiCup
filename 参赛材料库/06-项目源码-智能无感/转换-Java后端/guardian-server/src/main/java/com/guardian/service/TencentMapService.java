package com.guardian.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 腾讯位置服务
 *
 * 提供：
 *   ① 逆地理编码（坐标 → 地址）
 *   ② 地理编码（地址 → 坐标）
 *   ③ POI 周边搜索（医院 / 药店 / 公园）
 *   ④ 距离计算（Haversine，本地算，不消耗配额）
 *
 * ★ Key 从环境变量 TENCENT_MAP_KEY 读取，绝不写死在代码里。
 * ★ 未配置 Key 时全部返回兜底结果，不抛错 —— 页面照常能用。
 *
 * 申请地址：https://lbs.qq.com/console/myKey.html
 */
@Slf4j
@Service
public class TencentMapService {

    @Value("${guardian.tencent-map.key:}")
    private String key;

    private static final String BASE = "https://apis.map.qq.com";
    private static final int TIMEOUT = 8000;

    /** 是否已配置（供健康检查用） */
    public boolean isConfigured() {
        return StrUtil.isNotBlank(key);
    }

    // ========================================================================
    // ① 逆地理编码：坐标 → 地址
    // ========================================================================

    /**
     * 坐标转地址
     *
     * @param lat 纬度
     * @param lng 经度
     * @return { address, formatted, province, city, district, street, resolved }
     */
    public Map<String, Object> reverseGeocode(double lat, double lng) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("lat", lat);
        out.put("lng", lng);

        if (!isConfigured()) {
            return fallbackAddress(out, lat, lng, "未配置腾讯地图 Key");
        }

        try {
            // 腾讯地图要求 纬度,经度 顺序
            String url = BASE + "/ws/geocoder/v1/?location=" + lat + "," + lng
                    + "&key=" + key + "&get_poi=0";

            String body = HttpUtil.get(url, TIMEOUT);
            JSONObject json = JSONUtil.parseObj(body);

            if (json.getInt("status", -1) != 0) {
                log.warn("[地图] 逆地理编码失败: status={} msg={}", json.get("status"), json.get("message"));
                return fallbackAddress(out, lat, lng, "地图服务返回 " + json.get("status"));
            }

            JSONObject result = json.getJSONObject("result");
            JSONObject comp = result.getJSONObject("address_component");
            JSONObject adInfo = result.getJSONObject("ad_info");

            out.put("address", result.getStr("address"));
            out.put("formatted", pickFormatted(result));
            out.put("province", comp == null ? null : comp.getStr("province"));
            out.put("city", comp == null ? null : comp.getStr("city"));
            out.put("district", comp == null ? null : comp.getStr("district"));
            out.put("street", comp == null ? null : comp.getStr("street"));
            out.put("streetNumber", comp == null ? null : comp.getStr("street_number"));
            out.put("adcode", adInfo == null ? null : adInfo.getStr("adcode"));
            out.put("resolved", true);

            log.debug("[地图] 逆地理编码成功: {} -> {}", lat + "," + lng, out.get("address"));
            return out;

        } catch (Exception e) {
            log.warn("[地图] 逆地理编码异常: {}", e.getMessage());
            return fallbackAddress(out, lat, lng, e.getMessage());
        }
    }

    private Map<String, Object> fallbackAddress(Map<String, Object> out, double lat, double lng, String reason) {
        // 用坐标做一个可读的兜底地址，保证页面不空白
        out.put("address", String.format("%.4f, %.4f", lat, lng));
        out.put("formatted", String.format("当前位置（%.4f, %.4f）", lat, lng));
        out.put("resolved", false);
        out.put("reason", reason);
        return out;
    }

    /**
     * 取「推荐地址」文案
     *
     * ★ 腾讯地图的 formatted_addresses 在不同接口/版本下返回类型不一致：
     *   有时是对象 { recommend, rough }, 有时是数组。硬转数组会抛
     *   ClassCastException，所以这里按类型分别处理。
     */
    private String pickFormatted(JSONObject result) {
        Object fa = result.get("formatted_addresses");
        if (fa instanceof JSONObject obj) {
            String rec = obj.getStr("recommend");
            if (StrUtil.isNotBlank(rec)) {
                return rec;
            }
            String rough = obj.getStr("rough");
            if (StrUtil.isNotBlank(rough)) {
                return rough;
            }
        } else if (fa instanceof JSONArray arr && !arr.isEmpty()) {
            return arr.getStr(0);
        }
        return result.getStr("address");
    }

    // ========================================================================
    // ② 地理编码：地址 → 坐标
    // ========================================================================

    public Map<String, Object> geocode(String address) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (StrUtil.isBlank(address)) {
            out.put("resolved", false);
            out.put("reason", "地址为空");
            return out;
        }

        if (!isConfigured()) {
            out.put("resolved", false);
            out.put("reason", "未配置腾讯地图 Key");
            return out;
        }

        try {
            String url = BASE + "/ws/geocoder/v1/?address="
                    + URLEncoder.encode(address, StandardCharsets.UTF_8) + "&key=" + key;

            JSONObject json = JSONUtil.parseObj(HttpUtil.get(url, TIMEOUT));
            if (json.getInt("status", -1) != 0) {
                out.put("resolved", false);
                out.put("reason", "地图服务返回 " + json.get("status"));
                return out;
            }

            JSONObject loc = json.getJSONObject("result").getJSONObject("location");
            out.put("lat", loc.getDouble("lat"));
            out.put("lng", loc.getDouble("lng"));
            out.put("address", address);
            out.put("resolved", true);
            return out;

        } catch (Exception e) {
            log.warn("[地图] 地理编码异常: {}", e.getMessage());
            out.put("resolved", false);
            out.put("reason", e.getMessage());
            return out;
        }
    }

    // ========================================================================
    // ③ POI 周边搜索
    // ========================================================================

    /**
     * 搜索周边地点
     *
     * @param keyword 关键词，如「医院」「药店」
     * @param lat     中心纬度
     * @param lng     中心经度
     * @param radius  半径（米）
     */
    public List<Map<String, Object>> searchNearby(String keyword, double lat, double lng, int radius) {
        List<Map<String, Object>> list = new ArrayList<>();

        if (!isConfigured() || StrUtil.isBlank(keyword)) {
            return list;
        }

        try {
            String url = BASE + "/ws/place/v1/search?boundary=nearby(" + lat + "," + lng + "," + radius + ")"
                    + "&keyword=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + "&page_size=10&orderby=_distance&key=" + key;

            JSONObject json = JSONUtil.parseObj(HttpUtil.get(url, TIMEOUT));
            if (json.getInt("status", -1) != 0) {
                log.warn("[地图] POI 搜索失败: {}", json.get("message"));
                return list;
            }

            JSONArray data = json.getJSONArray("data");
            if (data == null) {
                return list;
            }

            for (Object o : data) {
                JSONObject item = (JSONObject) o;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", item.getStr("id"));
                m.put("title", item.getStr("title"));
                m.put("address", item.getStr("address"));
                m.put("category", item.getStr("category"));
                m.put("distance", item.getInt("_distance", 0));

                JSONObject loc = item.getJSONObject("location");
                if (loc != null) {
                    m.put("lat", loc.getDouble("lat"));
                    m.put("lng", loc.getDouble("lng"));
                }

                JSONObject tel = item.getJSONObject("tel");
                m.put("phone", tel == null ? null : tel.getStr("tel"));

                list.add(m);
            }

        } catch (Exception e) {
            log.warn("[地图] POI 搜索异常: {}", e.getMessage());
        }

        return list;
    }

    // ========================================================================
    // ④ 距离（本地计算，不消耗配额）
    // ========================================================================

    /**
     * Haversine 球面距离（米）
     *
     * 本地算，不调 API —— 安全区判断每秒可能都要算，不能走网络。
     */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** 距离文案 */
    public static String distanceText(double meters) {
        if (meters < 1000) {
            return Math.round(meters) + "米";
        }
        return String.format("%.1f公里", meters / 1000);
    }
}
