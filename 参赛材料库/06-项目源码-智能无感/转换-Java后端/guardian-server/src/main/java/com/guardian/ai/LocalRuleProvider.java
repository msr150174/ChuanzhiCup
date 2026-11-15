package com.guardian.ai;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 规则库 —— 最后一层兜底
 *
 * 当 Ollama 和 Coze 都不可用时，用规则匹配给出合理的回复。
 * 这保证「AI 永远不会向用户报错」。
 *
 * 规则移植自原项目 server/src/ai/ai.service.ts 的 generateOfflineResponse()
 */
@Slf4j
@Component
public class LocalRuleProvider {

    /** 一条规则：关键词命中即返回对应回复 */
    private record Rule(String[] keywords, ReplyBuilder builder) {}

    @FunctionalInterface
    private interface ReplyBuilder {
        String build(AiContext ctx);
    }

    private final List<Rule> rules = new ArrayList<>();

    public LocalRuleProvider() {
        // ---------- 1. 问候 ----------
        rules.add(new Rule(
                new String[]{"你好", "您好", "嗨", "hi", "hello", "在吗"},
                ctx -> "你好呀~ 😊 我是你的健康守护助手小护！今天感觉怎么样？有什么想和我聊聊的吗？ 💙"
        ));

        // ---------- 2. 健康 / 身体 ----------
        // ★ 关键词刻意收窄：不要放「怎么样」这类泛词，
        //   否则「我今天睡眠怎么样」会被这条抢先命中，走不到睡眠规则。
        rules.add(new Rule(
                new String[]{"健康", "身体", "体质", "健康评分", "健康分"},
                ctx -> {
                    if (ctx.getHealthScore() != null) {
                        return String.format(
                                "根据你今天的健康数据，你的健康评分是%d分，%s 💪 今日步数%d步，继续保持！ 🌟",
                                ctx.getHealthScore(),
                                scoreComment(ctx.getHealthScore()),
                                ctx.getTodaySteps() == null ? 0 : ctx.getTodaySteps()
                        );
                    }
                    return "健康是最重要的财富！ 💪 我可以帮你记录健康数据、提醒服药、分析作息规律。想了解哪方面呢？ 📊";
                }
        ));

        // ---------- 3. 步数 / 运动 ----------
        rules.add(new Rule(
                new String[]{"步数", "运动", "走路", "锻炼", "走了多少"},
                ctx -> {
                    if (ctx.getTodaySteps() != null && ctx.getTodaySteps() > 0) {
                        return String.format(
                                "你今天已经走了%d步，%s 🏃‍♀️ 适当运动对身体很好，继续保持这个好习惯！ 💪",
                                ctx.getTodaySteps(),
                                stepComment(ctx.getTodaySteps())
                        );
                    }
                    return "运动是保持健康的好方法！ 🏃‍♀️ 我可以帮你记录每日步数和运动情况，建议每天走 6000-10000 步哦！ 💪";
                }
        ));

        // ---------- 4. 睡眠 ----------
        rules.add(new Rule(
                new String[]{"睡眠", "睡觉", "睡了", "失眠", "作息", "几点睡"},
                ctx -> {
                    if (ctx.getSleepHours() != null && ctx.getSleepHours() > 0) {
                        return String.format(
                                "你昨晚睡了 %.1f 小时，%s 😴 建议保持规律作息，每天睡 7-8 小时最佳哦！ 🌙",
                                ctx.getSleepHours(),
                                sleepComment(ctx.getSleepHours())
                        );
                    }
                    return "良好的睡眠是健康的基石！ 😴 我可以帮你分析睡眠规律，建议每天保持 7-8 小时的睡眠时间。 🌙";
                }
        ));

        // ---------- 5. 用药提醒 ----------
        rules.add(new Rule(
                new String[]{"提醒", "服药", "吃药", "用药", "药"},
                ctx -> "服药提醒是我的重要功能！ 💊 我可以帮你设置服药提醒，确保按时吃药。需要我帮你设置吗？ ⏰"
        ));

        // ---------- 6. 心情 / 情绪 ----------
        rules.add(new Rule(
                new String[]{"心情", "情绪", "压力", "累", "烦", "难过", "孤独", "不开心"},
                ctx -> "心情和情绪健康同样重要！ 💙 有什么心事可以和我说说，我会认真倾听的。有时候倾诉一下会感觉轻松很多呢~ 🌸"
        ));

        // ---------- 7. 饮食 ----------
        rules.add(new Rule(
                new String[]{"吃", "饮食", "饭", "营养", "喝"},
                ctx -> "饮食均衡对健康很重要！ 🥗 我可以帮你记录每日饮食，建议多吃蔬菜水果，少油少盐。需要我帮你记录吗？ 🍎"
        ));

        // ---------- 8. 位置 ----------
        rules.add(new Rule(
                new String[]{"位置", "在哪", "定位", "地图"},
                ctx -> "我可以帮你查看当前位置和历史轨迹 🗺️ 需要我打开地图吗？也可以设置安全区域，离开时会提醒家人哦！"
        ));

        // ---------- 9. 紧急 / SOS ----------
        rules.add(new Rule(
                new String[]{"紧急", "求救", "sos", "救命", "摔倒"},
                ctx -> "如果需要紧急求助，请点击「居家守护」页面的 SOS 按钮，长按 3 秒即可通知家人！ 🆘 我会第一时间联系你的紧急联系人。"
        ));

        // ---------- 10. 家人 ----------
        rules.add(new Rule(
                new String[]{"家人", "家属", "绑定", "儿女", "孩子"},
                ctx -> "家人绑定后，他们可以随时查看你的健康状态和位置 👨‍👩‍👧 在「个人中心 → 家庭成员」可以邀请家人绑定哦！"
        ));

        // ---------- 11. 感谢 ----------
        rules.add(new Rule(
                new String[]{"谢谢", "感谢", "辛苦", "多谢"},
                ctx -> "不客气呀~ 😊 能够帮到你我很开心！有什么其他问题随时找我聊聊哦！ 💙"
        ));

        // ---------- 12. 告别 ----------
        rules.add(new Rule(
                new String[]{"再见", "拜拜", "晚安", "bye"},
                ctx -> "好的，记得早点休息哦！ 🌙 有任何需要随时找我，我一直都在~ 💙"
        ));
    }

