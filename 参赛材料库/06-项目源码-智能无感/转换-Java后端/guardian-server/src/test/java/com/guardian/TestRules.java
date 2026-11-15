import com.guardian.ai.LocalRuleProvider;
import com.guardian.ai.LocalRuleProvider.AiContext;

/**
 * 直接测试规则库匹配（不走 HTTP，排除网络/序列化干扰）
 */
public class TestRules {
    public static void main(String[] args) {
        LocalRuleProvider p = new LocalRuleProvider();
        AiContext ctx = new AiContext();
        ctx.setHealthScore(39);

        String[] cases = {"你好", "健康", "我今天睡眠怎么样", "谢谢你", "步数", "吃药提醒"};

        for (String msg : cases) {
            String reply = p.reply(msg, ctx);
            boolean isDefault = reply.startsWith("我收到你的消息了");
            System.out.println("[" + (isDefault ? "默认" : "命中") + "] 「" + msg + "」 -> "
                    + reply.substring(0, Math.min(40, reply.length())).replace("\n", " "));
        }
    }
}
