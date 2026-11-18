import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption } from '../../components/Chrome';
import {
  ChatBubble,
  EventStream,
  PhoneFrame,
  ResultBar,
  SceneIndex,
  SectionTitle,
} from '../../components/ai';
import { C, EASE, F, enter, leave } from '../../theme';

/**
 * M3｜三层 AI 降级（420 帧 / 14s）
 * 三张 provider 卡 → 断网 → 前两层熄灭 → 规则库点亮 → 手机里规则回复 → provider = rule
 */

const CARD_W = 520;
const CARD_H = 250;
const CARD_Y = 312;
const CARD_XS = [140, 700, 1260];

const PHONE_W = 340;
const PHONE_X = 160;
const PHONE_TOP = 300;

const clampEase = {
  extrapolateLeft: 'clamp' as const,
  extrapolateRight: 'clamp' as const,
  easing: EASE.out,
};

/* ============ 断网图标 ============ */

const NetOff: React.FC<{ from: number }> = ({ from }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 16);
  const blink = 0.55 + 0.45 * Math.abs(Math.sin(frame / 9));

  return (
    <div
      style={{
        position: 'absolute',
        left: 1380,
        top: 104,
        display: 'flex',
        alignItems: 'center',
        gap: 16,
        opacity: p,
        transform: `translateY(${(1 - p) * -14}px)`,
      }}
    >
      <svg width="58" height="58" viewBox="0 0 56 56" style={{ opacity: blink }}>
        <path
          d="M7 21a34 34 0 0 1 42 0"
          stroke={C.warn}
          strokeWidth="3.2"
          fill="none"
          strokeLinecap="round"
        />
        <path
          d="M15 30a24 24 0 0 1 26 0"
          stroke={C.warn}
          strokeWidth="3.2"
          fill="none"
          strokeLinecap="round"
        />
        <circle cx="28" cy="40" r="4" fill={C.warn} />
        <line x1="8" y1="8" x2="48" y2="48" stroke={C.warn} strokeWidth="3.4" strokeLinecap="round" />
      </svg>
      <div>
        <div style={{ fontFamily: F.cn, fontSize: 26, fontWeight: 800, color: C.warn, letterSpacing: 1 }}>
          网络断开
        </div>
        <div style={{ marginTop: 4, fontFamily: F.mono, fontSize: 14, letterSpacing: 3, color: C.dimmer }}>
          OFFLINE · 断网演示
        </div>
      </div>
    </div>
  );
};

/* ============ provider 卡 ============ */

const LayerCard: React.FC<{
  x: number;
  n: string;
  name: string;
  model: string;
  note: string;
  from: number;
  accent: string;
  dim: number;
  glow: number;
  stamp?: string;
  stampAt?: number;
}> = ({ x, n, name, model, note, from, accent, dim, glow, stamp, stampAt = 0 }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 16);
  const glowA = Math.round(glow * 0.55 * 255)
    .toString(16)
    .padStart(2, '0');

  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: CARD_Y,
        width: CARD_W,
        height: CARD_H,
        boxSizing: 'border-box',
        padding: '32px 36px',
        borderRadius: 24,
        overflow: 'hidden',
        border: `1px solid rgba(255,255,255,0.10)`,
        background: 'linear-gradient(155deg, rgba(255,255,255,0.075), rgba(255,255,255,0.02))',
        boxShadow: `0 26px 70px rgba(0,0,0,0.5), 0 0 ${Math.round(glow * 70)}px ${accent}${glowA}`,
        opacity: p,
        transform: `translateY(${(1 - p) * 34}px)`,
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span
          style={{
            fontFamily: F.mono,
            fontSize: 15,
            letterSpacing: 2,
            color: glow > 0.5 ? accent : C.dimmer,
          }}
        >
          {n}
        </span>
        <span style={{ fontFamily: F.mono, fontSize: 12, letterSpacing: 3, color: C.dimmer }}>
          PROVIDER
        </span>
      </div>
      <div
        style={{
          marginTop: 16,
          fontFamily: F.cn,
          fontSize: 40,
          fontWeight: 900,
          letterSpacing: 1,
          lineHeight: 1.1,
          color: C.ink,
        }}
      >
        {name}
      </div>
      <div
        style={{
          marginTop: 10,
          fontFamily: F.mono,
          fontSize: 20,
          letterSpacing: 1,
          color: accent,
        }}
      >
        {model}
      </div>
      <div
        style={{
          marginTop: 8,
          fontFamily: F.cn,
          fontSize: 17,
          maxWidth: 300,
          color: 'rgba(255,255,255,0.55)',
        }}
      >
        {note}
      </div>

      {/* 熄灭遮罩 */}
      <div style={{ position: 'absolute', inset: 0, background: C.bg, opacity: dim * 0.66 }} />

      {/* 状态戳 */}
      {stamp ? (
        <div
          style={{
            position: 'absolute',
            right: 26,
            bottom: 24,
            padding: '7px 14px',
            borderRadius: 8,
            border: `1px solid ${glow > 0.5 ? C.lime : C.warn}AA`,
            background: `${glow > 0.5 ? C.lime : C.warn}14`,
            fontFamily: F.mono,
            fontSize: 16,
            fontWeight: 700,
            letterSpacing: 2,
            color: glow > 0.5 ? C.lime : C.warn,
            opacity: enter(frame, stampAt, 10),
            transform: `scale(${0.9 + enter(frame, stampAt, 10) * 0.1})`,
          }}
        >
          {stamp}
        </div>
      ) : null}
    </div>
  );
};

