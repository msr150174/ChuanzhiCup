import React from 'react';
import { interpolate, useCurrentFrame, useVideoConfig } from 'remotion';
import { C, EASE, F, enter } from '../theme';

/** 小标签：色点 + 全大写英文/短词，潮流片常用作 section marker */
export const Kicker: React.FC<{
  text: string;
  from?: number;
  color?: string;
  style?: React.CSSProperties;
}> = ({ text, from = 0, color = C.cyan, style }) => {
  const frame = useCurrentFrame();
  const a = enter(frame, from, 12);
  return (
    <div
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        gap: 12,
        opacity: a,
        transform: `translateY(${(1 - a) * 14}px)`,
        ...style,
      }}
    >
      <div
        style={{
          width: 8,
          height: 8,
          borderRadius: 99,
          background: color,
          boxShadow: `0 0 14px ${color}`,
        }}
      />
      <div
        style={{
          fontFamily: F.mono,
          fontSize: 22,
          fontWeight: 600,
          letterSpacing: 5,
          color: 'rgba(255,255,255,0.72)',
          textTransform: 'uppercase',
        }}
      >
        {text}
      </div>
    </div>
  );
};

/** 大字标题：逐行上浮入场，支持逐行延迟 */
export const Headline: React.FC<{
  lines: string[];
  from?: number;
  stagger?: number;
  size?: number;
  weight?: number;
  color?: string;
  align?: 'left' | 'center' | 'right';
  lineHeight?: number;
  style?: React.CSSProperties;
}> = ({
  lines,
  from = 0,
  stagger = 8,
  size = 104,
  weight = 900,
  color = C.ink,
  align = 'left',
  lineHeight = 1.16,
  style,
}) => {
  const frame = useCurrentFrame();
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems:
          align === 'center' ? 'center' : align === 'right' ? 'flex-end' : 'flex-start',
        textAlign: align,
        ...style,
      }}
    >
      {lines.map((line, i) => {
        const a = enter(frame, from + i * stagger, 18);
        return (
          <div
            key={i}
            style={{
              fontFamily: F.cn,
              fontSize: size,
              fontWeight: weight,
              lineHeight,
              letterSpacing: -1,
              color,
              opacity: a,
              transform: `translateY(${(1 - a) * 46}px)`,
              clipPath: `inset(${(1 - a) * 100}% 0 0 0)`,
              whiteSpace: 'nowrap',
            }}
          >
            {line}
          </div>
        );
      })}
    </div>
  );
};

/** 玻璃拟态卡片 */
export const GlassCard: React.FC<{
  children?: React.ReactNode;
  accent?: string;
  style?: React.CSSProperties;
  padding?: number;
  radius?: number;
}> = ({ children, accent = C.cyan, style, padding = 40, radius = 24 }) => (
  <div
    style={{
      position: 'relative',
      padding,
      borderRadius: radius,
      background:
        'linear-gradient(155deg, rgba(255,255,255,0.075), rgba(255,255,255,0.02))',
      border: `1px solid ${C.line}`,
      boxShadow: `inset 0 1px 0 rgba(255,255,255,0.10), 0 24px 60px rgba(0,0,0,0.45)`,
      backdropFilter: 'blur(14px)',
      overflow: 'hidden',
      ...style,
    }}
  >
    <div
      style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        height: 2,
        background: `linear-gradient(90deg, ${accent}, transparent 70%)`,
        opacity: 0.85,
      }}
    />
    {children}
  </div>
);

/** 圆角标签 */
export const Tag: React.FC<{
  text: string;
  color?: string;
  from?: number;
  mono?: boolean;
}> = ({ text, color = C.cyan, from = 0, mono = false }) => {
  const frame = useCurrentFrame();
  const a = enter(frame, from, 12);
  return (
    <div
      style={{
        opacity: a,
        transform: `translateY(${(1 - a) * 12}px)`,
        padding: '10px 20px',
        borderRadius: 999,
        border: `1px solid ${color}66`,
        background: `${color}14`,
        color,
        fontFamily: mono ? F.mono : F.cn,
        fontSize: mono ? 22 : 24,
        fontWeight: 600,
        letterSpacing: mono ? 1 : 0.5,
        whiteSpace: 'nowrap',
      }}
    >
      {text}
    </div>
  );
};

/** 大数字：从 0 滚到目标值 */
export const StatBig: React.FC<{
  value: number;
  suffix?: string;
  prefix?: string;
  label: string;
  from?: number;
  color?: string;
  size?: number;
  format?: (n: number) => string;
}> = ({
  value,
  suffix = '',
  prefix = '',
  label,
  from = 0,
  color = C.ink,
  size = 132,
  format,
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const p = interpolate(frame, [from, from + 34], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  const shown = Math.round(value * p);
  const a = enter(frame, from, 12);
  const text = format ? format(shown) : shown.toLocaleString('en-US');

  return (
    <div style={{ opacity: a }}>
      <div
        style={{
          fontFamily: F.mono,
          fontSize: size,
          fontWeight: 900,
          lineHeight: 0.95,
          color,
          letterSpacing: -4,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {prefix}
        {text}
        <span style={{ fontSize: size * 0.36, marginLeft: 6, letterSpacing: 0 }}>
          {suffix}
        </span>
      </div>
      <div
        style={{
          marginTop: 16,
          fontFamily: F.cn,
          fontSize: 26,
          fontWeight: 500,
          color: C.dim,
          letterSpacing: 1,
        }}
      >
        {label}
      </div>
    </div>
  );
};

/** 细分割线，从左展开 */
export const Rule: React.FC<{
  from?: number;
  width?: number;
  color?: string;
  style?: React.CSSProperties;
}> = ({ from = 0, width = 160, color = C.line, style }) => {
  const frame = useCurrentFrame();
  const a = enter(frame, from, 20);
  return (
    <div
      style={{
        height: 1,
        width: width * a,
        background: `linear-gradient(90deg, ${color}, transparent)`,
        ...style,
      }}
    />
  );
};

/** 数据流脉冲：一条线上跑的光点，用于表达"链路在流动" */
export const FlowLine: React.FC<{
  width: number;
  color?: string;
  speed?: number;
  thickness?: number;
  dots?: number;
}> = ({ width, color = C.cyan, speed = 2.2, thickness = 2, dots = 3 }) => {
  const frame = useCurrentFrame();
  return (
    <div style={{ position: 'relative', width, height: thickness }}>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          background: `linear-gradient(90deg, ${color}00, ${color}66, ${color}00)`,
        }}
      />
      {Array.from({ length: dots }).map((_, i) => {
        const p = ((frame * speed) / width + i / dots) % 1;
        return (
          <div
            key={i}
            style={{
              position: 'absolute',
              left: p * width - 5,
              top: -(3 + thickness / 2),
              width: 10,
              height: 10,
              borderRadius: 99,
              background: color,
              boxShadow: `0 0 18px ${color}`,
            }}
          />
        );
      })}
    </div>
  );
};
