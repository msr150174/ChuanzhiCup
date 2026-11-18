import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption } from '../components/Chrome';
import { Kicker, Rule } from '../components/ui';
import { C, EASE, F, enter } from '../theme';

/**
 * S6｜工程规模｜150 帧（5s）
 * 五行数字瀑布式从下往上滚入（行间隔 5 帧），数字从 0 滚到目标值（34 帧）
 * 颜色交替 青 / 白 / 绿；背景网格额外叠一层更快的网格制造速度感
 */

type RowData = { value: number; label: string; color: string };

const ROWS: RowData[] = [
  { value: 184, label: '后端方法级接口', color: C.cyan },
  { value: 25, label: 'Controller', color: C.ink },
  { value: 12, label: '数据库表', color: C.lime },
  { value: 17565, label: '行 Java 代码', color: C.cyan },
  { value: 36, label: '小程序页面', color: C.ink },
];

const ROW_STEP = 5;

const Row: React.FC<{ row: RowData; i: number }> = ({ row, i }) => {
  const frame = useCurrentFrame();
  const from = i * ROW_STEP - 6;

  // 滚入：下→上 + 透明度 + 遮罩
  const a = interpolate(frame, [from, from + 16], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  // 数字滚动
  const p = interpolate(frame, [from, from + 34], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  const shown = Math.round(row.value * p).toLocaleString('en-US');
  const y = (1 - a) * 78;

  return (
    <div style={{ opacity: a, transform: `translateY(${y}px)` }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 36, height: 116 }}>
        {/* 行号 */}
        <div
          style={{
            width: 52,
            fontFamily: F.mono,
            fontSize: 18,
            fontWeight: 600,
            letterSpacing: 3,
            color: C.dimmer,
          }}
        >
          {String(i + 1).padStart(2, '0')}
        </div>

        {/* 数字 */}
        <div
          style={{
            width: 430,
            textAlign: 'right',
            fontFamily: F.mono,
            fontSize: 96,
            fontWeight: 900,
            lineHeight: 1,
            letterSpacing: -4,
            color: row.color,
            fontVariantNumeric: 'tabular-nums',
            textShadow: `0 0 46px ${row.color}55`,
            whiteSpace: 'nowrap',
          }}
        >
          {shown}
        </div>

        {/* 标签 */}
        <div
          style={{
            flex: 1,
            fontFamily: F.cn,
            fontSize: 38,
            fontWeight: 500,
            letterSpacing: 1,
            color: 'rgba(255,255,255,0.82)',
            whiteSpace: 'nowrap',
          }}
        >
          {row.label}
        </div>

        {/* 尾部速度线 */}
        <div
          style={{
            width: 140,
            height: 2,
            background: `linear-gradient(90deg, ${row.color}, ${row.color}00)`,
            opacity: p,
          }}
        />
      </div>
      {/* 行分隔线 */}
      <div
        style={{
          height: 1,
          width: '100%',
          background: 'linear-gradient(90deg, rgba(255,255,255,0.16), rgba(255,255,255,0.02) 72%)',
          opacity: a,
        }}
      />
    </div>
  );
};

export const S6Scale: React.FC = () => {
  const frame = useCurrentFrame();
  const titleA = enter(frame, 0, 14);

  // 额外的高速网格层（Backdrop 自带网格较慢，这里叠加一层加速层）
  const fastX = (frame * 1.9) % 96;
  const fastY = (frame * 1.2) % 96;

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <Backdrop accent={C.cyan} second={C.lime} intensity={0.85} gridOpacity={0.04}>
        {/* 加速网格 */}
        <AbsoluteFill
          style={{
            backgroundImage: `linear-gradient(${C.cyan}22 1px, transparent 1px), linear-gradient(90deg, ${C.cyan}22 1px, transparent 1px)`,
            backgroundSize: '96px 96px',
            backgroundPosition: `${fastX}px ${fastY}px`,
            opacity: 0.5,
            maskImage: 'radial-gradient(ellipse 70% 62% at 50% 52%, #000 20%, transparent 100%)',
            WebkitMaskImage:
              'radial-gradient(ellipse 70% 62% at 50% 52%, #000 20%, transparent 100%)',
          }}
        />

        {/* 顶部标识 */}
        <div style={{ position: 'absolute', left: 140, top: 126 }}>
          <Kicker text="ENGINEERING SCALE" from={0} color={C.cyan} />
          <Rule from={4} width={300} color={C.cyan} style={{ marginTop: 18 }} />
        </div>

        {/* 右上角计数提示 */}
        <div
          style={{
            position: 'absolute',
            right: 140,
            top: 130,
            fontFamily: F.mono,
            fontSize: 20,
            fontWeight: 600,
            letterSpacing: 5,
            color: 'rgba(255,255,255,0.34)',
            opacity: titleA,
          }}
        >
          05 METRICS
        </div>

        {/* 数字瀑布 */}
        <div style={{ position: 'absolute', left: 140, top: 214, width: 1640 }}>
          {ROWS.map((row, i) => (
            <Row key={row.label} row={row} i={i} />
          ))}
        </div>

        <Caption text="可运行、可核查的工程，不是 PPT。" from={10} to={168} accent={C.lime} />
      </Backdrop>
    </AbsoluteFill>
  );
};
