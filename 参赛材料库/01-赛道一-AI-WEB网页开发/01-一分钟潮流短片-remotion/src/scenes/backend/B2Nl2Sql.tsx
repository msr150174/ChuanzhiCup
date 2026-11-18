import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption } from '../../components/Chrome';
import {
  ChatBubble,
  CodeBlock,
  EventStream,
  ResultBar,
  SectionTitle,
  type StreamEvent,
} from '../../components/ai';
import { C, EASE, F, enter } from '../../theme';

/**
 * B2｜NL2SQL 数据问答｜480 帧（16s）—— 全片技术密度最高的一段
 *
 * 版式：左栏 x140 w760（问题气泡 + 事件流 + 结论条），右栏 x980 w800（SQL 代码块 + 耗时徽标）
 *
 * 场景内关键帧（30fps，帧号为本场景相对帧）：
 *   0–40     SectionTitle 入场
 *   30       第一问气泡「今天有多少位老人未打卡？」
 *   60–126   SSE 事件流 5 条依次出现：plan / sql / result / answer / done
 *   96–108   右侧 SQL 逐行打出（第 6 行 LIMIT 1000 高亮强调）
 *   200      结论条 1：今日共有 23 位老人用户未完成当日打卡（共 1 条）
 *   260      第二问气泡「连续 3 天未打卡」+ 右侧 15.7s 计时徽标滚动
 *   294      结论条 2：23 位老人连续三天未打卡（共 23 条）
 *   330–374  五道护栏横向条依次弹入
 *   336      底部字幕切换
 */

/** 逐字取自分镜，顺序不可变：plan → sql → result → answer → done */
const EVENTS: StreamEvent[] = [
  { tag: 'plan', text: '已通过安全校验（自动追加 LIMIT 1000 防全表拉取）', color: C.cyan },
  { tag: 'sql', text: '生成 SQL 完成', color: C.violet },
  { tag: 'result', text: '返回 1 行', color: C.amber },
  { tag: 'answer', text: '生成中文结论', color: C.lime },
  { tag: 'done', text: '全链路结束', color: C.cyan },
];

/** 逐字取自分镜，6 行；第 6 行（下标 5）LIMIT 1000 需要强调 */
const SQL_LINES = [
  'SELECT COUNT(*) AS cnt',
  'FROM users u',
  'LEFT JOIN checkin c',
  '  ON c.user_id = u.id AND c.date = CURDATE()',
  "WHERE u.role = 'elder' AND c.id IS NULL",
  'LIMIT 1000;',
];

const GUARDS = ['只读', '表白名单', '禁 SELECT *', '强制 LIMIT 1000', '5s 超时'];

/* ============ 五道护栏的单个横条 ============ */
const GuardChip: React.FC<{ text: string; index: number }> = ({ text, index }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, 330 + index * 7, 16);

  return (
    <div
      style={{
        flex: 1,
        height: 52,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 12,
        borderRadius: 12,
        border: `1px solid ${C.cyan}44`,
        background: `${C.cyan}0d`,
        opacity: p,
        transform: `translateY(${(1 - p) * 20}px)`,
      }}
    >
      <span
        style={{
          fontFamily: F.mono,
          fontSize: 16,
          fontWeight: 700,
          letterSpacing: 1,
          color: C.cyan,
          opacity: 0.8,
        }}
      >
        {String(index + 1).padStart(2, '0')}
      </span>
      <span
        style={{
          fontFamily: F.cn,
          fontSize: 25,
          fontWeight: 600,
          color: C.ink,
          whiteSpace: 'nowrap',
        }}
      >
        {text}
      </span>
    </div>
  );
};

