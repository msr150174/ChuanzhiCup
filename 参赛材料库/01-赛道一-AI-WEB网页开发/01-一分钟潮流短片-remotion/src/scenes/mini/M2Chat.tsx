import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption } from '../../components/Chrome';
import {
  ChatBubble,
  PhoneFrame,
  ResultBar,
  SceneIndex,
  SectionTitle,
} from '../../components/ai';
import { C, EASE, F, enter } from '../../theme';

/**
 * M2｜「小护」对话 + 上下文注入（390 帧 / 13s）
 * 左：PhoneFrame 内一轮问答；右：三张上下文卡依次亮起，虚线汇聚进手机
 */

const PHONE_W = 340;
const PHONE_X = 160;
const PHONE_TOP = 300;
const PHONE_RIGHT = PHONE_X + PHONE_W; // 500
const LINK_Y = PHONE_TOP + 348; // 648
const SPINE_X = 760;
const CARD_X = 820;
const CARD_TOPS = [300, 470, 640];
const CENTERS = CARD_TOPS.map((t) => t + 75);

/* ============ 输入中 ============ */

const Typing: React.FC<{ from: number; to: number }> = ({ from, to }) => {
  const frame = useCurrentFrame();
  const a = Math.min(
    interpolate(frame, [from, from + 6], [0, 1], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    }),
    interpolate(frame, [to, to + 4], [1, 0], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    }),
  );

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, opacity: a, paddingLeft: 4 }}>
      <div
        style={{
          display: 'flex',
          gap: 5,
          padding: '10px 14px',
          borderRadius: '16px 16px 16px 4px',
          background: 'rgba(255,255,255,0.07)',
          border: `1px solid ${C.line}`,
        }}
      >
        {[0, 1, 2].map((i) => {
          const o = 0.22 + 0.78 * Math.max(0, Math.sin((frame - from) / 3 - i * 0.9));
          return (
            <div
              key={i}
              style={{ width: 6, height: 6, borderRadius: 3, background: C.cyan, opacity: o }}
            />
          );
        })}
      </div>
      <span style={{ fontFamily: F.mono, fontSize: 10, letterSpacing: 1, color: C.dimmer }}>
        生成中
      </span>
    </div>
  );
};

/* ============ 手机内对话界面 ============ */

