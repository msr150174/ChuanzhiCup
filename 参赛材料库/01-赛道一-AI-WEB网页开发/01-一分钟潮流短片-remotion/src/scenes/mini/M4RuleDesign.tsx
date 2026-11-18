import React from 'react';
import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import { C, F, enter } from '../../theme';
import { Backdrop, Caption } from '../../components/Chrome';
import { GlassCard, Tag } from '../../components/ui';
import { SceneIndex, SectionTitle } from '../../components/ai';

/**
 * M4｜12 条规则库 + 适老化｜场景内 0–360 帧（成片 34–46s）
 *
 * ① 0–150f   12 个规则标签 4×3 网格依次点亮
 * ② 150–260f 设计细节：`"健康/身体"` 不含 `"怎么样"`，所以 `"我今天睡眠怎么样"` 命中睡眠规则
 * ③ 260–330f 适老化三标签：大字号 / 长按 3 秒 SOS / 一句话提问
 *
 * 动画只用 interpolate / spring，无任何 CSS animation。
 */

/* ============ 版式常量（1920×1080，左右安全边距 140） ============ */
const M = 140;
const TILE_W = 390;
const TILE_H = 112;
const GAP_X = 26;
const GAP_Y = 28;
const GRID_TOP = 372;

const tileX = (i: number) => M + (i % 4) * (TILE_W + GAP_X); // 140 / 556 / 972 / 1388
const tileY = (i: number) => GRID_TOP + Math.floor(i / 4) * (TILE_H + GAP_Y);

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

/* ============ 12 条规则（顺序与分镜脚本完全一致） ============ */
const RULES: { text: string; color?: string }[] = [
  { text: '你好' },
  { text: '健康/身体' },
  { text: '步数/运动' },
  { text: '睡眠/失眠', color: C.violet },
  { text: '服药提醒', color: C.amber },
  { text: '心情/情绪' },
  { text: '饮食' },
  { text: '位置' },
  { text: '紧急/SOS', color: C.lime },
  { text: '家人/绑定' },
  { text: '谢谢' },
  { text: '再见/晚安' },
];

/* ============ 适老化三件事 ============ */
const ACCESS: { n: string; text: string; color: string }[] = [
  { n: '01', text: '大字号', color: C.cyan },
  { n: '02', text: '长按 3 秒 SOS', color: C.lime },
  { n: '03', text: '一句话提问', color: C.violet },
];