/* ============ 耗时徽标：15.7s 滚动 ============ */
const TimerBadge: React.FC = () => {
  const frame = useCurrentFrame();
  const p = enter(frame, 260, 16);
  // 260f 起从 0.0 滚到 15.7，302f 后定格
  const t = interpolate(frame, [262, 302], [0, 15.7], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  const bar = interpolate(frame, [262, 330], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.inOut,
  });

  return (
    <div style={{ opacity: p, transform: `translateY(${(1 - p) * 20}px)` }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div
            style={{
              width: 4,
              height: 26,
              borderRadius: 2,
              background: C.lime,
              boxShadow: `0 0 16px ${C.lime}`,
            }}
          />
          <span
            style={{
              fontFamily: F.cn,
              fontSize: 24,
              fontWeight: 500,
              letterSpacing: 2,
              color: C.dim,
            }}
          >
            端到端耗时
          </span>
        </div>
        <span
          style={{
            fontFamily: F.mono,
            fontSize: 62,
            fontWeight: 900,
            lineHeight: 0.9,
            letterSpacing: -2,
            color: C.lime,
            fontVariantNumeric: 'tabular-nums',
            textShadow: `0 0 36px ${C.lime}55`,
          }}
        >
          {t.toFixed(1)}s
        </span>
      </div>
      <div
        style={{
          marginTop: 16,
          height: 4,
          borderRadius: 2,
          background: C.line,
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            width: `${bar * 100}%`,
            height: '100%',
            background: `linear-gradient(90deg, ${C.cyan}, ${C.lime})`,
            boxShadow: `0 0 18px ${C.lime}`,
          }}
        />
      </div>
    </div>
  );
};

export const B2Nl2Sql: React.FC = () => {
  return (
    <AbsoluteFill>
      <Backdrop accent={C.violet} second={C.cyan} intensity={0.95} gridOpacity={0.055} />

      <SectionTitle
        kicker="NL2SQL"
        title="NL2SQL 数据问答"
        sub="管理员不会写 SQL，也能查数"
        from={0}
        accent={C.cyan}
        size={64}
      />

      {/* ================= 左栏 x140 w760 ================= */}

      {/* 第一问：30f */}
      <div style={{ position: 'absolute', left: 140, top: 266, width: 760 }}>
        <ChatBubble
          role="user"
          text="今天有多少位老人未打卡？"
          from={30}
          accent={C.cyan}
          maxWidth={620}
          fontSize={28}
        />
      </div>

      {/* SSE 事件流：60f 起，每 14 帧一条 */}
      <div style={{ position: 'absolute', left: 140, top: 339 }}>
        <EventStream events={EVENTS} from={60} interval={14} width={760} fontSize={20} />
      </div>

      {/* 结论条 1：200f */}
      <div style={{ position: 'absolute', left: 140, top: 645 }}>
        <ResultBar
          label="answer"
          value="今日共有 23 位老人用户未完成当日打卡（共 1 条）"
          from={200}
          accent={C.lime}
          width={760}
        />
      </div>

      {/* 第二问：260f */}
      <div style={{ position: 'absolute', left: 140, top: 721, width: 760 }}>
        <ChatBubble
          role="user"
          text="连续 3 天未打卡"
          from={260}
          accent={C.violet}
          maxWidth={620}
          fontSize={28}
        />
      </div>

      {/* 结论条 2：294f */}
      <div style={{ position: 'absolute', left: 140, top: 794 }}>
        <ResultBar
          label="answer"
          value="23 位老人连续三天未打卡（共 23 条）"
          from={294}
          accent={C.lime}
          width={760}
        />
      </div>

      {/* ================= 右栏 x980 w800 ================= */}

      {/* SQL 代码块：96f 起逐行打出，第 6 行 LIMIT 1000 高亮 */}
      <div style={{ position: 'absolute', left: 980, top: 266 }}>
        <CodeBlock
          lines={SQL_LINES}
          from={96}
          accent={C.cyan}
          title="sql · read-only"
          width={800}
          fontSize={24}
          emphasis={[5]}
        />
      </div>

      {/* 耗时徽标：260f 起滚动到 15.7s */}
      <div style={{ position: 'absolute', left: 980, top: 632, width: 800 }}>
        <TimerBadge />
      </div>

      {/* ================= 五道护栏横向条：330f 起 ================= */}
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 880,
          width: 1640,
          display: 'flex',
          alignItems: 'center',
          gap: 18,
        }}
      >
        {GUARDS.map((g, i) => (
          <GuardChip key={g} text={g} index={i} />
        ))}
      </div>

      {/* ================= 底部字幕（330 帧前 / 后） ================= */}
      <Caption text="SQL 由模型生成，护栏由代码兜底" from={30} to={338} accent={C.cyan} />
      <Caption
        text="越权提问在规则层就被拒绝，进不到执行环节"
        from={340}
        to={480}
        accent={C.cyan}
      />
    </AbsoluteFill>
  );
};
