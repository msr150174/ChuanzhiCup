import React from 'react';
import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import { Backdrop, Caption, Flash } from '../components/Chrome';
import { Headline, Rule } from '../components/ui';
import { C, F, enter } from '../theme';

/**
 * S2｜Problem 链条断了｜270 帧（9s）
 *
 * 关键时间点（场景内相对帧）：
 *   0       承接 S1 的 1 帧闪白
 *   6–24    左上主标题「告警，不是终点」入场
 *   18      节点①「告警产生」spring 弹出（damping 14）
 *   42      节点②「消息推送」弹出
 *   66      节点③「有人跟进」弹出，先亮青色
 *   78      节点③转警示红 + 断裂：连线 scaleX 1→0.4、红色碎片下坠
 *   80/104  断点红色脉冲光圈 2 次
 *   75      「然后呢？」浮现（10.5s）
 *   30–258  底部字幕
 */

const NODE_W = 300;
const NODE_H = 140;
const NODE_TOP = 590;
const NODE_CY = NODE_TOP + NODE_H / 2; // 660
const X1 = 140;
const X2 = 810;
const X3 = 1480;
const LINK_W = X2 - (X1 + NODE_W); // 370
const BREAK_X = X2 + NODE_W; // 1110，断点位置
const RING_X = 1340; // 脉冲光圈落在断口的空档里，避免压住节点

/** 链路节点：spring 弹出，支持 待机/点亮/熄灭 三态 */
const ChainNode: React.FC<{
  label: string;
  index: string;
  x: number;
  at: number;
  accent: string;
  dead?: boolean;
}> = ({ label, index, x, at, accent, dead = false }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const s = spring({
    frame: Math.max(0, frame - at),
    fps,
    config: { damping: 14, mass: 0.6, stiffness: 130 },
  });
  const scale = interpolate(s, [0, 1], [0.82, 1]);
  const op = Math.min(1, s * 1.5);

  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: NODE_TOP,
        width: NODE_W,
        height: NODE_H,
        opacity: op,
        transform: `scale(${scale})`,
        transformOrigin: 'center',
      }}
    >
      {/* 节点序号 */}
      <div
        style={{
          position: 'absolute',
          left: 2,
          top: -32,
          fontFamily: F.mono,
          fontSize: 18,
          fontWeight: 600,
          letterSpacing: 3,
          color: accent,
          opacity: 0.55,
        }}
      >
        {index}
      </div>

      {/* 卡片本体 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 20,
          border: `1px solid ${accent}${dead ? '55' : 'AA'}`,
          background: `linear-gradient(160deg, ${accent}${dead ? '08' : '1A'}, rgba(255,255,255,0.02))`,
          boxShadow: dead
            ? 'inset 0 1px 0 rgba(255,255,255,0.06)'
            : `0 0 60px ${accent}44, inset 0 1px 0 rgba(255,255,255,0.12)`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div
          style={{
            fontFamily: F.cn,
            fontSize: 44,
            fontWeight: 700,
            letterSpacing: 1,
            color: dead ? accent : C.ink,
            textShadow: dead ? 'none' : `0 0 30px ${accent}88`,
          }}
        >
          {label}
        </div>
        {/* 顶部细线 */}
        <div
          style={{
            position: 'absolute',
            top: 0,
            left: 24,
            right: 24,
            height: 2,
            background: `linear-gradient(90deg, ${accent}, transparent)`,
            opacity: dead ? 0.35 : 0.9,
          }}
        />
      </div>
    </div>
  );
};

/** 连线：从左展开；断裂时 scaleX 1→0.4 并左移淡出 */
const Link: React.FC<{
  x: number;
  at: number;
  accent: string;
  broken?: boolean;
}> = ({ x, at, accent, broken = false }) => {
  const frame = useCurrentFrame();
  const grow = enter(frame, at, 16);
  const cut = broken
    ? interpolate(frame, [78, 94], [1, 0.4], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      })
    : 1;
  const fade = broken
    ? interpolate(frame, [78, 94], [1, 0.32], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      })
    : 1;
  const shift = broken
    ? interpolate(frame, [78, 94], [0, -14], {
        extrapolateLeft: 'clamp',
        extrapolateRight: 'clamp',
      })
    : 0;

  return (
    <div
      style={{
        position: 'absolute',
        left: x + shift,
        top: NODE_CY - 1,
        width: LINK_W,
        height: 2,
        transform: `scaleX(${grow * cut})`,
        transformOrigin: 'left center',
        opacity: grow * fade,
        background: `linear-gradient(90deg, ${accent}33, ${accent}, ${accent}33)`,
        boxShadow: `0 0 20px ${accent}`,
      }}
    />
  );
};

