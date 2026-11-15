package com.guardian.common;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 视图与类型转换工具
 *
 * 请求体进来的是松散的 {@code Map<String, Object>}（JSON 数字可能是 Integer / Double / BigDecimal，
 * 也可能被前端拼成字符串），数据库查出来的聚合结果同样是 Map。
 * 这里集中处理这些边界，避免每个 Controller 重复写防御性转换。
 *
 * ★ 约定：所有返回给前端的 Map 一律用 {@link LinkedHashMap} 且 key 为 camelCase，
 *   日期时间统一走 {@code toString()}（ISO-8601），由前端自行格式化。
 */
public final class ViewUtil {

    private ViewUtil() {}

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ========================================================================
    // Map 构造
    // ========================================================================

    /**
     * 构造有序 Map
     *
     * 用法：{@code ViewUtil.map("id", 1L, "name", "张三")}；不传参数即空 Map。
     * 值为 null 时该键仍会被写入（与 Map.of 不同，不会抛 NPE）。
     */
    public static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ========================================================================
    // 分页信封
    // ========================================================================

    /**
     * 标准分页结构
     *
     * { list, total, page, pageSize, hasMore }
     */
    public static Map<String, Object> page(List<?> list, long total, int page, int pageSize) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list == null ? new ArrayList<>() : list);
        data.put("total", total);
        data.put("page", page);
        data.put("pageSize", pageSize);
        data.put("hasMore", (long) page * pageSize < total);
        return data;
    }

    /** 页码归一化：>=1 */
    public static int page(Object raw, int def) {
        Integer v = toInt(raw);
        return v == null ? def : Math.max(1, v);
    }

    /** 每页条数归一化：1..100 */
    public static int pageSize(Object raw, int def) {
        Integer v = toInt(raw);
        if (v == null) return def;
        return Math.max(1, Math.min(100, v));
    }

    // ========================================================================
    // 防御式类型转换
    // ========================================================================

    /** 转 Integer，失败返回 null（兼容 "12"、"12.0"、12.7） */
    public static Integer toInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try {
            return (int) Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 转 int，失败返回默认值 */
    public static int toInt(Object o, int def) {
        Integer v = toInt(o);
        return v == null ? def : v;
    }

    /** 转 Long，失败返回 null */
    public static Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try {
            return (long) Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 转 long，失败返回默认值 */
    public static long toLong(Object o, long def) {
        Long v = toLong(o);
        return v == null ? def : v;
    }

    /** 转 Double，失败返回 null */
    public static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 转 double，失败返回默认值 */
    public static double toDouble(Object o, double def) {
        Double v = toDouble(o);
        return v == null ? def : v;
    }

    /** 转 BigDecimal（保留精度，用于写库），失败返回 null */
    public static BigDecimal toDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b;
        try {
            return new BigDecimal(String.valueOf(o).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 转 Boolean，兼容 true/false/1/0/yes/no */
    public static Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        if (o instanceof Number n) return n.intValue() != 0;
        String s = String.valueOf(o).trim().toLowerCase();
        if (s.isEmpty()) return null;
        return switch (s) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> null;
        };
    }

    /** 转 boolean，失败返回默认值 */
    public static boolean toBool(Object o, boolean def) {
        Boolean v = toBool(o);
        return v == null ? def : v;
    }

    /** 转字符串，null / 空白返回 null */
    public static String toStr(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? null : s;
    }

    /** 转字符串，null / 空白返回默认值 */
    public static String toStr(Object o, String def) {
        String s = toStr(o);
        return s == null ? def : s;
    }

    /** 转 LocalDate，支持 ISO 日期与日期时间前缀 */
    public static LocalDate toDate(Object o) {
        String s = toStr(o);
        if (s == null) return null;
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 转 LocalDateTime，支持 "yyyy-MM-dd HH:mm:ss" 与 ISO-8601 */
    public static LocalDateTime toDateTime(Object o) {
        String s = toStr(o);
        if (s == null) return null;
        try {
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            // 只有日期部分时补零点
            LocalDate d = toDate(s);
            return d == null ? null : d.atStartOfDay();
        }
    }

    /**
     * 把字符串列表转成有序去重列表
     *
     * 用于解析前端传来的 times: ["08:00","20:00"]。
     */
    public static List<String> toStrList(Object o) {
        List<String> out = new ArrayList<>();
        if (o == null) return out;

        if (o instanceof Iterable<?> it) {
            for (Object item : it) {
                String s = toStr(item);
                if (s != null && !out.contains(s)) out.add(s);
            }
            return out;
        }

        // 兼容 "08:00,20:00" 这种逗号分隔写法
        String raw = toStr(o);
        if (raw != null) {
            for (String part : raw.split(",")) {
                String s = toStr(part);
                if (s != null && !out.contains(s)) out.add(s);
            }
        }
        return out;
    }

    // ========================================================================
    // 序列化辅助
    // ========================================================================

    /** 日期转字符串，null 安全 */
    public static String dateStr(LocalDate d) {
        return d == null ? null : d.toString();
    }

    /** 日期时间转字符串，null 安全 */
    public static String dateTimeStr(LocalDateTime t) {
        return t == null ? null : t.toString();
    }

    /** 任意对象转字符串（用于 Map 里取出的日期字段），null 安全 */
    public static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /** 保留 n 位小数 */
    public static double round(double v, int scale) {
        double f = Math.pow(10, scale);
        return Math.round(v * f) / f;
    }

    /** 限幅到 [min, max] */
    public static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** 空集合判断 */
    public static boolean isEmpty(java.util.Collection<?> c) {
        return c == null || c.isEmpty();
    }
}
