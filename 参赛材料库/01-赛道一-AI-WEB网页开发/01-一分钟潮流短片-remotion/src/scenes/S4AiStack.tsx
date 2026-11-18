import React from 'react';
import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import { C, F, enter } from '../theme';
import { Backdrop, Caption } from '../components/Chrome';
import { FlowLine, GlassCard, Headline, Kicker, Rule } from '../components/ui';

/**
 * S4｜AI 三级降级｜场景内 0–420 帧（成片 25–39s，核心得分点）
 *
 * 一条数据脉冲从左往右跑，三级链路依次尝试、依次熄灭：
 * ① Ollama 本地模型 → TIMEOUT → ② Coze 云端智能体 → NO TOKEN
 * → ③ 本地规则库 12 条 · ALWAYS ON → 结论条 provider = rule
 *
 * 动画只用 interpolate / spring，逐帧可渲染，不含任何 CSS 动画。
 */

/* ============ 版式常量（1920×1080，左右安全边距 140） ============ */
const M = 140;
const CARD_W = 470;
const CARD_H = 320;
const GAP = 115;
const CARD_TOP = 372;
const PAD = 38;
const CARD_MID_Y = CARD_TOP + CARD_H / 2; // 532

const cardX = (i: number) => M + i * (CARD_W + GAP); // 140 / 725 / 1310
const gapX = (i: number) => cardX(i) + CARD_W; // 610 / 1195

