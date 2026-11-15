package com.guardian.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.DietRecord;
import com.guardian.entity.ElderInfo;
import com.guardian.mapper.DietRecordMapper;
import com.guardian.mapper.ElderInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 饮食服务
 *
 * 负责饮食记录、餐次汇总、营养分析、饮食偏好与建议。
 *
 * 说明：
 *   - 饮食偏好存在 elder_info.diet_preference（JSON 字段），不额外建表。
 *   - advice 为纯规则引擎，不调用 AI，保证离线可用与响应速度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DietService {

    private final DietRecordMapper dietMapper;
    private final ElderInfoMapper elderInfoMapper;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** 四个餐次，固定顺序便于前端渲染 */
    private static final List<String> MEAL_TYPES = List.of("breakfast", "lunch", "dinner", "snack");

    /** 每日热量参考区间（kcal） */
    private static final int CALORIE_LOW = 1500;
    private static final int CALORIE_HIGH = 2500;
    /** 老年人蛋白质推荐量（g/天） */
    private static final int PROTEIN_TARGET = 60;

    private Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String mealText(String mealType) {
        return switch (mealType == null ? "" : mealType) {
            case "breakfast" -> "早餐";
            case "lunch" -> "午餐";
            case "dinner" -> "晚餐";
            case "snack" -> "加餐";
            default -> "其他";
        };
    }

    // ========================================================================
    // 记录
    // ========================================================================

    /**
     * 新增饮食记录
     *
     * 请求体：{ "mealType": "breakfast", "foods": ["鸡蛋","牛奶"],
     *          "calories": 320, "protein": 18, "carbs": 30, "fat": 12,
     *          "mealTime": "08:10", "date": "2026-02-14", "notes": "" }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addRecord(String userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        // 兼容 mealType / type 两种字段名
        String mealType = str(body.getOrDefault("mealType", body.get("type")));
        if (mealType == null) {
            throw BizException.badRequest("请选择餐次");
        }
        if (!MEAL_TYPES.contains(mealType)) {
            throw BizException.badRequest("餐次必须是 breakfast/lunch/dinner/snack 之一");
        }

        // foods 支持数组或字符串
        String foodsJson = normalizeFoods(body.get("foods"));
        if (foodsJson == null) {
            throw BizException.badRequest("请至少填写一种食物");
        }

        LocalDate date = parseDate(body.get("date"));
        String mealTime = str(body.get("mealTime"));
        if (mealTime == null) {
            mealTime = LocalTime.now().format(HHMM);
        }

        DietRecord r = new DietRecord();
        r.setId(CommonUtil.uuid());
        r.setUserId(userId);
        r.setDate(date);
        r.setMealType(mealType);
        r.setMealTime(mealTime);
        r.setFoods(foodsJson);
        r.setCalories(nz(toInt(body.get("calories"))));
        r.setProtein(nz(toInt(body.get("protein"))));
        r.setCarbs(nz(toInt(body.get("carbs"))));
        r.setFat(nz(toInt(body.get("fat"))));
        r.setNotes(str(body.get("notes")));

        dietMapper.insert(r);
        log.info("用户 {} 新增饮食记录 {}（{}，{} kcal）", userId, r.getId(), mealType, r.getCalories());

        return toView(r);
    }

    // ========================================================================
    // 查询
    // ========================================================================

    /**
     * 今日各餐次记录
     *
     * 返回 meals（四个餐次）+ 汇总热量 + 已记录餐次数。
     */
    public Map<String, Object> today(String userId) {
        LocalDate today = LocalDate.now();
        List<DietRecord> records = dietMapper.selectByUserAndDate(userId, today);

        // 按餐次归集：snack 可能有多条，因此用 List
        Map<String, List<Map<String, Object>>> meals = new LinkedHashMap<>();
        for (String t : MEAL_TYPES) {
            meals.put(t, new ArrayList<>());
        }

        int totalCalories = 0;
        int totalProtein = 0, totalCarbs = 0, totalFat = 0;

        for (DietRecord r : records) {
            String type = r.getMealType() == null ? "snack" : r.getMealType();
            meals.computeIfAbsent(type, k -> new ArrayList<>()).add(toView(r));
            totalCalories += nz(r.getCalories());
            totalProtein += nz(r.getProtein());
            totalCarbs += nz(r.getCarbs());
            totalFat += nz(r.getFat());
        }

        // 主餐是否已记录（snack 不计入完成度）
        long completed = meals.entrySet().stream()
                .filter(e -> !"snack".equals(e.getKey()))
                .filter(e -> !e.getValue().isEmpty())
                .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.toString());
        data.put("meals", meals);
        data.put("mealTexts", MEAL_TEXTS);
        data.put("totalCalories", totalCalories);
        data.put("totalProtein", totalProtein);
        data.put("totalCarbs", totalCarbs);
        data.put("totalFat", totalFat);
        data.put("recordCount", records.size());
        data.put("completedMeals", completed);
        data.put("targetMeals", 3);
        data.put("completed", completed >= 3);
        return data;
    }

    /** 区间历史记录（默认最近 7 天） */
    public Map<String, Object> history(String userId, int days, int page, int pageSize) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(d - 1L);

        List<DietRecord> records = dietMapper.selectByUserAndRange(userId, from, to);

        List<Map<String, Object>> all = new ArrayList<>();
        for (DietRecord r : records) {
            all.add(toView(r));
        }

        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 200));
        int start = Math.min((p - 1) * size, all.size());
        int end = Math.min(start + size, all.size());
        long total = all.size();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", all.subList(start, end));
        data.put("total", total);
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", (long) p * size < total);
        data.put("from", from.toString());
        data.put("to", to.toString());
        return data;
    }

    /**
     * 近 7 天逐日汇总
     *
     * 无记录的日期也返回（补 0），前端画折线图不会断档。
     */
    public Map<String, Object> weekly(String userId) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);

        List<DietRecord> records = dietMapper.selectByUserAndRange(userId, from, today);

        // 日期 -> 当天记录
        Map<LocalDate, List<DietRecord>> byDate = new HashMap<>();
        for (DietRecord r : records) {
            byDate.computeIfAbsent(r.getDate(), k -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> weekData = new ArrayList<>();
        int totalCalories = 0;
        int recordedDays = 0;

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<DietRecord> dayRecords = byDate.getOrDefault(date, List.of());

            int calories = dayRecords.stream().mapToInt(r -> nz(r.getCalories())).sum();
            int protein = dayRecords.stream().mapToInt(r -> nz(r.getProtein())).sum();
            int carbs = dayRecords.stream().mapToInt(r -> nz(r.getCarbs())).sum();
            int fat = dayRecords.stream().mapToInt(r -> nz(r.getFat())).sum();

            totalCalories += calories;
            if (!dayRecords.isEmpty()) recordedDays++;

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date.toString());
            day.put("weekday", weekdayText(date));
            day.put("calories", calories);
            day.put("protein", protein);
            day.put("carbs", carbs);
            day.put("fat", fat);
            day.put("meals", dayRecords.size());
            weekData.add(day);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekData", weekData);
        data.put("totalCalories", totalCalories);
        data.put("avgCaloriesPerDay", Math.round(totalCalories / 7.0));
        data.put("recordedDays", recordedDays);
        data.put("from", from.toString());
        data.put("to", today.toString());
        return data;
    }

    /**
     * 营养汇总
     *
     * 今日 + 近 7 天，用 DietRecordMapper.sumNutrition 直接下推数据库求和。
     */
    public Map<String, Object> nutrition(String userId, int days) {
        LocalDate today = LocalDate.now();
        int d = Math.max(1, Math.min(days, 90));

        // 今日
        Map<String, Object> todaySum = dietMapper.sumNutrition(userId, today, today);
        // 区间
        LocalDate from = today.minusDays(d - 1L);
        Map<String, Object> rangeSum = dietMapper.sumNutrition(userId, from, today);

        int todayCalories = num(todaySum, "calories");
        int rangeCalories = num(rangeSum, "calories");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayCalories", todayCalories);
        data.put("todayProtein", num(todaySum, "protein"));
        data.put("todayCarbs", num(todaySum, "carbs"));
        data.put("todayFat", num(todaySum, "fat"));

        data.put("days", d);
        data.put("rangeCalories", rangeCalories);
        data.put("rangeProtein", num(rangeSum, "protein"));
        data.put("rangeCarbs", num(rangeSum, "carbs"));
        data.put("rangeFat", num(rangeSum, "fat"));
        data.put("avgDailyCalories", Math.round(rangeCalories / (double) d));

        // 三大营养素供能比（蛋白 4 / 碳水 4 / 脂肪 9 kcal per g）
        int p = num(rangeSum, "protein");
        int c = num(rangeSum, "carbs");
        int f = num(rangeSum, "fat");
        double pKcal = p * 4.0, cKcal = c * 4.0, fKcal = f * 9.0;
        double sum = pKcal + cKcal + fKcal;

        Map<String, Object> ratio = new LinkedHashMap<>();
        if (sum <= 0) {
            ratio.put("protein", 0);
            ratio.put("carbs", 0);
            ratio.put("fat", 0);
        } else {
            ratio.put("protein", Math.round(pKcal / sum * 100));
            ratio.put("carbs", Math.round(cKcal / sum * 100));
            ratio.put("fat", Math.round(fKcal / sum * 100));
        }
        data.put("energyRatio", ratio);

        data.put("targetCalories", 1800);
        data.put("targetProtein", PROTEIN_TARGET);
        return data;
    }

    /**
     * 饮食统计
     *
     * 记录天数、平均热量、餐次分布等。
     */
    public Map<String, Object> stats(String userId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(d - 1L);

        Map<String, Object> sum = dietMapper.statsByRange(userId, from, today);
        List<Map<String, Object>> byMeal = dietMapper.statsByMealType(userId, from, today);

        int totalCalories = num(sum, "calories");
        int recordDays = num(sum, "days");
        int meals = num(sum, "meals");

        // 餐次分布
        Map<String, Object> mealDistribution = new LinkedHashMap<>();
        for (String t : MEAL_TYPES) {
            mealDistribution.put(t, 0);
        }
        for (Map<String, Object> row : byMeal) {
            Object type = row.get("mealType");
            if (type != null) {
                mealDistribution.put(String.valueOf(type), num(row, "meals"));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", d);
        data.put("recordDays", recordDays);
        data.put("recordRate", Math.round(recordDays * 100.0 / d));
        data.put("mealCount", meals);
        data.put("totalCalories", totalCalories);
        data.put("avgCaloriesPerDay", Math.round(totalCalories / (double) d));
        data.put("avgCaloriesPerMeal", meals == 0 ? 0 : Math.round(totalCalories / (double) meals));
        data.put("avgMealsPerDay", recordDays == 0 ? 0 : Math.round(meals * 10.0 / recordDays) / 10.0);
        data.put("totalProtein", num(sum, "protein"));
        data.put("totalCarbs", num(sum, "carbs"));
        data.put("totalFat", num(sum, "fat"));
        data.put("mealDistribution", mealDistribution);
        data.put("from", from.toString());
        data.put("to", today.toString());
        return data;
    }

    // ========================================================================
    // 偏好
    // ========================================================================

    /** 读取饮食偏好（存在 elder_info.diet_preference） */
    public Map<String, Object> getPreferences(String userId) {
        ElderInfo info = elderInfoMapper.selectByUser(userId);
        if (info == null || info.getDietPreference() == null || info.getDietPreference().isBlank()) {
            Map<String, Object> defaults = defaultPreferences();
            defaults.put("userId", userId);
            defaults.put("configured", false);
            return defaults;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("configured", true);
        data.put("raw", info.getDietPreference());

        // 把 JSON 字段展开成扁平结构
        try {
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(info.getDietPreference());
            for (String key : obj.keySet()) {
                data.put(key, obj.get(key));
            }
        } catch (Exception e) {
            log.warn("饮食偏好解析失败: {}", e.getMessage());
        }
        return data;
    }

    /**
     * 保存饮食偏好
     *
     * elder_info 不存在时自动建档，保证首次设置也能成功。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> setPreferences(String userId, Object preferences) {
        if (preferences == null) {
            throw BizException.badRequest("请填写饮食偏好");
        }

        String json;
        if (preferences instanceof String s) {
            if (s.isBlank()) {
                throw BizException.badRequest("请填写饮食偏好");
            }
            json = s;
        } else {
            json = JSONUtil.toJsonStr(preferences);
        }

        ElderInfo info = elderInfoMapper.selectByUser(userId);
        if (info == null) {
            // 首次设置偏好时自动创建健康档案
            info = new ElderInfo();
            info.setId(CommonUtil.uuid());
            info.setUserId(userId);
            info.setDietPreference(json);
            elderInfoMapper.insert(info);
            log.info("用户 {} 首次设置饮食偏好，已自动创建健康档案", userId);
        } else {
            info.setDietPreference(json);
            elderInfoMapper.update(info);
        }

        log.info("用户 {} 更新饮食偏好", userId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("saved", true);
        data.put("raw", json);
        return data;
    }

    // ========================================================================
    // 建议（纯规则）
    // ========================================================================

    /**
     * 饮食建议
     *
     * 依据当日与近 7 天摄入量做规则判断，不调用 AI。
     */
    public Map<String, Object> advice(String userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        Map<String, Object> todaySum = dietMapper.sumNutrition(userId, today, today);
        Map<String, Object> weekSum = dietMapper.sumNutrition(userId, weekAgo, today);

        int todayCalories = num(todaySum, "calories");
        int todayProtein = num(todaySum, "protein");
        int todayFat = num(todaySum, "fat");
        int weekCalories = num(weekSum, "calories");
        int avgDaily = Math.round(weekCalories / 7.0f);

        List<String> tips = new ArrayList<>();
        List<String> level = new ArrayList<>();

        if (todayCalories == 0) {
            level.add("none");
            tips.add("今天还没有饮食记录，记得每餐都记录一下，方便了解营养摄入");
        } else if (todayCalories < CALORIE_LOW) {
            level.add("low");
            tips.add(String.format("今日已摄入 %d kcal，偏低，建议适当增加主食和优质蛋白", todayCalories));
        } else if (todayCalories > CALORIE_HIGH) {
            level.add("high");
            tips.add(String.format("今日已摄入 %d kcal，偏高，建议控制油脂和精制糖", todayCalories));
        } else {
            level.add("normal");
            tips.add(String.format("今日热量摄入 %d kcal，处于合理区间，继续保持", todayCalories));
        }

        if (todayProtein > 0 && todayProtein < PROTEIN_TARGET) {
            tips.add(String.format("蛋白质 %d g，未达推荐量 %d g，可补充鸡蛋、牛奶、豆制品或鱼肉",
                    todayProtein, PROTEIN_TARGET));
        }
        if (todayFat > todayCalories * 0.35 && todayCalories > 0) {
            tips.add("脂肪供能占比偏高，建议少油烹饪，多用蒸煮");
        }
        if (avgDaily > 0 && avgDaily < CALORIE_LOW) {
            tips.add("近一周日均热量偏低，注意规律进餐，避免体重下降");
        }

        // 结合偏好（如低盐、糖尿病等）补充提示
        ElderInfo info = elderInfoMapper.selectByUser(userId);
        List<String> preferences = new ArrayList<>();
        if (info != null && info.getDietPreference() != null && !info.getDietPreference().isBlank()) {
            try {
                cn.hutool.json.JSONObject obj = JSONUtil.parseObj(info.getDietPreference());
                if (obj.getBool("lowSalt", false) || obj.getBool("low_salt", false)) {
                    preferences.add("低盐");
                    tips.add("已设置低盐饮食，建议每日食盐不超过 5 g");
                }
                if (obj.getBool("lowSugar", false) || obj.getBool("low_sugar", false)) {
                    preferences.add("低糖");
                    tips.add("已设置低糖饮食，建议少喝含糖饮料、少吃甜点");
                }
                if (obj.getBool("vegetarian", false)) {
                    preferences.add("素食");
                    tips.add("素食饮食注意补充豆制品与坚果，保证蛋白质和维生素 B12");
                }
            } catch (Exception e) {
                log.warn("饮食偏好解析失败: {}", e.getMessage());
            }
        }

        tips.add("保持规律用餐时间，三餐定时定量");
        tips.add("多吃新鲜蔬果，少油少盐少糖，适量饮水");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.toString());
        data.put("advice", tips);
        data.put("level", level);
        data.put("todayCalories", todayCalories);
        data.put("avgDailyCalories", avgDaily);
        data.put("targetCalories", 1800);
        data.put("preferences", preferences);
        data.put("recommendations", List.of(
                "每日三餐定时定量",
                "多吃新鲜蔬菜水果",
                "少油少盐少糖",
                "适量饮水"));
        return data;
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private static final Map<String, String> MEAL_TEXTS = Map.of(
            "breakfast", "早餐", "lunch", "午餐", "dinner", "晚餐", "snack", "加餐");

    /** 记录转前端视图 */
    private Map<String, Object> toView(DietRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("date", r.getDate() == null ? null : r.getDate().toString());
        m.put("mealType", r.getMealType());
        m.put("mealTypeText", mealText(r.getMealType()));
        m.put("mealTime", r.getMealTime());
        m.put("foods", parseFoods(r.getFoods()));
        m.put("calories", nz(r.getCalories()));
        m.put("protein", nz(r.getProtein()));
        m.put("carbs", nz(r.getCarbs()));
        m.put("fat", nz(r.getFat()));
        m.put("notes", r.getNotes());
        m.put("createTime", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        return m;
    }

    /** foods JSON 字符串转数组，解析失败时退化为单元素数组 */
    private Object parseFoods(String foods) {
        if (foods == null || foods.isBlank()) {
            return new ArrayList<>();
        }
        try {
            JSONArray arr = JSONUtil.parseArray(foods);
            return arr;
        } catch (Exception e) {
            return List.of(foods);
        }
    }

    /** 归一化 foods：数组 / 逗号分隔字符串 / 单值，统一存 JSON 数组 */
    private String normalizeFoods(Object foods) {
        if (foods == null) return null;

        if (foods instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    items.add(String.valueOf(o).trim());
                }
            }
            return items.isEmpty() ? null : JSONUtil.toJsonStr(items);
        }

        if (foods instanceof Object[] arr) {
            return normalizeFoods(Arrays.asList(arr));
        }

        String s = String.valueOf(foods).trim();
        if (s.isEmpty() || "null".equals(s)) return null;

        // 逗号 / 顿号分隔
        if (s.contains(",") || s.contains("，") || s.contains("、")) {
            List<String> items = new ArrayList<>();
            for (String part : s.split("[,，、]")) {
                if (!part.isBlank()) items.add(part.trim());
            }
            return items.isEmpty() ? null : JSONUtil.toJsonStr(items);
        }

        return JSONUtil.toJsonStr(List.of(s));
    }

    private LocalDate parseDate(Object o) {
        String s = str(o);
        if (s == null) return LocalDate.now();
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw BizException.badRequest("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
    }

    /** 从 MyBatis 返回的 Map 里安全取整（可能是 BigDecimal / Long / String） */
    private int num(Map<String, Object> map, String key) {
        if (map == null) return 0;
        Object v = map.get(key);
        if (v == null) return 0;
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private String weekdayText(LocalDate date) {
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

    private Map<String, Object> defaultPreferences() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("vegetarian", false);
        m.put("lowSalt", false);
        m.put("lowSugar", false);
        m.put("avoidFoods", new ArrayList<>());
        m.put("favoriteFoods", new ArrayList<>());
        return m;
    }
}
