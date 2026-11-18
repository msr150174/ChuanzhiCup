import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { C, F, TOTAL_FRAMES, enter, leave } from '../theme';

/**
 * 噪点层：给纯色背景加胶片颗粒，避免数码感过重
 */
export const Grain: React.FC<{ opacity?: number }> = ({ opacity = 0.07 }) => (
  <svg
    style={{
      position: 'absolute',
      inset: 0,
      width: '100%',
      height: '100%',
      opacity,
      mixBlendMode: 'overlay',
      pointerEvents: 'none',
    }}
  >
    <filter id="grain">
      <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="3" />
    </filter>
    <rect width="100%" height="100%" filter="url(#grain)" />
  </svg>
);

/**
 * 全局背景：深空底色 + 透视网格 + 双光斑 + 暗角 + 噪点
 * accent 控制主光颜色，intensity 控制光斑强度
 */
export const Backdrop: React.FC<{
  accent?: string;
  second?: string;
  intensity?: number;
  gridOpacity?: number;
  children?: React.ReactNode;
}> = ({
  accent = C.cyan,
  second = C.violet,
  intensity = 1,
  gridOpacity = 0.05,
  children,
}) => {
  const frame = useCurrentFrame();
  // 网格缓慢漂移，制造"活着"的感觉
  const shift = (frame * 0.35) % 96;

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg, overflow: 'hidden' }}>
      {/* 主光斑 */}
      <div
        style={{
          position: 'absolute',
          width: 1500,
          height: 1500,
          left: -500 + Math.sin(frame / 90) * 60,
          top: -700,
          background: `radial-gradient(circle, ${accent}33 0%, transparent 62%)`,
          filter: 'blur(40px)',
          opacity: intensity,
        }}
      />
      {/* 副光斑 */}
      <div
        style={{
          position: 'absolute',
          width: 1300,
          height: 1300,
          right: -520,
          bottom: -680 + Math.cos(frame / 110) * 50,
          background: `radial-gradient(circle, ${second}33 0%, transparent 62%)`,
          filter: 'blur(50px)',
          opacity: intensity * 0.9,
        }}
      />
      {/* 网格 */}
      <div
        style={{
          position: 'absolute',
          inset: -200,
          backgroundImage: `linear-gradient(${C.line} 1px, transparent 1px), linear-gradient(90deg, ${C.line} 1px, transparent 1px)`,
          backgroundSize: '96px 96px',
          backgroundPosition: `${shift}px ${shift * 0.6}px`,
          opacity: gridOpacity,
          maskImage:
            'radial-gradient(ellipse 80% 70% at 50% 50%, #000 30%, transparent 100%)',
          WebkitMaskImage:
            'radial-gradient(ellipse 80% 70% at 50% 50%, #000 30%, transparent 100%)',
        }}
      />
      {/* 暗角 */}
      <AbsoluteFill
        style={{
          background:
            'radial-gradient(ellipse 75% 65% at 50% 50%, transparent 40%, rgba(0,0,0,0.72) 100%)',
        }}
      />
      <Grain />
      {children}
    </AbsoluteFill>
  );
};

/**
 * 底部字幕条：潮流片用短句 + 细底线，不做传统新闻字幕框
 */
export const Caption: React.FC<{
  text: string;
  from: number;
  to: number;
  accent?: string;
}> = ({ text, from, to, accent = C.cyan }) => {
  const frame = useCurrentFrame();
  const a = enter(frame, from, 14) * leave(frame, to, 12);
  const y = interpolate(a, [0, 1], [18, 0]);

  return (
    <AbsoluteFill
      style={{
        justifyContent: 'flex-end',
        alignItems: 'center',
        paddingBottom: 74,
      }}
    >
      <div
        style={{
          opacity: a,
          transform: `translateY(${y}px)`,
          display: 'flex',
          alignItems: 'center',
          gap: 18,
        }}
      >
        <div
          style={{
            width: 34,
            height: 3,
            background: accent,
            boxShadow: `0 0 18px ${accent}`,
          }}
        />
        <div
          style={{
            fontFamily: F.cn,
            fontSize: 34,
            fontWeight: 500,
            letterSpacing: 2,
            color: 'rgba(255,255,255,0.92)',
            textShadow: '0 2px 18px rgba(0,0,0,0.85)',
          }}
        >
          {text}
        </div>
        <div
          style={{
            width: 34,
            height: 3,
            background: accent,
            boxShadow: `0 0 18px ${accent}`,
          }}
        />
      </div>
    </AbsoluteFill>
  );
};

/** 顶部章节进度条（潮流短视频常用） */
export const ProgressBar: React.FC<{ accent?: string }> = ({
  accent = C.cyan,
}) => {
  const frame = useCurrentFrame();
  const p = Math.min(1, frame / TOTAL_FRAMES);
  // 结尾 14 帧淡出，避免末帧残留一条亮线
  const fadeOut = interpolate(
    frame,
    [TOTAL_FRAMES - 14, TOTAL_FRAMES - 2],
    [1, 0],
    { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }
  );
  return (
    <div
      style={{
        position: 'absolute',
        left: 0,
        right: 0,
        bottom: 0,
        height: 3,
        opacity: 0.62 * fadeOut,
        background: 'rgba(255,255,255,0.08)',
      }}
    >
      <div
        style={{
          width: `${p * 100}%`,
          height: '100%',
          background: `linear-gradient(90deg, ${C.violet}, ${accent})`,
          boxShadow: `0 0 20px ${accent}`,
        }}
      />
    </div>
  );
};

/** 斜向扫光：用于转场/标题高光 */
export const LightSweep: React.FC<{
  from: number;
  dur?: number;
  color?: string;
}> = ({ from, dur = 20, color = 'rgba(255,255,255,0.5)' }) => {
  const frame = useCurrentFrame();
  const p = interpolate(frame, [from, from + dur], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  if (p <= 0 || p >= 1) return null;
  return (
    <div
      style={{
        position: 'absolute',
        top: '-30%',
        bottom: '-30%',
        width: 260,
        left: `${-30 + p * 130}%`,
        transform: 'rotate(14deg)',
        background: `linear-gradient(90deg, transparent, ${color}, transparent)`,
        filter: 'blur(24px)',
        mixBlendMode: 'screen',
      }}
    />
  );
};

/** 全屏闪白（硬切转场用，1–3 帧） */
export const Flash: React.FC<{ at: number; frames?: number }> = ({
  at,
  frames = 2,
}) => {
  const frame = useCurrentFrame();
  const on = frame >= at && frame < at + frames;
  if (!on) return null;
  return <AbsoluteFill style={{ background: '#fff', opacity: 0.85 }} />;
};