/** 断裂瞬间下坠的红色碎片 */
const Frag: React.FC<{ x: number; delay: number; seed: number }> = ({
  x,
  delay,
  seed,
}) => {
  const frame = useCurrentFrame();
  const d = frame - delay;
  if (d < 0) return null;
  const t = Math.min(1, d / 34);
  const op = interpolate(d, [0, 30], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  return (
    <div
      style={{
        position: 'absolute',
        left: x,
        top: NODE_CY + t * t * 260,
        width: 6,
        height: 26,
        borderRadius: 2,
        background: C.warn,
        boxShadow: `0 0 18px ${C.warn}`,
        opacity: op,
        transform: `rotate(${seed * 32 + t * 70 * seed}deg)`,
      }}
    />
  );
};

/** 断点红色脉冲光圈 */
const PulseRing: React.FC<{ at: number }> = ({ at }) => {
  const frame = useCurrentFrame();
  const p = interpolate(frame, [at, at + 26], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  if (p <= 0 || p >= 1) return null;
  const size = 40 + p * 300;
  return (
    <div
      style={{
        position: 'absolute',
        left: RING_X - size / 2,
        top: NODE_CY - size / 2,
        width: size,
        height: size,
        borderRadius: '50%',
        border: `2px solid ${C.warn}`,
        opacity: (1 - p) * 0.9,
        boxShadow: `0 0 44px ${C.warn}`,
      }}
    />
  );
};

export const S2Problem: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const broken = frame >= 78;

  // 「然后呢？」10.5s = 场景内 75 帧
  const q = spring({
    frame: Math.max(0, frame - 75),
    fps,
    config: { damping: 16, mass: 0.7, stiffness: 110 },
  });
  const qScale = interpolate(q, [0, 1], [0.78, 1]);
  const qOp = interpolate(q, [0, 0.55], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  // 断点后的持续微颤
  const tremble = broken ? Math.sin((frame / 5) * Math.PI * 2) * 2 : 0;

  return (
    <AbsoluteFill>
      {/* 冷灰背景：光斑减弱 */}
      <Backdrop
        accent={C.dim}
        second={C.violet}
        intensity={0.5}
        gridOpacity={0.07}
      />

      {/* 左上主标题 */}
      <div style={{ position: 'absolute', left: 140, top: 118 }}>
        <Rule from={6} width={120} color={C.warn} />
      </div>
      <div style={{ position: 'absolute', left: 140, top: 146 }}>
        <Headline lines={['告警，不是终点']} from={10} size={76} />
      </div>

      {/* 右上角大字：然后呢？ */}
      <div
        style={{
          position: 'absolute',
          right: 140,
          top: 118,
          opacity: qOp,
          transform: `scale(${qScale})`,
          transformOrigin: 'right center',
          textAlign: 'right',
        }}
      >
        <div
          style={{
            fontFamily: F.cn,
            fontSize: 168,
            fontWeight: 900,
            letterSpacing: -6,
            lineHeight: 1,
            color: C.ink,
            whiteSpace: 'nowrap',
            textShadow: `0 0 55px ${C.warn}66`,
          }}
        >
          然后呢？
        </div>
        <div
          style={{
            marginTop: 18,
            marginLeft: 'auto',
            width: 300 * q,
            height: 5,
            background: `linear-gradient(90deg, transparent, ${C.warn})`,
            boxShadow: `0 0 24px ${C.warn}`,
          }}
        />
      </div>

      {/* 链路：告警产生 → 消息推送 → 有人跟进 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          transform: `translateX(${tremble}px)`,
        }}
      >
        <Link x={X1 + NODE_W} at={30} accent={C.cyan} />
        <Link
          x={X2 + NODE_W}
          at={58}
          accent={broken ? C.warn : C.cyan}
          broken
        />

        <ChainNode label="告警产生" index="01" x={X1} at={18} accent={C.cyan} />
        <ChainNode label="消息推送" index="02" x={X2} at={42} accent={C.cyan} />
        <ChainNode
          label="有人跟进"
          index="03"
          x={X3}
          at={66}
          accent={broken ? C.warn : C.cyan}
        />

        {/* 断裂碎片与脉冲 */}
        <Frag x={BREAK_X + 180} delay={80} seed={1} />
        <Frag x={BREAK_X + 212} delay={83} seed={-1} />
        <Frag x={BREAK_X + 242} delay={86} seed={2} />
        <PulseRing at={80} />
        <PulseRing at={104} />
      </div>

      {/* 底部字幕 */}
      <Caption
        text="AI 能发现异常，但异常之后，需要有人接住。"
        from={30}
        to={258}
        accent={C.warn}
      />

      {/* 承接 S1 的闪白尾帧 */}
      <Flash at={0} frames={1} />
    </AbsoluteFill>
  );
};
