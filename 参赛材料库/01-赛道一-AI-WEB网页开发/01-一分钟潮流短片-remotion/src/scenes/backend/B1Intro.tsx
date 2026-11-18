import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption, LightSweep } from '../../components/Chrome';
import { FlowLine, GlassCard, Headline, Rule } from '../../components/ui';
import { C, F, enter, leave } from '../../theme';

/**
 * B1｜开场 · 后台不只是看板，是 AI 值守台｜180 帧（6s）
 *
 * 场景内关键帧（30fps，帧号为本场景相对帧）：
 *   0–30    黑场中一条青色横线自左拉出
 *   6–26    右上计数 00 → 06 快速滚动，定格 06
 *   20      主标第一行「后台不只是看板」上浮（96px 白）
 *   44      第二行「是 AI 值守台」上浮（96px 青）
 *   58      标题下细分割线 + 数据流脉冲
 *   60–132  六个能力标签 2 行 3 列依次弹入（间隔 12 帧）
 *   100     底部字幕入场
 *   150–178 整体轻微上移淡出，接 B2
 */

const CAPS = [
  '告警分诊',
  '数据问答',
  '知识检索',
  '每日简报',
  '风险画像',
  '处置话术',
];

/** 单个能力标签：弹入（位移 + 缩放 + 淡入） */
const CapTile: React.FC<{ label: string; index: number; from: number }> = ({
  label,
  index,
  from,
}) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 18);

  return (
    <div
      style={{
        opacity: p,
        transform: `translateY(${interpolate(p, [0, 1], [30, 0])}px) scale(${interpolate(
          p,
          [0, 1],
          [0.93, 1],
        )})`,
      }}
    >
      <GlassCard padding={0} radius={18} accent={C.cyan} style={{ height: 118 }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 24,
            height: '100%',
            padding: '0 30px',
          }}
        >
          <span
            style={{
              fontFamily: F.mono,
              fontSize: 20,
              fontWeight: 700,
              letterSpacing: 2,
              color: C.cyan,
              opacity: 0.9,
            }}
          >
            {String(index + 1).padStart(2, '0')}
          </span>
          <span
            style={{
              fontFamily: F.cn,
              fontSize: 34,
              fontWeight: 700,
              letterSpacing: 2,
              color: C.ink,
              whiteSpace: 'nowrap',
            }}
          >
            {label}
          </span>
        </div>
      </GlassCard>
    </div>
  );
};

export const B1Intro: React.FC = () => {
  const frame = useCurrentFrame();

  // 0–30 横线拉出
  const lineP = enter(frame, 0, 30);
  // 计数淡入 + 00→06 滚动
  const cntP = enter(frame, 4, 12);
  const cnt = Math.round(
    interpolate(frame, [6, 26], [0, 6], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    }),
  );
  // 150–178 整体上移淡出
  const out = leave(frame, 150, 28);

  return (
    <AbsoluteFill>
      <Backdrop accent={C.cyan} second={C.violet} intensity={1} />

      <div
        style={{
          position: 'absolute',
          inset: 0,
          opacity: out,
          transform: `translateY(${(1 - out) * -46}px)`,
        }}
      >
        {/* ===== 顶部青色横线（0–30f 自左拉出） ===== */}
        <div
          style={{
            position: 'absolute',
            left: 140,
            top: 150,
            width: 1640 * lineP,
            height: 2,
            background: C.cyan,
            boxShadow: `0 0 26px ${C.cyan}`,
          }}
        />
        {/* 线右端的小方点，作为计数锚点 */}
        <div
          style={{
            position: 'absolute',
            left: 140 + 1640 * lineP - 3,
            top: 145,
            width: 12,
            height: 12,
            borderRadius: 2,
            background: C.cyan,
            boxShadow: `0 0 24px ${C.cyan}`,
            opacity: lineP,
          }}
        />

        {/* ===== 右上计数 06 ===== */}
        <div
          style={{
            position: 'absolute',
            right: 140,
            top: 52,
            opacity: cntP,
            fontFamily: F.mono,
            fontSize: 88,
            fontWeight: 900,
            lineHeight: 1,
            letterSpacing: -2,
            color: C.cyan,
            fontVariantNumeric: 'tabular-nums',
            textShadow: `0 0 40px ${C.cyan}66`,
          }}
        >
          {String(cnt).padStart(2, '0')}
        </div>

        {/* ===== 主标两行 ===== */}
        <div style={{ position: 'absolute', left: 140, top: 248 }}>
          <Headline lines={['后台不只是看板']} from={20} size={96} weight={900} color={C.ink} />
          <div style={{ height: 16 }} />
          <Headline lines={['是 AI 值守台']} from={44} size={96} weight={900} color={C.cyan} />
        </div>

        {/* ===== 分割线 + 数据流脉冲 ===== */}
        <div
          style={{
            position: 'absolute',
            left: 140,
            top: 512,
            display: 'flex',
            alignItems: 'center',
            gap: 48,
          }}
        >
          <Rule from={58} width={520} color={C.cyan} />
          <div style={{ opacity: enter(frame, 66, 20) }}>
            <FlowLine width={340} color={C.violet} speed={2.6} dots={3} />
          </div>
        </div>

        {/* ===== 六个能力标签：2 行 3 列 ===== */}
        <div
          style={{
            position: 'absolute',
            left: 140,
            top: 566,
            display: 'grid',
            gridTemplateColumns: 'repeat(3, 520px)',
            columnGap: 40,
            rowGap: 28,
          }}
        >
          {CAPS.map((label, i) => (
            <CapTile key={label} label={label} index={i} from={60 + i * 12} />
          ))}
        </div>

        {/* ===== 底部字幕 ===== */}
        <Caption
          text="6 个 AI 能力，覆盖研判 → 问答 → 检索 → 汇总 → 预警 → 沟通"
          from={100}
          to={172}
          accent={C.cyan}
        />
      </div>

      {/* 标题落定时的斜向扫光 */}
      <LightSweep from={26} dur={24} color={`${C.cyan}47`} />
    </AbsoluteFill>
  );
};
