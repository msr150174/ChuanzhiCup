import React from 'react';
import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import { C, EASE, F, enter } from '../../theme';
import { Backdrop, LightSweep } from '../../components/Chrome';
import { StatBig } from '../../components/ui';
import { PhoneFrame, SceneIndex, SectionTitle } from '../../components/ai';

/**
 * M5｜睡眠分析 / 健康报告 + 收尾｜场景内 0–420 帧（成片 46–60s）
 *
 * ① 0–140f   左 PhoneFrame：睡眠评分环 78（SVG）+ 一周七根柱状条；右三条能力说明
 * ② 140–220f 规模条：36 页小程序 / 153 个接口 / 12 张表
 * ③ 220–300f Slogan 砸入：让守护，从一句话开始
 * ④ 300–380f 署名 → 380–420f 整体淡出
 *
 * 动画只用 interpolate / spring，无任何 CSS animation。
 */

/* ============ 版式常量 ============ */
const M = 140;
const PHONE_W = 300;
const PHONE_TOP = 330;
const RIGHT_LEFT = 520;
const RIGHT_W = 1260;
const ROW_TOP = 330;
const ROW_STEP = 130;

/* ============ 评分环 ============ */
const RING = 200;
const RING_STROKE = 13;
const RING_R = (RING - RING_STROKE) / 2;
const RING_C = 2 * Math.PI * RING_R;

/* 一周七根柱：只做视觉示意，不标数值 */
const BARS = [0.62, 0.78, 0.52, 0.86, 0.7, 0.94, 0.66];
const DAYS = ['一', '二', '三', '四', '五', '六', '日'];

