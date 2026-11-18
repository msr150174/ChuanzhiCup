import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption, LightSweep } from '../../components/Chrome';
import { Kicker } from '../../components/ui';
import { PhoneFrame } from '../../components/ai';
import { C, EASE, F, enter, windowFade } from '../../theme';

/**
 * M1｜开场（210 帧 / 7s）
 * 三行适老化小字快闪 → 主标「老人不会用复杂 App / 但会说话」
 * → 右侧 PhoneFrame 浮起，「节律守护」首页（纯代码绘制）
 */

/* ============ 快闪小字 ============ */

const FLASH = [
  { t: '大字号', c: C.ink, at: 0 },
  { t: '长按 SOS', c: C.cyan, at: 9 },
  { t: '一句话提问', c: C.lime, at: 18 },
];

const FlashTags: React.FC = () => {
  const frame = useCurrentFrame();

  return (
    <div
      style={{
        position: 'absolute',
        left: 140,
        top: 168,
        display: 'flex',
        flexDirection: 'column',
        gap: 12,
      }}
    >
      {FLASH.map((f, i) => {
        const a = windowFade(frame, f.at, f.at + 28, 5);
        return (
          <div
            key={i}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              opacity: a,
              transform: `translateX(${(1 - a) * -16}px)`,
            }}
          >
            <div
              style={{
                width: 3,
                height: 26,
                background: f.c,
                boxShadow: `0 0 14px ${f.c}`,
              }}
            />
            <span
              style={{
                fontFamily: F.cn,
                fontSize: 30,
                fontWeight: 700,
                letterSpacing: 4,
                color: f.c,
              }}
            >
              {f.t}
            </span>
          </div>
        );
      })}
    </div>
  );
};

/* ============ 健康分环（SVG 纯代码） ============ */

const HealthRing: React.FC<{ value: number; from: number; size?: number }> = ({
  value,
  from,
  size = 196,
}) => {
  const frame = useCurrentFrame();
  const p = interpolate(frame, [from, from + 44], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  const r = size / 2 - 15;
  const circ = 2 * Math.PI * r;
  const shown = Math.round(value * p);

  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg
        width={size}
        height={size}
        style={{ position: 'absolute', inset: 0, transform: 'rotate(-90deg)' }}
      >
        <defs>
          <linearGradient id="m1ring" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={C.cyan} />
            <stop offset="100%" stopColor={C.lime} />
          </linearGradient>
        </defs>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="rgba(255,255,255,0.08)"
          strokeWidth={12}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="url(#m1ring)"
          strokeWidth={12}
          strokeLinecap="round"
          strokeDasharray={circ}
          strokeDashoffset={circ * (1 - (value / 100) * p)}
          style={{ filter: `drop-shadow(0 0 10px ${C.cyan}88)` }}
        />
      </svg>
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        <div
          style={{
            fontFamily: F.mono,
            fontSize: 54,
            fontWeight: 900,
            lineHeight: 1,
            letterSpacing: -2,
            color: C.ink,
          }}
        >
          {shown}
        </div>
        <div
          style={{
            marginTop: 8,
            fontFamily: F.cn,
            fontSize: 12,
            letterSpacing: 3,
            color: C.dim,
          }}
        >
          健康分
        </div>
      </div>
    </div>
  );
};

/* ============ 首页屏内示意 ============ */

const STATS = [
  { v: '6h20m', k: '睡眠' },
  { v: '4,120', k: '步数' },
  { v: '6/7', k: '打卡' },
];

const TABS = ['首页', '记录', 'AI', '我的'];