/* ============ 主场景 ============ */
export const M4RuleDesign: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  /* --- 整段收尾 --- */
  const outro = 1 - ramp(frame, 342, 358);

  /* --- 阶段 1：标题 + 网格 --- */
  const gridIn = ramp(frame, 6, 30);
  const gridOut = 1 - ramp(frame, 146, 158);

  /* --- 阶段 2：设计细节 --- */
  const detailIn = enter(frame, 152, 18);
  const detailOut = 1 - ramp(frame, 254, 266);

  /* --- 阶段 3：适老化 --- */
  const accIn = enter(frame, 262, 18);

  /* --- 标题在进入适老化前淡出 --- */
  const titleOut = 1 - ramp(frame, 246, 260);

  return (
    <AbsoluteFill style={{ opacity: outro }}>
      <Backdrop accent={C.cyan} second={C.violet}>
        {/* ============ 章节标题 ============ */}
        <div style={{ opacity: titleOut }}>
          <SectionTitle
            kicker="RULE ENGINE"
            title="12 条规则库"
            sub="第三层兜底，也是最后一层体面"
            from={0}
            accent={C.cyan}
          />
        </div>
        <div style={{ opacity: gridOut }}>
          <SceneIndex n="04" from={10} accent={C.cyan} />
        </div>

        {/* ============ 阶段 1：12 条规则网格 4×3 ============ */}
        <div style={{ position: 'absolute', inset: 0, opacity: gridIn * gridOut }}>
          {RULES.map((r, i) => {
            const color = r.color ?? C.cyan;
            const at = 30 + i * 9;
            const lit = enter(frame, at, 14);
            const pop = spring({
              frame: Math.max(0, frame - at),
              fps,
              config: { damping: 13, stiffness: 220, mass: 0.6 },
            });

            return (
              <div
                key={r.text}
                style={{
                  position: 'absolute',
                  left: tileX(i),
                  top: tileY(i),
                  width: TILE_W,
                  height: TILE_H,
                  transform: `translateY(${(1 - lit) * 8}px) scale(${
                    0.97 + 0.03 * Math.min(1, pop)
                  })`,
                }}
              >
                <GlassCard
                  accent={color}
                  padding={0}
                  radius={16}
                  style={{
                    width: '100%',
                    height: '100%',
                    border: `1px solid ${withA(color, 0.09 + 0.44 * lit)}`,
                    background: `linear-gradient(155deg, ${withA(
                      color,
                      0.012 + 0.1 * lit,
                    )}, rgba(255,255,255,0.012))`,
                    boxShadow: `inset 0 1px 0 rgba(255,255,255,${
                      0.05 + 0.06 * lit
                    }), 0 0 ${34 * lit}px ${withA(color, 0.3 * lit)}`,
                  }}
                >
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      height: '100%',
                      padding: '0 26px',
                      gap: 16,
                    }}
                  >
                    <span
                      style={{
                        fontFamily: F.mono,
                        fontSize: 17,
                        fontWeight: 700,
                        letterSpacing: 1,
                        minWidth: 26,
                        color: withA(color, 0.32 + 0.68 * lit),
                      }}
                    >
                      {String(i + 1).padStart(2, '0')}
                    </span>
                    <span
                      style={{
                        fontFamily: F.cn,
                        fontSize: 32,
                        fontWeight: 800,
                        letterSpacing: -0.5,
                        whiteSpace: 'nowrap',
                        color: `rgba(255,255,255,${0.3 + 0.7 * lit})`,
                        textShadow:
                          lit > 0.2
                            ? `0 0 ${16 * lit}px ${withA(color, 0.5 * lit)}`
                            : 'none',
                      }}
                    >
                      {r.text}
                    </span>
                    <div
                      style={{
                        marginLeft: 'auto',
                        width: 7,
                        height: 7,
                        borderRadius: 99,
                        background: color,
                        opacity: lit,
                        boxShadow: `0 0 ${14 * lit}px ${color}`,
                      }}
                    />
                  </div>

                  {/* 底部点亮点线 */}
                  <div
                    style={{
                      position: 'absolute',
                      left: 26,
                      right: 26,
                      bottom: 15,
                      height: 2,
                      background: withA(color, 0.09),
                    }}
                  >
                    <div
                      style={{
                        width: `${lit * 100}%`,
                        height: '100%',
                        background: color,
                        opacity: 0.9,
                        boxShadow: `0 0 12px ${color}`,
                      }}
                    />
                  </div>
                </GlassCard>
              </div>
            );
          })}
        </div>

        {/* ============ 阶段 2：设计细节对比 ============ */}
        <div
          style={{
            position: 'absolute',
            inset: 0,
            opacity: detailIn * detailOut,
            transform: `translateY(${(1 - detailIn) * 18}px)`,
          }}
        >
          {/* 左：规则关键词刻意收窄 */}
          <div style={{ position: 'absolute', left: M, top: 356, width: 760, height: 340 }}>
            <GlassCard
              accent={C.cyan}
              padding={34}
              radius={22}
              style={{ width: '100%', height: '100%' }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                <div
                  style={{
                    fontFamily: F.mono,
                    fontSize: 17,
                    fontWeight: 700,
                    letterSpacing: 5,
                    color: C.cyan,
                  }}
                >
                  KEYWORDS
                </div>
                <div
                  style={{
                    marginTop: 18,
                    fontFamily: F.cn,
                    fontSize: 44,
                    fontWeight: 900,
                    letterSpacing: -1,
                    color: C.ink,
                    whiteSpace: 'nowrap',
                  }}
                >
                  &quot;健康/身体&quot;
                </div>

                <div
                  style={{
                    marginTop: 26,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 14,
                  }}
                >
                  <span
                    style={{
                      fontFamily: F.cn,
                      fontSize: 22,
                      fontWeight: 500,
                      color: C.dim,
                    }}
                  >
                    包含
                  </span>
                  <Tag text="健康" color={C.cyan} from={166} />
                  <Tag text="身体" color={C.cyan} from={172} />
                </div>

                <div
                  style={{
                    marginTop: 'auto',
                    paddingTop: 22,
                    borderTop: '1px solid rgba(255,255,255,0.10)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 14,
                  }}
                >
                  <span
                    style={{
                      fontFamily: F.cn,
                      fontSize: 22,
                      fontWeight: 700,
                      color: C.warn,
                    }}
                  >
                    不含
                  </span>
                  <span
                    style={{
                      fontFamily: F.cn,
                      fontSize: 34,
                      fontWeight: 800,
                      color: withA(C.warn, 0.9),
                      textDecoration: 'line-through',
                      textDecorationThickness: 3,
                      textDecorationColor: C.warn,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    &quot;怎么样&quot;
                  </span>
                </div>
              </div>
            </GlassCard>
          </div>

          {/* 中线 */}
          <div
            style={{
              position: 'absolute',
              left: 958,
              top: 396,
              width: 1,
              height: 260,
              background: `linear-gradient(180deg, transparent, ${withA(
                C.cyan,
                0.55,
              )}, ${withA(C.lime, 0.55)}, transparent)`,
            }}
          />

          {/* 右：真实提问必须命中睡眠规则 */}
          <div style={{ position: 'absolute', left: 1020, top: 356, width: 760, height: 340 }}>
            <GlassCard
              accent={C.lime}
              padding={34}
              radius={22}
              style={{ width: '100%', height: '100%' }}
            >
              <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
                <div
                  style={{
                    fontFamily: F.mono,
                    fontSize: 17,
                    fontWeight: 700,
                    letterSpacing: 5,
                    color: C.lime,
                  }}
                >
                  QUERY
                </div>
                <div
                  style={{
                    marginTop: 18,
                    fontFamily: F.cn,
                    fontSize: 38,
                    fontWeight: 900,
                    letterSpacing: -1,
                    color: C.ink,
                    whiteSpace: 'nowrap',
                  }}
                >
                  &quot;我今天
                  <span
                    style={{
                      color: C.lime,
                      borderBottom: `3px solid ${C.lime}`,
                      paddingBottom: 2,
                      boxShadow: `inset 0 -14px 0 ${withA(C.lime, 0.16)}`,
                    }}
                  >
                    睡眠
                  </span>
                  怎么样&quot;
                </div>

                <div
                  style={{
                    marginTop: 26,
                    display: 'flex',
                    alignItems: 'center',
                    gap: 14,
                  }}
                >
                  <span
                    style={{
                      fontFamily: F.cn,
                      fontSize: 22,
                      fontWeight: 500,
                      color: C.dim,
                    }}
                  >
                    必须命中
                  </span>
                  <Tag text="睡眠" color={C.lime} from={184} />
                </div>

                <div
                  style={{
                    marginTop: 'auto',
                    paddingTop: 22,
                    borderTop: '1px solid rgba(255,255,255,0.10)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 12,
                    fontFamily: F.mono,
                    fontSize: 18,
                    letterSpacing: 2,
                    color: withA(C.lime, 0.7),
                  }}
                >
                  <div
                    style={{
                      width: 26,
                      height: 2,
                      background: C.lime,
                      boxShadow: `0 0 12px ${C.lime}`,
                    }}
                  />
                  rule = sleep
                </div>
              </div>
            </GlassCard>
          </div>

          {/* 设计取舍结论 */}
          <div
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: 726,
              display: 'flex',
              justifyContent: 'center',
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 22,
                padding: '18px 40px',
                borderRadius: 16,
                border: `1px solid ${withA(C.cyan, 0.42)}`,
                background: `linear-gradient(155deg, ${withA(
                  C.cyan,
                  0.1,
                )}, rgba(255,255,255,0.022))`,
                boxShadow: `0 0 60px ${withA(C.cyan, 0.12)}, inset 0 1px 0 rgba(255,255,255,0.1)`,
                opacity: enter(frame, 196, 16),
                transform: `translateY(${(1 - enter(frame, 196, 16)) * 18}px)`,
              }}
            >
              <span
                style={{
                  fontFamily: F.mono,
                  fontSize: 18,
                  fontWeight: 700,
                  letterSpacing: 3,
                  color: C.cyan,
                  whiteSpace: 'nowrap',
                }}
              >
                DESIGN
              </span>
              <div style={{ width: 1, height: 32, background: 'rgba(255,255,255,0.16)' }} />
              <span
                style={{
                  fontFamily: F.cn,
                  fontSize: 30,
                  fontWeight: 700,
                  letterSpacing: 1,
                  color: C.ink,
                  whiteSpace: 'nowrap',
                }}
              >
                泛词会把问题抢走，所以关键词刻意收窄
              </span>
            </div>
          </div>
        </div>

        {/* ============ 阶段 3：适老化三标签 ============ */}
        <div style={{ position: 'absolute', inset: 0, opacity: accIn }}>
          <div
            style={{
              position: 'absolute',
              left: M,
              top: 316,
              display: 'flex',
              alignItems: 'center',
              gap: 14,
              opacity: accIn,
              transform: `translateY(${(1 - accIn) * 14}px)`,
            }}
          >
            <div
              style={{
                width: 26,
                height: 2,
                background: C.lime,
                boxShadow: `0 0 14px ${C.lime}`,
              }}
            />
            <span
              style={{
                fontFamily: F.cn,
                fontSize: 26,
                fontWeight: 700,
                letterSpacing: 8,
                color: C.lime,
              }}
            >
              适老化
            </span>
          </div>

          {ACCESS.map((a, i) => {
            const at = 268 + i * 8;
            const p = enter(frame, at, 18);
            const lineW = enter(frame, at + 10, 26);
            return (
              <div
                key={a.n}
                style={{
                  position: 'absolute',
                  left: M + i * (520 + 40),
                  top: 380,
                  width: 520,
                  height: 320,
                  opacity: p,
                  transform: `translateY(${(1 - p) * 30}px)`,
                }}
              >
                <GlassCard
                  accent={a.color}
                  padding={38}
                  radius={24}
                  style={{
                    width: '100%',
                    height: '100%',
                    border: `1px solid ${withA(a.color, 0.24)}`,
                    background: `linear-gradient(155deg, ${withA(
                      a.color,
                      0.09,
                    )}, rgba(255,255,255,0.014))`,
                    boxShadow: `0 0 70px ${withA(a.color, 0.14)}, inset 0 1px 0 rgba(255,255,255,0.1)`,
                  }}
                >
                  {/* 水印序号，填住卡片右下角 */}
                  <div
                    style={{
                      position: 'absolute',
                      right: 20,
                      top: 18,
                      fontFamily: F.mono,
                      fontSize: 132,
                      fontWeight: 900,
                      lineHeight: 1,
                      letterSpacing: -6,
                      color: C.ink,
                      opacity: 0.04,
                    }}
                  >
                    {a.n}
                  </div>

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
                        fontFamily: F.mono,
                        fontSize: 20,
                        fontWeight: 700,
                        letterSpacing: 3,
                        color: withA(a.color, 0.85),
                      }}
                    >
                      {a.n}
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
                          fontFamily: F.cn,
                          fontSize: 46,
                          fontWeight: 900,
                          letterSpacing: -1,
                          lineHeight: 1.2,
                          color: C.ink,
                          whiteSpace: 'nowrap',
                          textShadow: `0 0 30px ${withA(a.color, 0.35)}`,
                        }}
                      >
                        {a.text}
                      </div>
                      <div
                        style={{
                          marginTop: 22,
                          height: 3,
                          width: 150 * lineW,
                          background: a.color,
                          boxShadow: `0 0 18px ${a.color}`,
                        }}
                      />
                    </div>
                  </div>
                </GlassCard>
              </div>
            );
          })}
        </div>

        {/* ============ 底部字幕 ============ */}
        <Caption text="规则收窄，是为了答对" from={10} to={350} accent={C.lime} />
      </Backdrop>
    </AbsoluteFill>
  );
};