/** 线性钳位 0–1 */
const ramp = (f: number, a: number, b: number) =>
  interpolate(f, [a, b], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

/** #RRGGBB + 透明度 → 8 位十六进制 */
const withA = (hex: string, a: number) => {
  const v = Math.round(Math.max(0, Math.min(1, a)) * 255)
    .toString(16)
    .padStart(2, '0');
  return `${hex}${v}`;
};

/* ============ 右侧三条说明 ============ */
const ITEMS: { n: string; title: string; desc: string; color: string }[] = [
  {
    n: '01',
    title: '睡眠分析',
    desc: '时序数据算评分，不是写死的假数据',
    color: C.cyan,
  },
  {
    n: '02',
    title: '每日报告 / 每周报告',
    desc: '聚合后推给家属',
    color: C.violet,
  },
  {
    n: '03',
    title: '家属端同步',
    desc: '同一份数据，两种信息密度',
    color: C.lime,
  },
];

/* ============ 手机内画面：评分环 + 一周柱状条 ============ */
const SleepPanel: React.FC = () => {
  const frame = useCurrentFrame();
  const ringP = interpolate(frame, [44, 100], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  const shown = Math.round(78 * ringP);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        padding: '18px 20px 22px',
      }}
    >
      {/* 顶部小标签 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
        }}
      >
        <span
          style={{
            fontFamily: F.cn,
            fontSize: 15,
            fontWeight: 600,
            letterSpacing: 2,
            color: 'rgba(255,255,255,0.62)',
          }}
        >
          睡眠评分
        </span>
        <span
          style={{
            fontFamily: F.mono,
            fontSize: 12,
            letterSpacing: 2,
            color: C.dimmer,
          }}
        >
          7D
        </span>
      </div>

      {/* 评分环 */}
      <div
        style={{
          position: 'relative',
          width: RING,
          height: RING,
          margin: '16px auto 0',
        }}
      >
        <svg width={RING} height={RING} style={{ transform: 'rotate(-90deg)' }}>
          <circle
            cx={RING / 2}
            cy={RING / 2}
            r={RING_R}
            fill="none"
            stroke="rgba(255,255,255,0.08)"
            strokeWidth={RING_STROKE}
          />
          <circle
            cx={RING / 2}
            cy={RING / 2}
            r={RING_R}
            fill="none"
            stroke={C.cyan}
            strokeWidth={RING_STROKE}
            strokeLinecap="round"
            strokeDasharray={RING_C}
            strokeDashoffset={RING_C * (1 - 0.78 * ringP)}
            style={{ filter: `drop-shadow(0 0 7px ${withA(C.cyan, 0.85)})` }}
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
              fontSize: 66,
              fontWeight: 900,
              lineHeight: 1,
              letterSpacing: -3,
              color: C.ink,
              fontVariantNumeric: 'tabular-nums',
              textShadow: `0 0 30px ${withA(C.cyan, 0.45)}`,
            }}
          >
            {shown}
          </div>
          <div
            style={{
              marginTop: 8,
              fontFamily: F.cn,
              fontSize: 13,
              letterSpacing: 3,
              color: C.dim,
            }}
          >
            分
          </div>
        </div>
      </div>

      {/* 一周七根柱 */}
      <div style={{ marginTop: 'auto' }}>
        <div style={{ height: 1, background: C.line, marginBottom: 14 }} />
        <div
          style={{
            display: 'flex',
            alignItems: 'flex-end',
            gap: 8,
            height: 104,
          }}
        >
          {BARS.map((h, i) => {
            const p = enter(frame, 56 + i * 7, 16);
            return (
              <div
                key={i}
                style={{
                  flex: 1,
                  height: `${h * 100 * p}%`,
                  borderRadius: '6px 6px 3px 3px',
                  background: `linear-gradient(180deg, ${C.cyan}, ${withA(
                    C.violet,
                    0.6,
                  )})`,
                  boxShadow: `0 0 14px ${withA(C.cyan, 0.4 * p)}`,
                }}
              />
            );
          })}
        </div>
        <div style={{ display: 'flex', gap: 8, marginTop: 9 }}>
          {DAYS.map((d, i) => (
            <div
              key={i}
              style={{
                flex: 1,
                textAlign: 'center',
                fontFamily: F.cn,
                fontSize: 12,
                color: C.dimmer,
              }}
            >
              {d}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

/* ============ 主场景 ============ */
export const M5Report: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  /* --- 整体收尾 --- */
  const outro = 1 - ramp(frame, 380, 418);

  /* --- 阶段 1：数据画面 --- */
  const detailIn = enter(frame, 20, 20);
  const detailOut = 1 - ramp(frame, 208, 234);

  /* --- 阶段 2：规模条 --- */
  const statRule = enter(frame, 142, 24);

  /* --- 阶段 3：Slogan --- */
  const sloganSp = spring({
    frame: Math.max(0, frame - 220),
    fps,
    config: { damping: 18, stiffness: 110, mass: 1 },
  });
  const sloganA = ramp(frame, 220, 234);

  /* --- 阶段 4：署名 --- */
  const sigA = enter(frame, 300, 20);
  const sigP = enter(frame, 302, 26);

  return (
    <AbsoluteFill style={{ opacity: outro }}>
      <Backdrop accent={C.cyan} second={C.violet}>
        <LightSweep from={222} dur={26} color="rgba(0,229,255,0.32)" />

        {/* ============ 章节标题 ============ */}
        <div style={{ opacity: detailOut }}>
          <SectionTitle
            kicker="SLEEP · REPORT"
            title="睡眠分析 · 健康报告"
            sub="数据不是摆着看的"
            from={0}
            accent={C.cyan}
            size={72}
          />
        </div>
        <div style={{ opacity: detailOut }}>
          <SceneIndex n="05" from={10} accent={C.cyan} />
        </div>

        {/* ============ 阶段 1 + 2：数据画面 ============ */}
        <div style={{ position: 'absolute', inset: 0, opacity: detailOut }}>
          {/* 左：手机 */}
          <div
            style={{
              position: 'absolute',
              left: M,
              top: PHONE_TOP,
              opacity: detailIn,
              transform: `translateY(${(1 - detailIn) * 40}px)`,
            }}
          >
            <PhoneFrame width={PHONE_W} from={24} accent={C.cyan}>
              <SleepPanel />
            </PhoneFrame>
          </div>

          {/* 右：三条说明 */}
          {ITEMS.map((it, i) => {
            const at = 48 + i * 14;
            const a = enter(frame, at, 18);
            const barH = enter(frame, at + 4, 26);
            return (
              <div
                key={it.n}
                style={{
                  position: 'absolute',
                  left: RIGHT_LEFT,
                  top: ROW_TOP + i * ROW_STEP,
                  width: RIGHT_W,
                  height: 116,
                  display: 'flex',
                  alignItems: 'center',
                  gap: 28,
                  opacity: a,
                  transform: `translateX(${(1 - a) * 44}px)`,
                }}
              >
                {/* 行尾细线，把右栏横向铺满 */}
                <div
                  style={{
                    position: 'absolute',
                    left: 0,
                    right: 0,
                    bottom: -14,
                    height: 1,
                    background:
                      'linear-gradient(90deg, rgba(255,255,255,0.16), rgba(255,255,255,0.05) 62%, transparent)',
                  }}
                />
                <div
                  style={{
                    width: 3,
                    height: 94 * barH,
                    borderRadius: 2,
                    background: it.color,
                    boxShadow: `0 0 16px ${withA(it.color, 0.9)}`,
                  }}
                />
                <div
                  style={{
                    width: 58,
                    fontFamily: F.mono,
                    fontSize: 26,
                    fontWeight: 800,
                    letterSpacing: 1,
                    color: withA(it.color, 0.9),
                  }}
                >
                  {it.n}
                </div>
                <div style={{ flex: 1 }}>
                  <div
                    style={{
                      fontFamily: F.cn,
                      fontSize: 40,
                      fontWeight: 900,
                      letterSpacing: -0.5,
                      color: C.ink,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {it.title}
                  </div>
                  <div
                    style={{
                      marginTop: 10,
                      fontFamily: F.cn,
                      fontSize: 24,
                      fontWeight: 500,
                      letterSpacing: 0.5,
                      color: 'rgba(255,255,255,0.62)',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {it.desc}
                  </div>
                </div>
              </div>
            );
          })}

          {/* 规模条 */}
          <div
            style={{
              position: 'absolute',
              left: RIGHT_LEFT,
              top: 748,
              width: RIGHT_W,
              opacity: enter(frame, 140, 18),
            }}
          >
            <div
              style={{
                height: 1,
                width: RIGHT_W * statRule,
                background: `linear-gradient(90deg, ${C.cyan}, ${withA(
                  C.violet,
                  0.5,
                )} 60%, transparent)`,
                boxShadow: `0 0 16px ${withA(C.cyan, 0.45)}`,
              }}
            />
            <div style={{ display: 'flex', gap: 92, marginTop: 32 }}>
              <StatBig value={36} suffix="页" label="小程序" from={146} size={84} />
              <StatBig value={153} suffix="个" label="接口" from={154} size={84} />
              <StatBig value={12} suffix="张" label="表" from={162} size={84} />
            </div>
          </div>
        </div>

        {/* ============ 阶段 3 + 4：Slogan 与署名 ============ */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            paddingBottom: 40,
          }}
        >
          <div
            style={{
              fontFamily: F.cn,
              fontSize: 96,
              fontWeight: 900,
              letterSpacing: -2,
              lineHeight: 1.1,
              color: C.ink,
              whiteSpace: 'nowrap',
              opacity: sloganA,
              transform: `translateY(${(1 - sloganSp) * 48}px) scale(${
                1.1 - 0.1 * sloganSp
              })`,
              textShadow: `0 0 70px ${withA(C.cyan, 0.32)}`,
            }}
          >
            让守护，从
            <span
              style={{
                color: C.cyan,
                textShadow: `0 0 34px ${withA(C.cyan, 0.55)}`,
              }}
            >
              一句话
            </span>
            开始
          </div>

          <div
            style={{
              marginTop: 60,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              gap: 20,
              opacity: sigA,
              transform: `translateY(${(1 - sigA) * 26}px)`,
            }}
          >
            <div
              style={{
                width: 260 * sigP,
                height: 1,
                background: `linear-gradient(90deg, transparent, ${withA(
                  C.cyan,
                  0.9,
                )}, transparent)`,
              }}
            />
            <div
              style={{
                fontFamily: F.cn,
                fontSize: 36,
                fontWeight: 700,
                letterSpacing: 4,
                color: C.cyan,
                whiteSpace: 'nowrap',
                textShadow: `0 0 34px ${withA(C.cyan, 0.5)}`,
              }}
            >
              AI 节律守护 · 智能无感
            </div>
            <div
              style={{
                fontFamily: F.cn,
                fontSize: 28,
                fontWeight: 500,
                letterSpacing: 4,
                color: C.dim,
                whiteSpace: 'nowrap',
              }}
            >
              微信小程序 · 小护
            </div>
          </div>
        </div>
      </Backdrop>
    </AbsoluteFill>
  );
};