const HomeScreen: React.FC = () => {
  const frame = useCurrentFrame();
  const ringIn = enter(frame, 76, 18);
  // AI 助手入口的呼吸光
  const pulse = frame > 104 ? (Math.sin((frame - 104) / 8) + 1) / 2 : 0;
  const glowA = Math.round((0.16 + pulse * 0.3) * 255)
    .toString(16)
    .padStart(2, '0');

  return (
    <div
      style={{
        height: '100%',
        padding: '16px 20px 0',
        display: 'flex',
        flexDirection: 'column',
        fontFamily: F.cn,
        boxSizing: 'border-box',
      }}
    >
      {/* 顶部问候 */}
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between' }}>
        <div>
          <div
            style={{
              fontFamily: F.mono,
              fontSize: 11,
              letterSpacing: 3,
              color: C.cyan,
              opacity: 0.9,
            }}
          >
            节律守护
          </div>
          <div
            style={{
              marginTop: 6,
              fontSize: 23,
              fontWeight: 800,
              color: C.ink,
              letterSpacing: 1,
            }}
          >
            早上好，王阿姨
          </div>
          <div style={{ marginTop: 4, fontSize: 12, color: 'rgba(255,255,255,0.4)' }}>
            今天也要好好休息
          </div>
        </div>
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: 17,
            background: `linear-gradient(135deg, ${C.cyan}55, ${C.violet}44)`,
            border: `1px solid ${C.line}`,
          }}
        />
      </div>

      {/* 健康分环 */}
      <div
        style={{
          marginTop: 18,
          display: 'flex',
          justifyContent: 'center',
          opacity: ringIn,
          transform: `scale(${0.9 + ringIn * 0.1})`,
        }}
      >
        <HealthRing value={78} from={74} />
      </div>
      <div
        style={{
          marginTop: 8,
          textAlign: 'center',
          fontSize: 11,
          letterSpacing: 2,
          color: 'rgba(255,255,255,0.35)',
          opacity: ringIn,
        }}
      >
        睡眠 · 打卡 · 用药 综合
      </div>

      {/* 三个小指标 */}
      <div style={{ marginTop: 18, display: 'flex', gap: 8 }}>
        {STATS.map((s, i) => {
          const a = enter(frame, 84 + i * 4, 14);
          return (
            <div
              key={s.k}
              style={{
                flex: 1,
                padding: '10px 6px',
                borderRadius: 12,
                background: 'rgba(255,255,255,0.05)',
                border: `1px solid ${C.line}`,
                textAlign: 'center',
                opacity: a,
                transform: `translateY(${(1 - a) * 12}px)`,
              }}
            >
              <div
                style={{
                  fontFamily: F.mono,
                  fontSize: 16,
                  fontWeight: 700,
                  color: C.ink,
                }}
              >
                {s.v}
              </div>
              <div style={{ marginTop: 4, fontSize: 10, color: C.dimmer, letterSpacing: 1 }}>
                {s.k}
              </div>
            </div>
          );
        })}
      </div>

      {/* AI 助手入口 */}
      <div
        style={{
          marginTop: 16,
          padding: '13px 14px',
          borderRadius: 18,
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          background: `linear-gradient(115deg, ${C.cyan}24, ${C.violet}18)`,
          border: `1px solid ${C.cyan}${glowA}`,
          boxShadow: `0 0 ${14 + pulse * 26}px ${C.cyan}${glowA}`,
          opacity: enter(frame, 96, 16),
        }}
      >
        <div
          style={{
            width: 34,
            height: 34,
            borderRadius: 12,
            background: `${C.cyan}1F`,
            border: `1px solid ${C.cyan}66`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0,
          }}
        >
          <svg width="20" height="20" viewBox="0 0 24 24">
            <path
              d="M12 2.4l2.3 6.2 6.3 2.4-6.3 2.4L12 19.6l-2.3-6.2L3.4 11l6.3-2.4z"
              fill={C.cyan}
            />
          </svg>
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 17, fontWeight: 800, color: C.ink, letterSpacing: 1 }}>
            AI 助手
          </div>
          <div style={{ marginTop: 3, fontSize: 11, color: 'rgba(255,255,255,0.5)' }}>
            一句话问健康
          </div>
        </div>
        <div style={{ fontFamily: F.mono, fontSize: 20, color: C.cyan }}>›</div>
      </div>

      {/* 底部 tab */}
      <div
        style={{
          marginTop: 'auto',
          height: 54,
          borderTop: `1px solid ${C.line}`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-around',
        }}
      >
        {TABS.map((t, i) => (
          <div key={t} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
            <div
              style={{
                width: i === 0 ? 16 : 12,
                height: i === 0 ? 16 : 12,
                borderRadius: i === 0 ? 5 : 3,
                border: `1.5px solid ${i === 0 ? C.cyan : 'rgba(255,255,255,0.22)'}`,
                background: i === 0 ? `${C.cyan}33` : 'transparent',
              }}
            />
            <span
              style={{
                fontSize: 10,
                color: i === 0 ? C.cyan : 'rgba(255,255,255,0.3)',
                letterSpacing: 1,
              }}
            >
              {t}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};

/* ============ 场景 ============ */

export const M1Intro: React.FC = () => {
  const frame = useCurrentFrame();

  const a1 = enter(frame, 20, 18);
  const a2 = enter(frame, 44, 20);
  const bar = interpolate(frame, [56, 94], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });

  return (
    <AbsoluteFill>
      <Backdrop accent={C.cyan} second={C.violet} intensity={1} />

      <Kicker text="01 / 适老化 · 小护" from={4} color={C.cyan} style={{ position: 'absolute', left: 140, top: 108 }} />

      <FlashTags />

      {/* 主标第一行 */}
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 306,
          fontFamily: F.cn,
          fontSize: 88,
          fontWeight: 900,
          letterSpacing: -2,
          lineHeight: 1.1,
          color: C.ink,
          whiteSpace: 'nowrap',
          opacity: a1,
          transform: `translateY(${(1 - a1) * 42}px)`,
          clipPath: `inset(${(1 - a1) * 100}% 0 0 0)`,
        }}
      >
        老人不会用复杂 App
      </div>

      {/* 主标第二行 */}
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 424,
          fontFamily: F.cn,
          fontSize: 96,
          fontWeight: 900,
          letterSpacing: -1,
          lineHeight: 1.1,
          color: C.cyan,
          whiteSpace: 'nowrap',
          opacity: a2,
          transform: `translateY(${(1 - a2) * 48}px)`,
          textShadow: `0 0 70px ${C.cyan}66`,
          clipPath: `inset(${(1 - a2) * 100}% 0 0 0)`,
        }}
      >
        但会说话
      </div>

      {/* 下划光条 */}
      <div
        style={{
          position: 'absolute',
          left: 142,
          top: 566,
          width: 400 * bar,
          height: 6,
          borderRadius: 3,
          background: `linear-gradient(90deg, ${C.cyan}, ${C.lime}66, transparent)`,
          boxShadow: `0 0 24px ${C.cyan}AA`,
        }}
      />

      {/* 三条适老化标签落定 */}
      <div
        style={{
          position: 'absolute',
          left: 142,
          top: 618,
          display: 'flex',
          gap: 14,
        }}
      >
        {FLASH.map((f, i) => {
          const a = enter(frame, 100 + i * 8, 16);
          return (
            <div
              key={f.t}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '11px 20px',
                borderRadius: 999,
                border: `1px solid ${f.c}44`,
                background: `${f.c}0D`,
                opacity: a * 0.9,
                transform: `translateY(${(1 - a) * 16}px)`,
              }}
            >
              <div
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: 3,
                  background: f.c,
                  boxShadow: `0 0 10px ${f.c}`,
                }}
              />
              <span
                style={{
                  fontFamily: F.cn,
                  fontSize: 22,
                  fontWeight: 600,
                  letterSpacing: 2,
                  color: 'rgba(255,255,255,0.82)',
                }}
              >
                {f.t}
              </span>
            </div>
          );
        })}
      </div>

      {/* 手机外框 + 首页示意 */}
      <div style={{ position: 'absolute', left: 1330, top: 100 }}>
        <PhoneFrame width={400} from={66} accent={C.cyan}>
          <HomeScreen />
        </PhoneFrame>
      </div>

      <LightSweep from={96} dur={26} color="rgba(255,255,255,0.30)" />
      <Caption text="小护，一句话就能问" from={78} to={202} accent={C.cyan} />
    </AbsoluteFill>
  );
};
