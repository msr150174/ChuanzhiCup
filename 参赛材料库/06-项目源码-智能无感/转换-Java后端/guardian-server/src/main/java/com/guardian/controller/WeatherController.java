package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.ViewUtil;
import com.guardian.entity.User;
import com.guardian.mapper.ElderInfoMapper;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 天气接口
 *
 * 路由：
 *   GET /api/weather/live     实时天气
 *   GET /api/weather/forecast 未来几天预报
 *   GET /api/weather/unified  统一天气（实时 + 预报 + 生活指数）
 *   GET /api/weather/advice   天气健康建议
 *
 * ★ 没有接入第三方天气 API（无 key），所有数据基于「季节 + 时间 + 城市纬度」的
 *   确定性模型生成，响应里 { mock: true } 明确标注，前端可据此提示用户。
 *   接入真实 API 时只需替换 buildWeather() 内的取值逻辑。
 * ★ 结果按城市缓存 10 分钟，避免同一城市反复计算。
 */
@Slf4j
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final AuthService authService;
    private final ElderInfoMapper elderInfoMapper;
    private final StringRedisTemplate redis;

    private static final String CACHE_KEY = "guardian:weather:";
    private static final long CACHE_TTL_MINUTES = 10;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 实时天气
    // ========================================================================

    /**
     * 实时天气
     *
     * GET /api/weather/live?city=北京
     */
    @GetMapping("/live")
    public ApiResponse<Map<String, Object>> live(HttpServletRequest req,
                                                 @RequestParam(required = false) String city) {
        authService.requireUser(token(req));
        String c = normalizeCity(city);

        Map<String, Object> data = buildWeather(c, LocalDate.now());
        data.put("updateTime", LocalDateTime.now().toString());

        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ② 未来预报
    // ========================================================================

    /**
     * 未来预报
     *
     * GET /api/weather/forecast?city=北京&days=7
     */
    @GetMapping("/forecast")
    public ApiResponse<Map<String, Object>> forecast(HttpServletRequest req,
                                                     @RequestParam(required = false) String city,
                                                     @RequestParam(defaultValue = "7") int days) {
        authService.requireUser(token(req));
        String c = normalizeCity(city);
        int d = ViewUtil.clamp(days, 1, 15);

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = 0; i < d; i++) {
            LocalDate date = today.plusDays(i);
            Map<String, Object> w = buildWeather(c, date);
            w.put("date", date.toString());
            w.put("weekday", weekday(date));
            w.put("dayOffset", i);
            // 只保留预报需要的字段
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", w.get("date"));
            item.put("weekday", w.get("weekday"));
            item.put("dayOffset", i);
            item.put("weather", w.get("weather"));
            item.put("weatherText", w.get("weatherText"));
            item.put("icon", w.get("icon"));
            item.put("tempMin", w.get("tempMin"));
            item.put("tempMax", w.get("tempMax"));
            item.put("humidity", w.get("humidity"));
            item.put("windLevel", w.get("windLevel"));
            item.put("windDirection", w.get("windDirection"));
            item.put("precipitationChance", w.get("precipitationChance"));
            item.put("uvIndex", w.get("uvIndex"));
            list.add(item);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", c);
        data.put("mock", true);
        data.put("source", "seasonal-model");
        data.put("days", d);
        data.put("list", list);
        data.put("generatedAt", LocalDateTime.now().toString());
        data.put("notice", "未接入第三方天气服务，数据为基于季节与时间的模拟值");
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ③ 统一天气
    // ========================================================================

    /**
     * 统一天气
     *
     * GET /api/weather/unified?city=北京
     *
     * 一次返回实时 + 3 天预报 + 生活指数 + 健康建议，减少前端请求次数。
     */
    @GetMapping("/unified")
    public ApiResponse<Map<String, Object>> unified(HttpServletRequest req,
                                                    @RequestParam(required = false) String city) {
        User me = authService.requireUser(token(req));
        String c = normalizeCity(city);

        LocalDate today = LocalDate.now();
        Map<String, Object> current = buildWeather(c, today);
        current.put("updateTime", LocalDateTime.now().toString());

        List<Map<String, Object>> forecast = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            LocalDate date = today.plusDays(i);
            Map<String, Object> w = buildWeather(c, date);
            forecast.add(ViewUtil.map(
                    "date", date.toString(),
                    "weekday", weekday(date),
                    "weather", w.get("weather"),
                    "weatherText", w.get("weatherText"),
                    "icon", w.get("icon"),
                    "tempMin", w.get("tempMin"),
                    "tempMax", w.get("tempMax"),
                    "precipitationChance", w.get("precipitationChance")
            ));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", c);
        data.put("mock", true);
        data.put("source", "seasonal-model");
        data.put("current", current);
        data.put("forecast", forecast);
        data.put("indices", buildIndices(current));
        data.put("advice", buildAdvice(current, me.getId()));
        data.put("generatedAt", LocalDateTime.now().toString());
        data.put("notice", "未接入第三方天气服务，数据为基于季节与时间的模拟值");
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ④ 天气健康建议
    // ========================================================================

    /**
     * 天气健康建议
     *
     * GET /api/weather/advice?city=北京
     *
     * 纯规则：温度 / 湿度 / 温差 / 降水 → 穿衣、出行、健康提醒。
     */
    @GetMapping("/advice")
    public ApiResponse<Map<String, Object>> advice(HttpServletRequest req,
                                                   @RequestParam(required = false) String city) {
        User me = authService.requireUser(token(req));
        String c = normalizeCity(city);

        Map<String, Object> w = buildWeather(c, LocalDate.now());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("city", c);
        data.put("mock", true);
        data.put("weather", ViewUtil.map(
                "weatherText", w.get("weatherText"),
                "temperature", w.get("temperature"),
                "tempMin", w.get("tempMin"),
                "tempMax", w.get("tempMax"),
                "humidity", w.get("humidity")
        ));
        data.put("advice", buildAdvice(w, me.getId()));
        data.put("indices", buildIndices(w));
        data.put("generatedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 天气模型
    // ========================================================================

    /**
     * 基于季节与城市纬度的确定性天气模型
     *
     * 用「一年中的第几天」作为正弦波相位，得到季节性的基准温度；
     * 再用城市纬度做南北修正，最后叠加一个由日期哈希产生的小幅扰动，
     * 保证同一城市同一天的天气是稳定的（不会每次刷新都变），
     * 且相邻日期之间平滑过渡。
     */
    private Map<String, Object> buildWeather(String city, LocalDate date) {
        int dayOfYear = date.getDayOfYear();
        double latitude = latitudeOf(city);

        // ---------- 基准温度：7 月中下旬最热，1 月中下旬最冷 ----------
        // 相位取 200 天（约 7 月 19 日）为峰值
        double phase = (dayOfYear - 200) / 365.0 * 2 * Math.PI;
        double seasonal = Math.cos(phase);       // -1（最冷）~ 1（最热）

        // 纬度越高，年温差越大、整体越冷
        double annualMean = 26 - (latitude - 20) * 0.55;   // 纬度 20° 年均 26℃，每高 1° 降 0.55℃
        double amplitude = 8 + (latitude - 20) * 0.28;     // 纬度 20° 振幅 8℃，每高 1° 增 0.28℃

        double baseTemp = annualMean + seasonal * amplitude;

        // ---------- 日间扰动：用日期哈希保证同一天稳定 ----------
        int seed = Math.abs((city + date).hashCode());
        double jitter = ((seed % 100) / 100.0 - 0.5) * 5;   // ±2.5℃

        double avgTemp = baseTemp + jitter;

        // 日内温差：晴天温差大，阴雨天温差小
        boolean rainy = (seed / 100) % 100 < rainChance(date, latitude);
        double dayRange = rainy ? 5 : 9;

        double tempMax = avgTemp + dayRange / 2;
        double tempMin = avgTemp - dayRange / 2;

        // ---------- 湿度 ----------
        // 夏季多雨地区更潮，冬季北方干燥
        double humidityBase = 60 + seasonal * 12 - (latitude - 25) * 0.4;
        int humidity = (int) Math.round(ViewUtil.clamp(
                (int) (humidityBase + (seed % 20) - 10), 20, 95));

        // ---------- 天气现象 ----------
        String weather = rainy ? (avgTemp < 2 ? "snow" : "rain") : ((seed % 10) < 3 ? "cloudy" : "sunny");

        // ---------- 风 ----------
        int windLevel = 1 + (seed / 7) % 5;                 // 1-5 级
        String[] dirs = {"北风", "东北风", "东风", "东南风", "南风", "西南风", "西风", "西北风"};
        String windDirection = dirs[(seed / 13) % dirs.length];

        // ---------- 紫外线 ----------
        int uvIndex;
        if ("rain".equals(weather) || "snow".equals(weather)) uvIndex = 1;
        else if ("cloudy".equals(weather)) uvIndex = 4;
        else uvIndex = (int) Math.round(ViewUtil.clamp((int) (2 + seasonal * 5), 1, 11));

        int precipChance = rainy ? 55 + (seed % 40) : (seed % 25);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("city", city);
        m.put("mock", true);
        m.put("source", "seasonal-model");
        m.put("date", date.toString());
        m.put("weekday", weekday(date));
        m.put("weather", weather);
        m.put("weatherText", weatherText(weather));
        m.put("icon", weather);
        m.put("temperature", ViewUtil.round(avgTemp, 1));
        m.put("tempMax", ViewUtil.round(tempMax, 1));
        m.put("tempMin", ViewUtil.round(tempMin, 1));
        m.put("feelsLike", ViewUtil.round(feelsLike(avgTemp, humidity, windLevel), 1));
        m.put("humidity", humidity);
        m.put("windLevel", windLevel);
        m.put("windDirection", windDirection);
        m.put("windSpeed", ViewUtil.round(windLevel * 3.5, 1));
        m.put("precipitationChance", precipChance);
        m.put("uvIndex", uvIndex);
        m.put("uvText", uvText(uvIndex));
        m.put("pressure", 1013 + (seed % 15) - 7);
        m.put("visibility", "rain".equals(weather) ? 5 : 15);
        return m;
    }

    /** 降水概率（按月份估计） */
    private int rainChance(LocalDate date, double latitude) {
        int month = date.getMonthValue();
        // 夏季（6-8 月）与南方地区降水更多
        int base = switch (month) {
            case 6, 7, 8 -> 45;
            case 4, 5, 9 -> 32;
            case 3, 10 -> 22;
            default -> 12;
        };
        // 纬度低于 30° 的南方地区加成
        if (latitude < 30) base += 12;
        return base;
    }

    /** 体感温度：湿度高更闷热，风大更冷 */
    private double feelsLike(double temp, int humidity, int windLevel) {
        double feels = temp;
        if (temp > 26) {
            feels += (humidity - 50) * 0.06;      // 高温高湿更热
        } else if (temp < 10) {
            feels -= windLevel * 0.8;             // 低温有风更冷
        }
        return feels;
    }

    /** 生活指数 */
    private Map<String, Object> buildIndices(Map<String, Object> w) {
        double temp = ViewUtil.toDouble(w.get("temperature"), 20);
        int humidity = ViewUtil.toInt(w.get("humidity"), 50);
        int uv = ViewUtil.toInt(w.get("uvIndex"), 3);
        int wind = ViewUtil.toInt(w.get("windLevel"), 2);
        String weather = ViewUtil.toStr(w.get("weather"), "sunny");

        Map<String, Object> indices = new LinkedHashMap<>();

        // 穿衣
        String dress;
        String dressLevel;
        if (temp >= 30) { dress = "短袖短裤，注意防晒"; dressLevel = "炎热"; }
        else if (temp >= 25) { dress = "短袖薄衫即可"; dressLevel = "热"; }
        else if (temp >= 18) { dress = "长袖衬衫，早晚可加薄外套"; dressLevel = "舒适"; }
        else if (temp >= 10) { dress = "毛衣加外套"; dressLevel = "较凉"; }
        else if (temp >= 0) { dress = "厚外套、棉服"; dressLevel = "冷"; }
        else { dress = "羽绒服、帽子手套，注意保暖"; dressLevel = "严寒"; }
        indices.put("dress", ViewUtil.map("level", dressLevel, "text", dress));

        // 出行
        String travel;
        if ("rain".equals(weather)) travel = "有雨，记得带伞，路面湿滑请慢行";
        else if ("snow".equals(weather)) travel = "有雪，注意防滑，尽量减少外出";
        else if (wind >= 5) travel = "风力较大，外出注意安全";
        else if (temp >= 33) travel = "天气炎热，建议避开正午时段外出";
        else if (temp <= -5) travel = "天气寒冷，外出做好保暖";
        else travel = "天气适宜出行";
        indices.put("travel", ViewUtil.map("level", "rain".equals(weather) ? "不便" : "适宜", "text", travel));

        // 紫外线
        indices.put("uv", ViewUtil.map("level", uvText(uv),
                "text", uv >= 7 ? "紫外线强，外出请涂抹防晒霜、戴帽子"
                        : uv >= 4 ? "紫外线中等，适当防晒" : "紫外线较弱，无需特别防护"));

        // 空气湿度
        String humidText = humidity >= 80 ? "空气潮湿，注意关节保暖防潮"
                : humidity <= 30 ? "空气干燥，记得多喝水、注意皮肤保湿"
                : "湿度适宜";
        indices.put("humidity", ViewUtil.map("level", humidity >= 80 ? "潮湿"
                : humidity <= 30 ? "干燥" : "适宜", "text", humidText));

        // 运动
        String sport;
        if ("rain".equals(weather) || "snow".equals(weather)) sport = "有降水，建议室内活动";
        else if (temp >= 33 || temp <= 0) sport = "气温不适，建议室内运动";
        else if (uv >= 8) sport = "紫外线强，建议清晨或傍晚运动";
        else sport = "适合户外散步、打太极";
        indices.put("sport", ViewUtil.map("level", "适宜", "text", sport));

        return indices;
    }

    /**
     * 天气健康建议（规则生成）
     *
     * 结合温度、湿度、温差、降水与老人健康档案给出针对性提示。
     */
    private Map<String, Object> buildAdvice(Map<String, Object> w, String userId) {
        double temp = ViewUtil.toDouble(w.get("temperature"), 20);
        double tempMax = ViewUtil.toDouble(w.get("tempMax"), temp);
        double tempMin = ViewUtil.toDouble(w.get("tempMin"), temp);
        double range = tempMax - tempMin;
        int humidity = ViewUtil.toInt(w.get("humidity"), 50);
        String weather = ViewUtil.toStr(w.get("weather"), "sunny");

        List<String> tips = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ---------- 穿衣 ----------
        if (temp <= 5) {
            tips.add("气温很低（" + ViewUtil.round(temp, 0) + "℃），出门务必穿厚外套、戴帽子和围巾，"
                    + "老年人对寒冷更敏感，注意护住头颈和膝盖。");
        } else if (temp <= 15) {
            tips.add("天气转凉（" + ViewUtil.round(temp, 0) + "℃），建议穿毛衣加外套，早晚温差大注意添衣。");
        } else if (temp <= 26) {
            tips.add("气温舒适（" + ViewUtil.round(temp, 0) + "℃），适合外出散步，建议穿长袖或薄外套。");
        } else if (temp <= 33) {
            tips.add("天气较热（" + ViewUtil.round(temp, 0) + "℃），穿轻薄透气的衣服，多喝水。");
        } else {
            warnings.add("高温天气（" + ViewUtil.round(temp, 0) + "℃），尽量避免中午外出，"
                    + "室内注意通风降温，警惕中暑。");
        }

        // ---------- 温差 ----------
        if (range >= 10) {
            tips.add(String.format("今日昼夜温差达 %.0f℃，早晚记得加衣服，避免着凉。", range));
        }

        // ---------- 降水 ----------
        if ("rain".equals(weather)) {
            tips.add("今天有雨，出门带好雨伞，穿防滑的鞋子，尽量走平整路面。");
            warnings.add("雨天路滑，是老年人跌倒高发时段，建议减少外出。");
        } else if ("snow".equals(weather)) {
            warnings.add("雨雪天气路面结冰，跌倒风险高，建议尽量不外出；如需外出请家属陪同。");
        }

        // ---------- 湿度 ----------
        if (humidity >= 85) {
            tips.add("空气潮湿（湿度 " + humidity + "%），有关节不适的话注意保暖防潮。");
        } else if (humidity <= 30) {
            tips.add("空气干燥（湿度 " + humidity + "%），记得多喝温水，可用加湿器改善室内湿度。");
        }

        // ---------- 风 ----------
        int wind = ViewUtil.toInt(w.get("windLevel"), 2);
        if (wind >= 5) {
            tips.add("风力较大（" + wind + " 级），外出注意防风，避免在广告牌、树下逗留。");
        }

        // ---------- 健康档案个性化 ----------
        try {
            var info = elderInfoMapper.selectByUser(userId);
            if (info != null) {
                String diseases = info.getChronicDiseases();
                if (diseases != null && diseases.contains("高血压")) {
                    if (temp <= 5) {
                        warnings.add("你有高血压，寒冷会使血压升高，务必注意保暖并按时服药。");
                    } else if (temp >= 32) {
                        warnings.add("你有高血压，高温天气注意补水，避免长时间户外活动。");
                    } else {
                        tips.add("你有高血压，记得每天定时测量血压并按时服药。");
                    }
                }
                if (diseases != null && diseases.contains("关节")) {
                    if (humidity >= 80 || temp <= 10) {
                        tips.add("你有风湿/关节问题，阴冷天注意关节保暖，可用热敷缓解。");
                    }
                }
                if (diseases != null && (diseases.contains("哮喘") || diseases.contains("呼吸"))) {
                    if ("rain".equals(weather) || tempMin <= 5) {
                        warnings.add("你有呼吸系统疾病，冷空气与降雨易诱发不适，外出建议戴口罩。");
                    }
                }
                if (diseases != null && diseases.contains("心脏")) {
                    if (range >= 10 || temp <= 5) {
                        warnings.add("你有心脏方面的问题，气温骤变时注意保暖，避免清晨过早外出锻炼。");
                    }
                }
            }
        } catch (Exception e) {
            log.debug("读取健康档案失败（建议降级为通用）: {}", e.getMessage());
        }

        if (tips.isEmpty()) {
            tips.add("今天天气不错，适合出门走走晒晒太阳，记得按时喝水。");
        }

        // ---------- 综合等级 ----------
        String level = warnings.size() >= 2 ? "high" : warnings.isEmpty() ? "low" : "medium";

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("level", level);
        out.put("levelText", switch (level) {
            case "high" -> "需特别注意";
            case "medium" -> "需留意";
            default -> "天气适宜";
        });
        out.put("tips", tips);
        out.put("warnings", warnings);
        out.put("summary", warnings.isEmpty()
                ? "今天天气对长辈比较友好，正常安排起居即可。"
                : String.join(" ", warnings));
        return out;
    }

    // ========================================================================
    // 工具
    // ========================================================================

    /** 城市名归一化；空值取默认城市 */
    private String normalizeCity(String city) {
        String c = ViewUtil.toStr(city);
        return c == null ? "北京" : c;
    }

    /**
     * 城市纬度查表
     *
     * 未命中时返回 35°（中国中部的代表纬度），保证模型仍可用。
     */
    private double latitudeOf(String city) {
        if (city == null) return 35;
        return switch (city) {
            case "北京" -> 39.9;
            case "上海" -> 31.2;
            case "广州" -> 23.1;
            case "深圳" -> 22.5;
            case "天津" -> 39.1;
            case "重庆" -> 29.6;
            case "成都" -> 30.7;
            case "杭州" -> 30.3;
            case "南京" -> 32.1;
            case "武汉" -> 30.6;
            case "西安" -> 34.3;
            case "长沙" -> 28.2;
            case "郑州" -> 34.7;
            case "青岛" -> 36.1;
            case "沈阳" -> 41.8;
            case "哈尔滨" -> 45.8;
            case "长春" -> 43.9;
            case "济南" -> 36.7;
            case "合肥" -> 31.8;
            case "福州" -> 26.1;
            case "厦门" -> 24.5;
            case "南昌" -> 28.7;
            case "昆明" -> 25.0;
            case "贵阳" -> 26.6;
            case "南宁" -> 22.8;
            case "海口" -> 20.0;
            case "兰州" -> 36.1;
            case "西宁" -> 36.6;
            case "银川" -> 38.5;
            case "乌鲁木齐" -> 43.8;
            case "拉萨" -> 29.7;
            case "呼和浩特" -> 40.8;
            case "太原" -> 37.9;
            case "石家庄" -> 38.0;
            case "苏州" -> 31.3;
            case "无锡" -> 31.6;
            case "宁波" -> 29.9;
            case "温州" -> 28.0;
            case "佛山" -> 23.0;
            case "东莞" -> 23.0;
            case "珠海" -> 22.3;
            case "香港" -> 22.3;
            case "澳门" -> 22.2;
            case "台北" -> 25.0;
            default -> 35;
        };
    }

    private String weekday(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    private String weatherText(String weather) {
        return switch (weather) {
            case "sunny" -> "晴";
            case "cloudy" -> "多云";
            case "rain" -> "小雨";
            case "snow" -> "小雪";
            default -> "晴";
        };
    }

    private String uvText(int uv) {
        if (uv <= 2) return "最弱";
        if (uv <= 4) return "弱";
        if (uv <= 6) return "中等";
        if (uv <= 9) return "强";
        return "很强";
    }

    /** 当前时段问候语（用于天气播报） */
    @SuppressWarnings("unused")
    private String greeting() {
        int h = LocalTime.now().getHour();
        if (h < 6) return "凌晨好";
        if (h < 12) return "早上好";
        if (h < 14) return "中午好";
        if (h < 18) return "下午好";
        return "晚上好";
    }

    /** 预留缓存读取（当前 buildWeather 足够快，仅接入真实 API 后需要） */
    @SuppressWarnings("unused")
    private String cacheGet(String key) {
        try {
            return redis.opsForValue().get(CACHE_KEY + key);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private void cachePut(String key, String value) {
        try {
            redis.opsForValue().set(CACHE_KEY + key, value, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("天气缓存写入失败（忽略）: {}", e.getMessage());
        }
    }
}