    /**
     * 匹配规则并生成回复
     *
     * @return 命中的回复；没有规则命中时返回通用回复
     */
    public String reply(String message, AiContext ctx) {
        if (message == null || message.isBlank()) {
            return defaultReply();
        }

        String lower = message.toLowerCase();
        for (Rule rule : rules) {
            for (String kw : rule.keywords()) {
                if (lower.contains(kw.toLowerCase())) {
                    try {
                        String reply = rule.builder().build(ctx);
                        log.debug("[规则库] 命中关键词「{}」", kw);
                        return reply;
                    } catch (Exception e) {
                        log.warn("[规则库] 规则执行失败: {}", e.getMessage());
                    }
                }
            }
        }

        return defaultReply();
    }

    /** 通用回复：介绍能力 */
    private String defaultReply() {
        return """
                我收到你的消息了~ 😊 作为你的健康守护助手，我可以帮你：

                💪 记录健康数据
                💊 设置服药提醒
                🏃‍♀️ 分析运动情况
                😴 关注睡眠质量
                💙 倾听你的心事

                有什么需要帮助的吗？""";
    }

    // ---------------- 评语生成 ----------------

    private String scoreComment(int score) {
        if (score >= 90) return "状态非常棒";
        if (score >= 75) return "状态不错哦";
        if (score >= 60) return "还可以再努力一下";
        return "要注意休息哦";
    }

    private String stepComment(int steps) {
        if (steps >= 10000) return "运动量很充足";
        if (steps >= 6000) return "真棒";
        return "可以再走走哦";
    }

    private String sleepComment(double hours) {
        if (hours >= 7 && hours <= 9) return "睡眠质量很好";
        if (hours < 6) return "睡得有点少";
        if (hours > 9) return "睡得有点多";
        return "睡眠质量对健康很重要";
    }

    // ========================================================================
    // 上下文
    // ========================================================================

    /** AI 上下文：携带用户健康数据，让回复更个性化 */
    @Data
    public static class AiContext {
        private Integer healthScore;
        private Integer todaySteps;
        private Double sleepHours;
        private Boolean medicineTaken;
        private String location;
        private String lastActivity;

        public static AiContext empty() {
            return new AiContext();
        }
    }
}