/** 线性钳位到 0–1 */
const ramp = (f: number, a: number, b: number) =>
  interpolate(f, [a, b], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

/** #RRGGBB + 透明度 → 8 位十六进制色 */
const withA = (hex: string, a: number) => {
  const v = Math.round(Math.max(0, Math.min(1, a)) * 255)
    .toString(16)
    .padStart(2, '0');
  return `${hex}${v}`;
};

/* ============ 三级链路数据（名称与副标与分镜脚本完全一致） ============ */
type Part = { text: string; mono?: boolean };

type CardSpec = {
  idx: string;
  name: string;
  parts: Part[];
  sub: string;
  color: string;
  enterAt: number;
  glowIn: number;
  glowOut: number | null;
  sweep: [number, number];
  dieAt: number | null;
  tag: { text: string; color: string; at: number } | null;
  watermark: string;
};

const CARDS: CardSpec[] = [
  {
    idx: '①',
    name: 'Ollama',
    parts: [{ text: '本地模型 ' }, { text: 'qwen2.5:7b', mono: true }],
    sub: '零 API 成本 · 数据不出本地',
    color: C.cyan,
    enterAt: 20,
    glowIn: 88,
    glowOut: 150,
    sweep: [94, 142],
    dieAt: 150,
    tag: { text: 'TIMEOUT', color: C.warn, at: 150 },
    watermark: '01',
  },
  {
    idx: '②',
    name: 'Coze',
    parts: [{ text: '云端智能体' }],
    sub: '流式 SSE · 需 Token',
    color: C.violet,
    enterAt: 32,
    glowIn: 148,
    glowOut: 210,
    sweep: [154, 200],
    dieAt: 210,
    tag: { text: 'NO TOKEN', color: C.warn, at: 210 },
    watermark: '02',
  },
  {
    idx: '③',
    name: 'Rule',
    parts: [{ text: '本地规则库 ' }, { text: '12', mono: true }, { text: ' 条' }],
    sub: '永远可用 · 零延迟',
    color: C.lime,
    enterAt: 44,
    glowIn: 224,
    glowOut: null,
    sweep: [232, 270],
    dieAt: null,
    tag: { text: 'ALWAYS ON', color: C.lime, at: 246 },
    watermark: '03',
  },
];

/* ============ 卡间连线：底槽 + 流动光点 + 箭头 + 脉冲头 ============ */
const Connector: React.FC<{
  x: number;
  y: number;
  w: number;
  color: string;
  flow: number;
  dead: number;
  dotP: number;
  dotColor: string;
  fade: number;
}> = ({ x, y, w, color, flow, dead, dotP, dotColor, fade }) => (
  <div style={{ position: 'absolute', inset: 0, opacity: fade }}>
    {/* 底槽细线 */}
    <div
      style={{
        position: 'absolute',
        left: x,
        top: y - 1,
        width: w,
        height: 2,
        borderRadius: 2,
        background: `linear-gradient(90deg, ${withA(color, 0.16)}, ${withA(
          color,
          0.46,
        )}, ${withA(color, 0.16)})`,
        opacity: 0.3 + 0.7 * flow,
        filter: `grayscale(${dead})`,
      }}
    />
    {/* 流动光点（共享组件 FlowLine） */}
    {flow > 0.01 && (
      <div
        style={{
          position: 'absolute',
          left: x,
          top: y - 1,
          opacity: flow * 0.85,
          filter: `grayscale(${dead})`,
        }}
      >
        <FlowLine width={w} color={color} thickness={2} dots={2} speed={1.6} />
      </div>
    )}
    {/* 方向箭头（实心三角，指向右） */}
    <div
      style={{
        position: 'absolute',
        left: x + w / 2 - 3,
        top: y - 6,
        width: 0,
        height: 0,
        borderTop: '6px solid transparent',
        borderBottom: '6px solid transparent',
        borderLeft: `9px solid ${withA(color, 0.45 + 0.5 * flow)}`,
        filter: `grayscale(${dead})`,
      }}
    />
    {/* 脉冲头 + 拖尾 */}
    {dotP >= 0 && (
      <>
        <div
          style={{
            position: 'absolute',
            left: x + dotP * w - 86,
            top: y - 2,
            width: 86,
            height: 4,
            background: `linear-gradient(90deg, transparent, ${withA(
              dotColor,
              0.9,
            )})`,
          }}
        />
        <div
          style={{
            position: 'absolute',
            left: x + dotP * w - 8,
            top: y - 8,
            width: 16,
            height: 16,
            borderRadius: 99,
            background: C.ink,
            boxShadow: `0 0 12px ${dotColor}, 0 0 32px ${dotColor}, 0 0 64px ${withA(
              dotColor,
              0.6,
            )}`,
          }}
        />
      </>
    )}
  </div>
);

/* ============ 主场景 ============ */
export const S4AiStack: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  /* --- 收束：最后 20 帧整体轻微淡出 --- */
  const outro = 1 - ramp(frame, 398, 418);
  const outroScale = interpolate(frame, [398, 418], [1, 0.985], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  /* --- 标题区 --- */
  const kickerA = enter(frame, 4, 14);
  const headRuleW = 260 * enter(frame, 26, 22);
  const badgeA = enter(frame, 58, 16);
  const badgePulse = 0.55 + 0.45 * Math.sin(frame / 7);

  /* --- 脉冲 / 连线时间轴 --- */
  const lineFade = ramp(frame, 58, 78); // 连线随卡片入场后再浮现
  const dot1 = frame >= 100 && frame <= 152 ? ramp(frame, 100, 150) : -1;
  const dot2 = frame >= 208 && frame <= 238 ? ramp(frame, 208, 236) : -1;
  const flow1 = Math.min(ramp(frame, 96, 106), 1 - ramp(frame, 146, 158));
  const flow2 = Math.min(
    1,
    Math.min(ramp(frame, 204, 214), 1 - ramp(frame, 234, 250)) +
      0.6 * ramp(frame, 242, 262),
  );
  const dead1 = ramp(frame, 150, 164);
  const dead2 = ramp(frame, 210, 224);
  const g2Color = frame < 238 ? C.cyan : C.lime;
  const g2Dead = dead2 * (1 - ramp(frame, 236, 250));

  /* --- ③ 落地爆点 --- */
  const burst = ramp(frame, 228, 268);
  const burstGlow = Math.min(ramp(frame, 224, 240), 1 - ramp(frame, 250, 292));

  /* --- 结论条 --- */
  const barS = spring({
    frame: Math.max(0, frame - 270),
    fps,
    config: { damping: 16, stiffness: 90, mass: 1 },
  });
  const barA = Math.min(1, barS * 1.3);
  const barGlow = 0.5 + 0.5 * Math.sin(frame / 11);

  return (
    <AbsoluteFill style={{ opacity: outro, transform: `scale(${outroScale})` }}>
      <Backdrop accent={C.cyan}>
        {/* ============ 标题区 ============ */}
        <div style={{ position: 'absolute', left: M, top: 94 }}>
          <div style={{ opacity: kickerA }}>
            <Kicker text="AI FALLBACK CHAIN" from={4} color={C.cyan} />
          </div>
          <div style={{ marginTop: 16 }}>
            <Headline lines={['三级 AI 降级']} from={10} size={96} />
          </div>
          <div
            style={{
              marginTop: 24,
              height: 2,
              width: headRuleW,
              background: `linear-gradient(90deg, ${C.cyan}, ${withA(
                C.violet,
                0.5,
              )} 60%, transparent)`,
              boxShadow: `0 0 18px ${withA(C.cyan, 0.5)}`,
            }}
          />
        </div>

        {/* ============ 右上角缓存徽标 ============ */}
        <div
          style={{
            position: 'absolute',
            top: 100,
            right: M,
            display: 'flex',
            alignItems: 'center',
            gap: 14,
            padding: '13px 24px',
            borderRadius: 999,
            border: `1px solid ${withA(C.violet, 0.45)}`,
            background: `linear-gradient(155deg, ${withA(
              C.violet,
              0.16,
            )}, rgba(255,255,255,0.02))`,
            boxShadow: `0 0 32px ${withA(C.violet, 0.18)}`,
            opacity: badgeA,
            transform: `translateY(${(1 - badgeA) * -12}px)`,
          }}
        >
          <div
            style={{
              width: 9,
              height: 9,
              borderRadius: 99,
              background: C.violet,
              boxShadow: `0 0 ${14 * badgePulse}px ${C.violet}`,
              opacity: 0.7 + 0.3 * badgePulse,
            }}
          />
          <div
            style={{
              fontFamily: F.mono,
              fontSize: 20,
              fontWeight: 700,
              letterSpacing: 2,
              color: 'rgba(255,255,255,0.8)',
              whiteSpace: 'nowrap',
            }}
          >
            Redis CACHE · TTL 300s
          </div>
        </div>

        {/* ============ 三级链路卡片 ============ */}
        {CARDS.map((c, i) => {
          const sp = spring({
            frame: Math.max(0, frame - c.enterAt),
            fps,
            config: { damping: 14, stiffness: 120, mass: 0.9 },
          });
          const appear = Math.min(1, Math.max(0, sp) * 1.2);
          const glowIn = ramp(frame, c.glowIn, c.glowIn + 14);
          const glowOut =
            c.glowOut === null
              ? 1
              : 1 - ramp(frame, c.glowOut - 4, c.glowOut + 10);
          const glow = Math.max(0, Math.min(glowIn, glowOut));
          const dead = c.dieAt === null ? 0 : ramp(frame, c.dieAt, c.dieAt + 10);
          const bump = c.dieAt === null ? ramp(frame, 234, 260) : 0;
          const jitter =
            c.dieAt !== null && frame >= c.dieAt
              ? Math.sin((frame - c.dieAt) * 2.1) *
                4 *
                (1 - ramp(frame, c.dieAt, c.dieAt + 10))
              : 0;

          /* 卡内扫描光带 */
          const sweepP = ramp(frame, c.sweep[0], c.sweep[1]);
          const sweepEnv = Math.min(
            ramp(frame, c.sweep[0], c.sweep[0] + 8),
            1 - ramp(frame, c.sweep[1] - 10, c.sweep[1]),
          );

          /* 状态小标签（不参与卡片去饱和，保证失败标签始终是红的） */
          const tagSp = c.tag
            ? spring({
                frame: Math.max(0, frame - c.tag.at),
                fps,
                config: { damping: 11, stiffness: 240, mass: 0.6 },
              })
            : 0;

          return (
            <div
              key={c.name}
              style={{
                position: 'absolute',
                left: cardX(i),
                top: CARD_TOP,
                width: CARD_W,
                height: CARD_H,
                transformOrigin: '50% 50%',
                transform: `translateY(${(1 - sp) * 56}px) translateX(${jitter}px) scale(${
                  (0.94 + 0.06 * sp) * (1 + 0.04 * bump)
                })`,
              }}
            >
              {/* 卡片本体（熄灭时整卡去饱和 + 降透明） */}
              <div
                style={{
                  width: '100%',
                  height: '100%',
                  opacity: appear * (1 - dead * 0.55),
                  filter: `grayscale(${dead})`,
                }}
              >
                <GlassCard
                  accent={c.color}
                  padding={PAD}
                  radius={24}
                  style={{
                    width: '100%',
                    height: '100%',
                    border: `1px solid ${
                      glow > 0.06 ? withA(c.color, 0.26 + 0.52 * glow) : C.line
                    }`,
                    background: `linear-gradient(155deg, ${withA(
                      c.color,
                      0.05 + 0.08 * glow,
                    )}, rgba(255,255,255,0.018))`,
                    boxShadow:
                      glow > 0.02
                        ? `0 0 ${36 * glow}px ${withA(
                            c.color,
                            0.5 * glow,
                          )}, 0 0 ${100 * glow}px ${withA(
                            c.color,
                            0.18 * glow,
                          )}, inset 0 1px 0 rgba(255,255,255,0.10), 0 24px 60px rgba(0,0,0,0.45)`
                        : 'inset 0 1px 0 rgba(255,255,255,0.10), 0 24px 60px rgba(0,0,0,0.45)',
                  }}
                >
                  {/* 顶部细线：点亮时亮起 */}
                  <div
                    style={{
                      position: 'absolute',
                      top: -PAD,
                      left: -PAD,
                      right: -PAD,
                      height: 2,
                      background: c.color,
                      opacity: 0.26 + 0.74 * glow,
                      boxShadow:
                        glow > 0.05 ? `0 0 ${18 * glow}px ${c.color}` : 'none',
                    }}
                  />

                  {/* 卡内扫描光带 */}
                  {sweepEnv > 0.01 && (
                    <div
                      style={{
                        position: 'absolute',
                        top: -PAD,
                        bottom: -PAD,
                        width: 170,
                        left: -PAD + (-0.4 + 1.4 * sweepP) * CARD_W,
                        background: `linear-gradient(90deg, transparent, ${withA(
                          c.color,
                          0.24,
                        )}, transparent)`,
                        filter: 'blur(4px)',
                        opacity: sweepEnv * 0.95,
                      }}
                    />
                  )}

                  {/* 熄灭瞬间的红色描边闪 */}
                  {c.dieAt !== null &&
                    frame >= c.dieAt &&
                    frame < c.dieAt + 16 && (
                      <div
                        style={{
                          position: 'absolute',
                          top: -PAD,
                          left: -PAD,
                          right: -PAD,
                          bottom: -PAD,
                          borderRadius: 24,
                          border: `2px solid ${C.warn}`,
                          opacity:
                            0.85 * (1 - ramp(frame, c.dieAt, c.dieAt + 14)),
                          boxShadow: `inset 0 0 60px ${withA(C.warn, 0.3)}`,
                        }}
                      />
                    )}

                  {/* 水印序号 */}
                  <div
                    style={{
                      position: 'absolute',
                      right: -PAD + 30,
                      bottom: -PAD + 2,
                      fontFamily: F.mono,
                      fontSize: 118,
                      fontWeight: 900,
                      lineHeight: 1,
                      letterSpacing: -6,
                      color: C.ink,
                      opacity: 0.055,
                    }}
                  >
                    {c.watermark}
                  </div>

                  {/* 内容：顶行 / 主标居中 / 副标贴底 */}
                  <div
                    style={{
                      position: 'relative',
                      display: 'flex',
                      flexDirection: 'column',
                      height: '100%',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'baseline',
                        gap: 12,
                        paddingRight: 108,
                      }}
                    >
                      <span
                        style={{
                          fontFamily: F.cn,
                          fontSize: 26,
                          lineHeight: 1,
                          color:
                            glow > 0.2 ? c.color : 'rgba(255,255,255,0.45)',
                        }}
                      >
                        {c.idx}
                      </span>
                      <span
                        style={{
                          fontFamily: F.mono,
                          fontSize: 24,
                          fontWeight: 700,
                          letterSpacing: 5,
                          color:
                            glow > 0.2 ? c.color : 'rgba(255,255,255,0.55)',
                        }}
                      >
                        {c.name}
                      </span>
                    </div>

                    <div
                      style={{
                        flex: 1,
                        display: 'flex',
                        flexDirection: 'column',
                        justifyContent: 'center',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          alignItems: 'baseline',
                          fontFamily: F.cn,
                          fontSize: 34,
                          fontWeight: 800,
                          lineHeight: 1.24,
                          letterSpacing: -0.5,
                          whiteSpace: 'pre',
                          color: glow > 0.2 ? C.ink : 'rgba(255,255,255,0.88)',
                          textShadow:
                            glow > 0.2
                              ? `0 0 ${18 * glow}px ${withA(c.color, 0.6)}`
                              : 'none',
                        }}
                      >
                        {c.parts.map((p, k) => (
                          <span
                            key={k}
                            style={
                              p.mono
                                ? {
                                    fontFamily: F.mono,
                                    fontSize: 31,
                                    fontWeight: 800,
                                    letterSpacing: -1,
                                  }
                                : undefined
                            }
                          >
                            {p.text}
                          </span>
                        ))}
                      </div>
                    </div>

                    <div>
                      <Rule
                        from={c.enterAt + 8}
                        width={CARD_W - PAD * 2}
                        color={withA(c.color, 0.55)}
                        style={{ marginBottom: 16 }}
                      />
                      <div
                        style={{
                          fontFamily: F.cn,
                          fontSize: 23,
                          fontWeight: 500,
                          letterSpacing: 0.5,
                          whiteSpace: 'nowrap',
                          color: 'rgba(255,255,255,0.6)',
                        }}
                      >
                        {c.sub}
                      </div>
                    </div>
                  </div>
                </GlassCard>
              </div>

              {/* 状态标签：浮在卡片之上，始终保持本色 */}
              {c.tag && frame >= c.tag.at && (
                <div
                  style={{
                    position: 'absolute',
                    top: PAD - 6,
                    right: PAD,
                    padding: '7px 14px',
                    borderRadius: 6,
                    border: `1px solid ${withA(c.tag.color, 0.62)}`,
                    background: withA(c.tag.color, 0.14),
                    color: c.tag.color,
                    fontFamily: F.mono,
                    fontSize: 16,
                    fontWeight: 700,
                    letterSpacing: 2,
                    whiteSpace: 'nowrap',
                    opacity: Math.min(1, Math.max(0, tagSp) * 1.6),
                    transform: `scale(${0.7 + 0.3 * tagSp})`,
                    boxShadow: `0 0 18px ${withA(c.tag.color, 0.32)}`,
                  }}
                >
                  {c.tag.text}
                </div>
              )}
            </div>
          );
        })}

        {/* ============ 卡间连线 ============ */}
        <Connector
          x={gapX(0)}
          y={CARD_MID_Y}
          w={GAP}
          color={C.cyan}
          flow={flow1}
          dead={dead1}
          dotP={dot1}
          dotColor={C.cyan}
          fade={lineFade}
        />
        <Connector
          x={gapX(1)}
          y={CARD_MID_Y}
          w={GAP}
          color={g2Color}
          flow={flow2}
          dead={g2Dead}
          dotP={dot2}
          dotColor={g2Color}
          fade={lineFade}
        />

        {/* ============ ③ 落地爆点 ============ */}
        {burstGlow > 0.01 && (
          <div
            style={{
              position: 'absolute',
              left: cardX(2) + CARD_W / 2 - 320,
              top: CARD_TOP + CARD_H / 2 - 320,
              width: 640,
              height: 640,
              borderRadius: '50%',
              background: `radial-gradient(circle, ${withA(
                C.lime,
                0.22,
              )} 0%, transparent 66%)`,
              filter: 'blur(10px)',
              opacity: burstGlow,
            }}
          />
        )}
        {burst > 0 && burst < 1 && (
          <div
            style={{
              position: 'absolute',
              left: cardX(2),
              top: CARD_TOP,
              width: CARD_W,
              height: CARD_H,
              borderRadius: 24,
              border: `2px solid ${withA(C.lime, 0.9)}`,
              boxShadow: `0 0 40px ${withA(C.lime, 0.5)}`,
              opacity: 1 - burst,
              transform: `scale(${0.98 + 0.14 * burst})`,
              transformOrigin: '50% 50%',
            }}
          />
        )}

        {/* ============ 结论条 ============ */}
        <div
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 738,
            display: 'flex',
            justifyContent: 'center',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 28,
              height: 112,
              padding: '0 52px',
              borderRadius: 20,
              background: `linear-gradient(155deg, ${withA(
                C.lime,
                0.12,
              )}, rgba(255,255,255,0.028))`,
              border: `1px solid ${withA(C.lime, 0.55)}`,
              boxShadow: `0 0 ${54 + 20 * barGlow}px ${withA(
                C.lime,
                0.14 + 0.08 * barGlow,
              )}, inset 0 1px 0 rgba(255,255,255,0.12), 0 24px 60px rgba(0,0,0,0.5)`,
              backdropFilter: 'blur(14px)',
              opacity: barA,
              transform: `translateY(${(1 - barS) * 44}px) scale(${
                0.95 + 0.05 * barS
              })`,
            }}
          >
            <div
              style={{
                width: 10,
                height: 10,
                borderRadius: 99,
                background: C.lime,
                boxShadow: `0 0 ${18 * barGlow}px ${C.lime}`,
              }}
            />
            <div
              style={{
                fontFamily: F.mono,
                fontSize: 44,
                fontWeight: 800,
                color: C.lime,
                whiteSpace: 'nowrap',
                textShadow: `0 0 26px ${withA(C.lime, 0.5)}`,
              }}
            >
              provider = rule
            </div>
            <div
              style={{
                width: 1,
                height: 46,
                background: 'rgba(255,255,255,0.16)',
              }}
            />
            <div
              style={{
                fontFamily: F.cn,
                fontSize: 40,
                fontWeight: 700,
                letterSpacing: 1,
                color: C.ink,
                whiteSpace: 'nowrap',
              }}
            >
              用户永远看不到报错
            </div>
          </div>
        </div>

        {/* ============ 底部字幕（两段） ============ */}
        <Caption
          text="本地模型跑不动？自动切云端。"
          from={20}
          to={206}
          accent={C.cyan}
        />
        <Caption
          text="云端也挂了？12 条规则兜底，回复永远不空。"
          from={214}
          to={400}
          accent={C.lime}
        />
      </Backdrop>
    </AbsoluteFill>
  );
};