/* ============ 手机内对话（规则回复） ============ */

const RuleChat: React.FC = () => {
  const frame = useCurrentFrame();

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', fontFamily: F.cn }}>
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
          <div style={{ marginTop: 2, fontFamily: F.mono, fontSize: 9, letterSpacing: 1, color: C.lime }}>
            ● 在线
          </div>
        </div>
      </div>

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
        <ChatBubble role="user" text="我有点心慌" from={204} accent={C.cyan} maxWidth={200} fontSize={15} />
        <ChatBubble
          role="ai"
          text="别着急，先坐下慢慢呼吸。如果持续不舒服，按一下紧急求助，我马上通知家人。"
          from={232}
          maxWidth={246}
          fontSize={15}
        />
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            opacity: enter(frame, 250, 14),
          }}
        >
          <div
            style={{
              padding: '4px 10px',
              borderRadius: 6,
              border: `1px solid ${C.lime}55`,
              background: `${C.lime}12`,
              fontFamily: F.mono,
              fontSize: 10,
              letterSpacing: 1,
              color: C.lime,
            }}
          >
            provider = rule
          </div>
          <span style={{ fontFamily: F.mono, fontSize: 10, letterSpacing: 1, color: C.dimmer }}>
            12 条关键词规则
          </span>
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

/* ============ 场景 ============ */

export const M3Fallback: React.FC = () => {
  const frame = useCurrentFrame();

  const dim1 = interpolate(frame, [120, 138], [0, 1], clampEase);
  const dim2 = interpolate(frame, [148, 166], [0, 1], clampEase);
  const glow3 = interpolate(frame, [168, 188], [0, 1], clampEase);

  const mv = interpolate(frame, [186, 210], [0, 1], clampEase);
  const titleOut = leave(frame, 188, 14);

  const events = [
    { tag: 'ollama', text: 'TIMEOUT 30s', color: C.warn },
    { tag: 'coze', text: 'NO TOKEN', color: C.warn },
    { tag: 'rule', text: 'hit: 心慌 → 安抚 + 紧急求助', color: C.lime },
  ];

  return (
    <AbsoluteFill>
      <Backdrop accent={C.cyan} second={C.violet} intensity={0.85} />

      <SceneIndex n="03" from={4} accent={C.cyan} />

      {/* 章节标题（阶段 A） */}
      <div style={{ position: 'absolute', inset: 0, opacity: titleOut }}>
        <SectionTitle
          kicker="M3 / FALLBACK"
          title="三层 AI 降级"
          sub="老人不能理解“服务异常”"
          from={0}
          size={72}
        />
      </div>

      {/* 断网图标 */}
      <NetOff from={104} />

      {/* 三张 provider 卡 */}
      <div style={{ position: 'absolute', inset: 0, transformOrigin: '140px 312px', transform: `translateY(${-212 * mv}px) scale(${1 - 0.28 * mv})` }}>
        <LayerCard
          x={CARD_XS[0]}
          n="01"
          name="Ollama"
          model="本地 qwen2.5:7b"
          note="免费 · 数据不出本地"
          from={40}
          accent={C.cyan}
          dim={dim1}
          glow={0}
          stamp="TIMEOUT"
          stampAt={124}
        />
        <LayerCard
          x={CARD_XS[1]}
          n="02"
          name="Coze"
          model="云端智能体"
          note="能力强 · 需 Token"
          from={52}
          accent={C.violet}
          dim={dim2}
          glow={0}
          stamp="NO TOKEN"
          stampAt={154}
        />
        <LayerCard
          x={CARD_XS[2]}
          n="03"
          name="规则库"
          model="12 条关键词规则"
          note="永远可用 · 零延迟"
          from={64}
          accent={C.lime}
          dim={0}
          glow={glow3}
          stamp="ALWAYS ON"
          stampAt={176}
        />
      </div>

      <div style={{ position: 'absolute', left: PHONE_X, top: PHONE_TOP }}>
        <PhoneFrame width={PHONE_W} from={192} accent={C.lime}>
          <RuleChat />
        </PhoneFrame>
      </div>

      <div style={{ position: 'absolute', left: 800, top: 320, width: 960 }}>
        <div
          style={{
            fontFamily: F.mono,
            fontSize: 17,
            letterSpacing: 3,
            color: C.dimmer,
            opacity: enter(frame, 198, 14),
          }}
        >
          FALLBACK CHAIN
        </div>
        <div style={{ marginTop: 18 }}>
          <EventStream events={events} from={204} interval={16} width={960} fontSize={21} />
        </div>
        <div
          style={{
            marginTop: 20,
            fontFamily: F.cn,
            fontSize: 21,
            color: 'rgba(255,255,255,0.45)',
            opacity: enter(frame, 262, 14),
          }}
        >
          三层顺序固定，不向用户暴露错误
        </div>
      </div>

      <div style={{ position: 'absolute', left: 800, top: 660 }}>
        <ResultBar
          label="provider = rule"
          value="用户永远看不到报错"
          from={280}
          width={960}
          accent={C.lime}
        />
      </div>

      <Caption text="断网、零额度、模型超时，都还有回复" from={196} to={412} accent={C.lime} />
    </AbsoluteFill>
  );
};