const ChatScreen: React.FC = () => {
  const frame = useCurrentFrame();
  return (
  <div style={{ height: '100%', display: 'flex', flexDirection: 'column', fontFamily: F.cn }}>
    {/* 头部 */}
    <div
      style={{
        height: 54,
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        gap: 9,
        padding: '0 16px',
        borderBottom: `1px solid ${C.line}`,
      }}
    >
      <div
        style={{
          width: 26,
          height: 26,
          borderRadius: 13,
          background: `linear-gradient(135deg, ${C.cyan}, ${C.violet})`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: 12,
          fontWeight: 900,
          color: '#04121A',
        }}
      >
        护
      </div>
      <div>
        <div style={{ fontSize: 14, fontWeight: 800, color: C.ink, letterSpacing: 1 }}>小护</div>
        <div
          style={{ fontFamily: F.mono, fontSize: 9, letterSpacing: 1, color: C.lime, marginTop: 2 }}
        >
          ● 在线
        </div>
      </div>
    </div>

    {/* 对话区 */}
    <div
      style={{
        flex: 1,
        position: 'relative',
        padding: '16px 14px',
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      <ChatBubble
        role="user"
        text="我今天睡眠怎么样？"
        from={60}
        accent={C.cyan}
        maxWidth={220}
        fontSize={15}
      />
      <Typing from={92} to={114} />
      <ChatBubble
        role="ai"
        text="昨晚睡了 6 小时 20 分，比前一周平均少 42 分钟；健康分 78。今晚建议 22:30 前休息。"
        from={110}
        maxWidth={246}
        fontSize={15}
      />
      <div
        style={{
          fontFamily: F.mono,
          fontSize: 10,
          letterSpacing: 1,
          color: C.dimmer,
          opacity: enter(frame, 132, 14),
        }}
      >
        qwen2.5:7b · 本地
      </div>

      <div
        style={{
          position: 'absolute',
          right: 12,
          bottom: 4,
          fontFamily: F.mono,
          fontSize: 10,
          letterSpacing: 1,
          color: 'rgba(255,255,255,0.26)',
        }}
      >
        界面示意
      </div>
    </div>

    {/* 输入栏 */}
    <div
      style={{
        height: 56,
        flexShrink: 0,
        borderTop: `1px solid ${C.line}`,
        display: 'flex',
        alignItems: 'center',
        gap: 10,
        padding: '0 14px',
      }}
    >
      <div
        style={{
          flex: 1,
          height: 34,
          borderRadius: 17,
          background: 'rgba(255,255,255,0.06)',
          border: `1px solid ${C.line}`,
          display: 'flex',
          alignItems: 'center',
          padding: '0 12px',
          fontSize: 12,
          color: 'rgba(255,255,255,0.32)',
        }}
      >
        说一句话…
      </div>
      <div
        style={{
          width: 32,
          height: 32,
          borderRadius: 16,
          background: C.cyan,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <svg width="15" height="15" viewBox="0 0 24 24">
          <path d="M3 20l18-8L3 4v6l12 2-12 2z" fill="#04121A" />
        </svg>
      </div>
    </div>
  </div>
  );
};

/* ============ 上下文来源卡 ============ */

const CtxCard: React.FC<{
  n: string;
  top: number;
  title: string;
  sub: string;
  from: number;
  accent: string;
  visual: React.ReactNode;
}> = ({ n, top, title, sub, from, accent, visual }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 16);
  const on = interpolate(frame, [from + 2, from + 18], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  const bgA = Math.round((0.03 + on * 0.11) * 255)
    .toString(16)
    .padStart(2, '0');

  return (
    <div
      style={{
        position: 'absolute',
        left: CARD_X,
        top,
        width: 960,
        height: 150,
        boxSizing: 'border-box',
        display: 'flex',
        alignItems: 'center',
        gap: 26,
        padding: '0 34px',
        borderRadius: 20,
        overflow: 'hidden',
        border: `1px solid rgba(255,255,255,${0.08 + on * 0.14})`,
        background: `linear-gradient(110deg, ${accent}${bgA}, rgba(255,255,255,0.02))`,
        boxShadow: `inset 0 1px 0 rgba(255,255,255,${0.05 + on * 0.08}), 0 18px 50px rgba(0,0,0,0.45)`,
        opacity: p,
        transform: `translateX(${(1 - p) * 30}px)`,
      }}
    >
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          bottom: 0,
          width: 3,
          background: accent,
          opacity: 0.15 + on * 0.85,
          boxShadow: `0 0 18px ${accent}`,
        }}
      />
      <div
        style={{
          width: 34,
          flexShrink: 0,
          fontFamily: F.mono,
          fontSize: 15,
          letterSpacing: 1,
          color: on > 0.6 ? accent : C.dimmer,
        }}
      >
        {n}
      </div>
      <div style={{ width: 300, flexShrink: 0 }}>
        <div style={{ fontSize: 32, fontWeight: 800, letterSpacing: 1, color: C.ink }}>
          {title}
        </div>
        <div style={{ marginTop: 8, fontSize: 18, color: 'rgba(255,255,255,0.55)' }}>{sub}</div>
      </div>
      <div
        style={{
          marginLeft: 'auto',
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          opacity: 0.3 + on * 0.7,
        }}
      >
        {visual}
      </div>
    </div>
  );
};

const CheckInVisual: React.FC = () => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
    {Array.from({ length: 7 }).map((_, i) => (
      <div
        key={i}
        style={{
          width: 24,
          height: 24,
          borderRadius: 12,
          background: i < 6 ? C.lime : 'rgba(255,255,255,0.10)',
          boxShadow: i < 6 ? `0 0 14px ${C.lime}66` : 'none',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {i < 6 ? (
          <svg width="11" height="11" viewBox="0 0 24 24">
            <path d="M4 13l5 5L20 6" stroke="#04121A" strokeWidth="3.4" fill="none" strokeLinecap="round" />
          </svg>
        ) : null}
      </div>
    ))}
    <span
      style={{ fontFamily: F.mono, fontSize: 22, fontWeight: 700, color: C.lime, marginLeft: 6 }}
    >
      6/7
    </span>
  </div>
);

const SLEEP = [
  { v: '23:18', k: '入睡' },
  { v: '6h20m', k: '时长' },
  { v: '4,120', k: '步数' },
];

const SleepVisual: React.FC = () => (
  <div style={{ display: 'flex', gap: 10 }}>
    {SLEEP.map((s) => (
      <div
        key={s.k}
        style={{
          padding: '10px 16px',
          borderRadius: 12,
          border: `1px solid ${C.cyan}33`,
          background: `${C.cyan}0F`,
          textAlign: 'center',
        }}
      >
        <div style={{ fontFamily: F.mono, fontSize: 20, fontWeight: 700, color: C.cyan }}>
          {s.v}
        </div>
        <div style={{ marginTop: 3, fontSize: 11, color: C.dimmer, letterSpacing: 1 }}>{s.k}</div>
      </div>
    ))}
  </div>
);

const MEDS = [
  { t: '08:00 降压药', ok: true },
  { t: '20:00 钙片', ok: false },
];

const MedVisual: React.FC = () => (
  <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
    {MEDS.map((m) => (
      <div key={m.t} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div
          style={{
            width: 8,
            height: 8,
            borderRadius: 4,
            background: m.ok ? C.lime : C.amber,
            boxShadow: `0 0 10px ${m.ok ? C.lime : C.amber}`,
          }}
        />
        <span style={{ fontFamily: F.mono, fontSize: 17, color: 'rgba(255,255,255,0.72)' }}>
          {m.t}
        </span>
        <span
          style={{
            fontFamily: F.cn,
            fontSize: 13,
            color: m.ok ? C.lime : C.amber,
            letterSpacing: 1,
          }}
        >
          {m.ok ? '已服' : '待服'}
        </span>
      </div>
    ))}
  </div>
);

/* ============ 场景 ============ */

export const M2Chat: React.FC = () => {
  const frame = useCurrentFrame();

  const clamp = {
    extrapolateLeft: 'clamp' as const,
    extrapolateRight: 'clamp' as const,
    easing: EASE.out,
  };
  const stub = interpolate(frame, [200, 236], [0, 1], clamp);
  const spine = interpolate(frame, [214, 250], [0, 1], clamp);
  const into = interpolate(frame, [238, 276], [0, 1], clamp);
  const dotT = (((frame - 250) % 40) + 40) % 40 / 40;
  const dotOn = frame >= 250;

  return (
    <AbsoluteFill>
      <Backdrop accent={C.cyan} second={C.violet} intensity={0.9} />

      <SceneIndex n="02" from={4} accent={C.cyan} />
      <SectionTitle
        kicker="M2 / DIALOG"
        title="「小护」对话"
        sub="回答不是泛泛而谈，而是带着这位老人的数据"
        from={0}
        size={64}
      />

      {/* 虚线汇聚：三卡 → 主干 → 手机 */}
      <svg width={1920} height={1080} style={{ position: 'absolute', inset: 0 }}>
        {CENTERS.map((cy, i) => (
          <line
            key={i}
            x1={CARD_X}
            y1={cy}
            x2={CARD_X - 60 * stub}
            y2={cy}
            stroke={C.cyan}
            strokeWidth={1.6}
            strokeDasharray="7 7"
            opacity={0.55 * stub}
          />
        ))}
        <line
          x1={SPINE_X}
          y1={CENTERS[0]}
          x2={SPINE_X}
          y2={CENTERS[0] + (CENTERS[2] - CENTERS[0]) * spine}
          stroke={C.cyan}
          strokeWidth={1.6}
          strokeDasharray="7 7"
          opacity={0.5}
        />
        <line
          x1={PHONE_RIGHT}
          y1={LINK_Y}
          x2={PHONE_RIGHT + (SPINE_X - PHONE_RIGHT) * into}
          y2={LINK_Y}
          stroke={C.cyan}
          strokeWidth={1.8}
          strokeDasharray="7 7"
          opacity={0.7}
        />
        <polygon
          points={`${PHONE_RIGHT + 2},${LINK_Y} ${PHONE_RIGHT + 16},${LINK_Y - 7} ${PHONE_RIGHT + 16},${LINK_Y + 7}`}
          fill={C.cyan}
          opacity={into}
        />
        {dotOn ? (
          <circle
            cx={SPINE_X - (SPINE_X - PHONE_RIGHT) * dotT}
            cy={LINK_Y}
            r={4.5}
            fill={C.cyan}
            opacity={0.9}
          />
        ) : null}
      </svg>

      <div
        style={{
          position: 'absolute',
          left: 520,
          top: LINK_Y - 46,
          fontFamily: F.mono,
          fontSize: 17,
          letterSpacing: 2,
          color: C.cyan,
          opacity: enter(frame, 248, 14),
        }}
      >
        AiService 组装上下文
      </div>

      {/* 手机 */}
      <div style={{ position: 'absolute', left: PHONE_X, top: PHONE_TOP }}>
        <PhoneFrame width={PHONE_W} from={18} accent={C.cyan}>
          <ChatScreen />
        </PhoneFrame>
      </div>

      {/* 三张上下文卡 */}
      <CtxCard
        n="01"
        top={CARD_TOPS[0]}
        title="打卡记录"
        sub="近 7 天打卡 6/7"
        from={90}
        accent={C.lime}
        visual={<CheckInVisual />}
      />
      <CtxCard
        n="02"
        top={CARD_TOPS[1]}
        title="睡眠与运动"
        sub="入睡时间、时长、步数"
        from={118}
        accent={C.cyan}
        visual={<SleepVisual />}
      />
      <CtxCard
        n="03"
        top={CARD_TOPS[2]}
        title="用药提醒"
        sub="今日服药计划"
        from={146}
        accent={C.violet}
        visual={<MedVisual />}
      />

      <div style={{ position: 'absolute', left: CARD_X, top: 840 }}>
        <ResultBar
          label="关键"
          value="同一个问题，回答里带的是他自己的数"
          from={300}
          width={960}
          accent={C.lime}
        />
      </div>

      <Caption text="上下文由后端注入，前端零密钥" from={40} to={382} accent={C.cyan} />
    </AbsoluteFill>
  );
};
