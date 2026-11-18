import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Flash } from '../components/Chrome';
import { C, F, enter, leave } from '../theme';

/**
 * S1｜Hook 跌倒｜240 帧（8s）
 *
 * 关键时间点（场景内相对帧）：
 *   0–40    黑场起，底部红橙光斑涌起
 *   12      「跌倒了。」砸入（0.4s）
 *   78      「手机，不在手边。」砸入（2.6s）
 *   144     「家里，没有人。」砸入，转警示红（4.8s）
 *   144–210 整屏轻抖，正弦 6 帧周期、±3px
 *   204     三行同时向下压缩淡出（6.8s）
 *   210–226 三行压成一条红色细线，作为硬切前的画面锚点
 *   237–239 闪白，接 S2
 */

/** 字块砸入瞬间的 4 帧微白闪 */
const MicroFlash: React.FC<{ at: number }> = ({ at }) => {
  const frame = useCurrentFrame();
  const d = frame - at;
  if (d < 0 || d >= 4) return null;
  return (
    <AbsoluteFill
      style={{
        background: '#fff',
        opacity: 0.16 * (1 - d / 4),
        pointerEvents: 'none',
      }}
    />
  );
};

/** 单行大字：translateY 40→0 + clipPath 遮罩 + scaleX 1.06→1，18 帧入场 */
const BigLine: React.FC<{
  text: string;
  delay: number;
  size: number;
  color: string;
  offsetX?: number;
}> = ({ text, delay, size, color, offsetX = 0 }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, delay, 18); // 18 帧入场
  const out = leave(frame, 204, 20); // 6.8s 起压缩淡出

  return (
    <div
      style={{
        fontFamily: F.cn,
        fontSize: size,
        fontWeight: 900,
        lineHeight: 1.08,
        letterSpacing: -2,
        color,
        whiteSpace: 'nowrap',
        opacity: p * out,
        transformOrigin: 'left center',
        transform: `translate(${offsetX}px, ${(1 - p) * 40 + (1 - out) * 30}px) scaleX(${interpolate(
          p,
          [0, 1],
          [1.06, 1],
        )}) scaleY(${interpolate(out, [0, 1], [0.72, 1])})`,
        // 遮罩只切文字本身，发光由外层单独铺，避免 clip 切出发光硬边
        clipPath: `inset(${(1 - p) * 100}% 0 0 0)`,
      }}
    >
      {text}
    </div>
  );
};

export const S1Hook: React.FC = () => {
  const frame = useCurrentFrame();

  // 0–40 帧光斑涌起；第三句（144）出现后再压亮一档
  const glow =
    interpolate(frame, [0, 40], [0.3, 1], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    }) +
    interpolate(frame, [144, 170], [0, 0.3], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    });

  // 第三句出现后整屏轻抖：正弦 6 帧周期、幅度 ±3px
  const shaking = frame >= 144 && frame < 210;
  const shake = shaking ? Math.sin((frame / 6) * Math.PI * 2) * 3 : 0;

  // 210–226 帧：三行压成一条红线，作为末帧锚点
  const bar = enter(frame, 210, 16);
  const barH = interpolate(bar, [0, 1], [0, 6]);

  // 左侧竖线，首帧即已半显，保证第一帧就有结构
  const vline = enter(frame, -10, 22);

  return (
    <AbsoluteFill>
      <Backdrop accent={C.warn} second={C.amber} intensity={glow} />

      {/* 底部涌起的红橙光斑 */}
      <div
        style={{
          position: 'absolute',
          left: '50%',
          bottom: -400,
          width: 1900,
          height: 780,
          marginLeft: -950,
          background: `radial-gradient(ellipse at 50% 100%, ${C.warn}55 0%, ${C.amber}22 42%, transparent 74%)`,
          filter: 'blur(30px)',
          opacity: glow * 0.9,
        }}
      />

      {/* 左侧结构竖线（杂志感） */}
      <div
        style={{
          position: 'absolute',
          left: 100,
          top: 250,
          width: 2,
          height: 560 * vline,
          background: `linear-gradient(180deg, transparent, ${C.warn}, transparent)`,
          opacity: vline * 0.8,
        }}
      />

      {/* 三行大字：错位排版 */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          paddingLeft: 140,
          paddingRight: 140,
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          gap: 4,
          transform: `translateX(${shake}px)`,
        }}
      >
        <BigLine text="跌倒了。" delay={12} size={168} color={C.ink} offsetX={0} />
        <BigLine
          text="手机，不在手边。"
          delay={78}
          size={140}
          color={C.ink}
          offsetX={76}
        />
        {/* 第三行：红色光晕单独铺一层，避开 clipPath 的硬边 */}
        <div style={{ position: 'relative' }}>
          <div
            style={{
              position: 'absolute',
              left: -100,
              right: -100,
              top: -50,
              bottom: -70,
              background: `radial-gradient(ellipse at 38% 50%, ${C.warn}44 0%, ${C.warn}11 45%, transparent 72%)`,
              filter: 'blur(34px)',
              opacity: enter(frame, 144, 26),
            }}
          />
          <BigLine text="家里，没有人。" delay={144} size={176} color={C.warn} offsetX={0} />
        </div>
      </div>

      {/* 压缩后的红色横线：末帧不空，便于硬切 */}
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: '50%',
          width: 1920,
          height: 180,
          marginTop: -90,
          background: `linear-gradient(90deg, transparent, ${C.warn}33 35%, ${C.warn}33 65%, transparent)`,
          filter: 'blur(38px)',
          opacity: bar * 0.85,
        }}
      />
      <div
        style={{
          position: 'absolute',
          left: 0,
          top: '50%',
          width: 1920,
          height: barH,
          transform: `translateY(-50%) scaleX(${bar})`,
          transformOrigin: 'center',
          background: `linear-gradient(90deg, transparent, ${C.warn} 30%, ${C.warn} 70%, transparent)`,
          boxShadow: `0 0 60px ${C.warn}`,
          opacity: bar,
        }}
      />

      {/* 每行砸入的 4 帧白闪 */}
      <MicroFlash at={12} />
      <MicroFlash at={78} />
      <MicroFlash at={144} />

      {/* 接 S2 的闪白 */}
      <Flash at={237} frames={3} />
    </AbsoluteFill>
  );
};
