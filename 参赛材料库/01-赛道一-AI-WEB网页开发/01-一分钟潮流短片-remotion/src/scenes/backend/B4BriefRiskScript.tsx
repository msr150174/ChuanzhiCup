import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, LightSweep } from '../../components/Chrome';
import { GlassCard, Kicker, Rule } from '../../components/ui';
import { SceneIndex } from '../../components/ai';
import { C, EASE, F, enter } from '../../theme';

/**
 * B4｜简报 / 风险画像 / 处置话术｜场景内 0–420 帧（成片 36–50s）
 *
 * 三块并排放在一条 5760px 长的轨道上，每块占 140 帧：
 * 轨道横向滑动切换（真位移，不是叠化），块内元素各自入场。
 *   块 1 0–140   每日值守简报
 *   块 2 140–280 AI 风险画像
 *   块 3 280–420 AI 处置话术
 */

const M = 140;
const BLOCK = 1920;
const BLOCK_FRAMES = 140;
const CONTENT_W = 1640;

const slide = (f: number, a: number, b: number, from: number, to: number) =>
  interpolate(f, [a, b], [from, to], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.inOut,
  });

/** 轨道位移：124–152 切到第 2 块，264–292 切到第 3 块 */
const trackX = (f: number) => {
  if (f <= 124) return 0;
  if (f <= 152) return slide(f, 124, 152, 0, -BLOCK);
  if (f <= 264) return -BLOCK;
  if (f <= 292) return slide(f, 264, 292, -BLOCK, -BLOCK * 2);
  return -BLOCK * 2;
};

/* ============ 块头（Kicker + 大标题 + 细线） ============ */
const Head: React.FC<{ kicker: string; title: string; base: number; accent: string }> = ({
  kicker,
  title,
  base,
  accent,
}) => {
  const frame = useCurrentFrame();
  const t = enter(frame, base + 8, 20);
  return (
    <div style={{ position: 'absolute', left: M, top: 232 }}>
      <Kicker text={kicker} from={base + 2} color={accent} />
      <div
        style={{
          marginTop: 16,
          fontFamily: F.cn,
          fontSize: 72,
          fontWeight: 900,
          letterSpacing: -1,
          lineHeight: 1.12,
          color: C.ink,
          opacity: t,
          transform: `translateY(${(1 - t) * 26}px)`,
          whiteSpace: 'nowrap',
        }}
      >
        {title}
      </div>
      <Rule from={base + 18} width={300} color={accent} style={{ marginTop: 18 }} />
    </div>
  );
};

/* ============ 块 1｜每日值守简报 ============ */
const FIELDS = [
  { k: 'date', v: '2026-11-16', color: C.ink },
  { k: 'checkinAbnormal', v: '22', color: C.lime },
  { k: 'medRate', v: 'N/A', color: C.amber },
];

const ADVICE = [
  'checkinAbnormal 22 位老人优先回访',
  'medRate N/A · 无用药记录不写 100%',
  'date 2026-11-16 · 13:04:22 生成',
];

const BlockBriefing: React.FC<{ base: number }> = ({ base }) => {
  const frame = useCurrentFrame();
  const fields = enter(frame, base + 14, 18);
  const badge1 = enter(frame, base + 58, 12) * (1 - enter(frame, base + 78, 10));
  const badge2 = enter(frame, base + 78, 12);
  const note = enter(frame, base + 92, 14);

  return (
    <>
      <Head kicker="BRIEFING" title="每日值守简报" base={base} accent={C.cyan} />

      {/* 字段条 */}
      <div
        style={{
          position: 'absolute',
          left: M,
          top: 420,
          width: CONTENT_W,
          opacity: fields,
          transform: `translateY(${(1 - fields) * 24}px)`,
        }}
      >
        <GlassCard accent={C.cyan} padding={0} radius={18}>
          <div style={{ display: 'flex', alignItems: 'center', height: 132, padding: '0 42px', gap: 44 }}>
            {FIELDS.map((f, i) => (
              <React.Fragment key={f.k}>
                <div>
                  <div
                    style={{
                      fontFamily: F.mono,
                      fontSize: 17,
                      fontWeight: 600,
                      letterSpacing: 2,
                      color: C.dimmer,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {f.k}
                  </div>
                  <div
                    style={{
                      marginTop: 10,
                      fontFamily: F.mono,
                      fontSize: 42,
                      fontWeight: 900,
                      letterSpacing: -1,
                      color: f.color,
                      fontVariantNumeric: 'tabular-nums',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {f.v}
                  </div>
                </div>
                {i < FIELDS.length - 1 ? (
                  <div style={{ width: 1, height: 62, background: C.line }} />
                ) : null}
              </React.Fragment>
            ))}
            <div
              style={{
                marginLeft: 'auto',
                fontFamily: F.cn,
                fontSize: 20,
                color: C.dimmer,
                whiteSpace: 'nowrap',
              }}
            >
              当日值守数据
            </div>
          </div>
        </GlassCard>
      </div>

      {/* 建议列表 */}
      <div style={{ position: 'absolute', left: M, top: 578, width: CONTENT_W }}>
        {ADVICE.map((text, i) => {
          const p = enter(frame, base + 34 + i * 9, 16);
          return (
            <div
              key={i}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 22,
                height: 58,
                opacity: p,
                transform: `translateX(${(1 - p) * -26}px)`,
              }}
            >
              <span
                style={{
                  width: 46,
                  fontFamily: F.mono,
                  fontSize: 24,
                  fontWeight: 900,
                  color: C.cyan,
                }}
              >
                {i + 1}.
              </span>
              <span
                style={{
                  fontFamily: F.cn,
                  fontSize: 30,
                  fontWeight: 500,
                  color: 'rgba(255,255,255,0.86)',
                  whiteSpace: 'nowrap',
                }}
              >
                {text}
              </span>
            </div>
          );
        })}
      </div>

      {/* 生成来源徽标：真实 coze 生成 → 之后命中 cache */}
      <div style={{ position: 'absolute', left: M, top: 790, width: 760, height: 56 }}>
        <div
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '12px 22px',
            borderRadius: 10,
            border: `1px solid ${C.lime}55`,
            background: `${C.lime}12`,
            opacity: badge1,
            transform: `translateY(${(1 - badge1) * -18}px)`,
            whiteSpace: 'nowrap',
          }}
        >
          <div
            style={{ width: 8, height: 8, borderRadius: 99, background: C.lime, boxShadow: `0 0 12px ${C.lime}` }}
          />
          <span style={{ fontFamily: F.mono, fontSize: 22, fontWeight: 700, color: C.lime }}>
            13:04:22 真实 coze 生成
          </span>
        </div>

        <div
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            display: 'flex',
            alignItems: 'center',
            gap: 12,
            padding: '12px 22px',
            borderRadius: 10,
            border: `1px solid ${C.cyan}55`,
            background: `${C.cyan}12`,
            opacity: badge2,
            transform: `translateY(${(1 - badge2) * 18}px)`,
            whiteSpace: 'nowrap',
          }}
        >
          <div
            style={{ width: 8, height: 8, borderRadius: 99, background: C.cyan, boxShadow: `0 0 12px ${C.cyan}` }}
          />
          <span style={{ fontFamily: F.mono, fontSize: 22, fontWeight: 700, color: C.cyan }}>
            之后命中 cache
          </span>
        </div>
      </div>

      <div
        style={{
          position: 'absolute',
          left: M,
          top: 876,
          fontFamily: F.cn,
          fontSize: 21,
          color: C.dim,
          opacity: note,
          whiteSpace: 'nowrap',
        }}
      >
        无用药记录时 medRate 返回 N/A，不写 100%
      </div>
    </>
  );
};

/* ============ 块 2｜AI 风险画像 ============ */
const BlockRisk: React.FC<{ base: number }> = ({ base }) => {
  const frame = useCurrentFrame();
  const card = enter(frame, base + 12, 20);
  const idP = enter(frame, base + 20, 16);
  const s1 = enter(frame, base + 26, 18);
  const s2 = enter(frame, base + 32, 18);
  const note = enter(frame, base + 60, 14);

  const limits = [
    { q: '?limit=1', r: '返回 1 条', color: C.cyan },
    { q: '?limit=3', r: '正常返回 3 条', color: C.lime },
  ];

  return (
    <>
      <Head kicker="RISK PROFILE" title="AI 风险画像" base={base} accent={C.violet} />

      {/* 大卡 */}
      <div
        style={{
          position: 'absolute',
          left: M,
          top: 396,
          width: 780,
          opacity: card,
          transform: `translateY(${(1 - card) * 30}px)`,
        }}
      >
        <GlassCard accent={C.warn} padding={40} radius={22}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <span
              style={{
                fontFamily: F.mono,
                fontSize: 42,
                fontWeight: 900,
                letterSpacing: 1,
                color: C.ink,
                opacity: idP,
              }}
            >
              用户0519
            </span>
            <span
              style={{
                padding: '8px 18px',
                borderRadius: 8,
                background: `${C.warn}1F`,
                border: `1px solid ${C.warn}66`,
                fontFamily: F.cn,
                fontSize: 24,
                fontWeight: 800,
                color: C.warn,
                whiteSpace: 'nowrap',
                opacity: idP,
              }}
            >
              较高风险
            </span>
          </div>

          <div style={{ height: 1, background: C.line, margin: '26px 0 24px' }} />

          <div style={{ display: 'flex', gap: 70 }}>
            <div style={{ opacity: s1, transform: `translateY(${(1 - s1) * 20}px)` }}>
              <div
                style={{
                  fontFamily: F.mono,
                  fontSize: 18,
                  letterSpacing: 2,
                  color: C.dimmer,
                  whiteSpace: 'nowrap',
                }}
              >
                healthScore
              </div>
              <div
                style={{
                  marginTop: 10,
                  fontFamily: F.mono,
                  fontSize: 80,
                  fontWeight: 900,
                  lineHeight: 1,
                  letterSpacing: -3,
                  color: C.amber,
                  fontVariantNumeric: 'tabular-nums',
                  textShadow: `0 0 40px ${C.amber}44`,
                }}
              >
                35
              </div>
            </div>

            <div style={{ opacity: s2, transform: `translateY(${(1 - s2) * 20}px)` }}>
              <div
                style={{
                  fontFamily: F.mono,
                  fontSize: 18,
                  letterSpacing: 2,
                  color: C.dimmer,
                  whiteSpace: 'nowrap',
                }}
              >
                riskScore
              </div>
              <div
                style={{
                  marginTop: 10,
                  fontFamily: F.mono,
                  fontSize: 80,
                  fontWeight: 900,
                  lineHeight: 1,
                  letterSpacing: -3,
                  color: C.warn,
                  fontVariantNumeric: 'tabular-nums',
                  textShadow: `0 0 40px ${C.warn}44`,
                }}
              >
                65
              </div>
            </div>
          </div>
        </GlassCard>
      </div>

      {/* 右侧接口条 */}
      <div style={{ position: 'absolute', left: 980, top: 396, width: 760, display: 'flex', flexDirection: 'column', gap: 26 }}>
        {limits.map((l, i) => {
          const p = enter(frame, base + 24 + i * 10, 18);
          return (
            <div
              key={l.q}
              style={{
                opacity: p,
                transform: `translateX(${(1 - p) * 34}px)`,
              }}
            >
              <GlassCard accent={l.color} padding={0} radius={16}>
                <div style={{ display: 'flex', alignItems: 'center', height: 148, padding: '0 34px', gap: 24 }}>
                  <span
                    style={{
                      fontFamily: F.mono,
                      fontSize: 28,
                      fontWeight: 800,
                      color: l.color,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {l.q}
                  </span>
                  <div
                    style={{
                      flex: 1,
                      height: 1,
                      background: `linear-gradient(90deg, ${l.color}66, transparent)`,
                    }}
                  />
                  <span
                    style={{
                      fontFamily: F.cn,
                      fontSize: 30,
                      fontWeight: 600,
                      color: 'rgba(255,255,255,0.9)',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {l.r}
                  </span>
                </div>
              </GlassCard>
            </div>
          );
        })}
      </div>

      <div
        style={{
          position: 'absolute',
          left: M,
          top: 876,
          fontFamily: F.cn,
          fontSize: 24,
          color: C.dim,
          opacity: note,
          whiteSpace: 'nowrap',
        }}
      >
        从"事后报警"到"事前干预"
      </div>
    </>
  );
};

/* ============ 块 3｜AI 处置话术 ============ */
const BlockScript: React.FC<{ base: number }> = ({ base }) => {
  const frame = useCurrentFrame();
  const note = enter(frame, base + 76, 14);

  const outputs = [
    { key: 'handleNote', value: '1~7 连续编号', color: C.cyan, at: base + 14 },
    { key: 'callScript', value: '5 条', color: C.violet, at: base + 22 },
    { key: 'familyScript', value: '211 字', color: C.lime, at: base + 30 },
  ];

  const checks = ['无 ---', '括号成对', '无「（危急）」残留'];

  return (
    <>
      <Head kicker="COMMUNICATION" title="AI 处置话术" base={base} accent={C.lime} />

      {outputs.map((o, i) => {
        const p = enter(frame, o.at, 18);
        return (
          <div
            key={o.key}
            style={{
              position: 'absolute',
              left: M + i * 560,
              top: 396,
              width: 520,
              height: 288,
              padding: '30px 32px',
              borderRadius: 20,
              border: `1px solid ${o.color}33`,
              background: `linear-gradient(152deg, ${o.color}14, rgba(255,255,255,0.02))`,
              boxShadow: `inset 0 1px 0 rgba(255,255,255,0.08), 0 20px 50px rgba(0,0,0,0.4)`,
              opacity: p,
              transform: `translateY(${(1 - p) * 34}px)`,
            }}
          >
            <div
              style={{
                fontFamily: F.mono,
                fontSize: 22,
                fontWeight: 700,
                letterSpacing: 1,
                color: o.color,
                whiteSpace: 'nowrap',
              }}
            >
              {o.key}
            </div>
            <div
              style={{
                marginTop: 16,
                fontFamily: F.cn,
                fontSize: 34,
                fontWeight: 800,
                color: C.ink,
                whiteSpace: 'nowrap',
              }}
            >
              {o.value}
            </div>

            {/* 细节可视化 */}
            <div style={{ marginTop: 30 }}>
              {i === 0
                ? // 1~7 连续编号
                  Array.from({ length: 7 }).map((_, k) => {
                    const q = enter(frame, base + 40 + k * 3, 8);
                    return (
                      <span
                        key={k}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          width: 38,
                          height: 38,
                          marginRight: 10,
                          borderRadius: 8,
                          border: `1px solid ${C.cyan}66`,
                          background: `${C.cyan}14`,
                          fontFamily: F.mono,
                          fontSize: 18,
                          fontWeight: 700,
                          color: C.cyan,
                          opacity: 0.15 + 0.85 * q,
                          transform: `scale(${0.7 + 0.3 * q})`,
                        }}
                      >
                        {k + 1}
                      </span>
                    );
                  })
                : i === 1
                ? // 5 条话术
                  Array.from({ length: 5 }).map((_, k) => {
                    const q = enter(frame, base + 44 + k * 4, 10);
                    return (
                      <div
                        key={k}
                        style={{
                          display: 'flex',
                          alignItems: 'center',
                          gap: 12,
                          marginBottom: 10,
                          opacity: q,
                          transform: `translateX(${(1 - q) * -16}px)`,
                        }}
                      >
                        <div style={{ width: 6, height: 6, borderRadius: 99, background: C.violet }} />
                        <div
                          style={{
                            width: 300 - k * 26,
                            height: 7,
                            borderRadius: 4,
                            background: `linear-gradient(90deg, ${C.violet}AA, ${C.violet}11)`,
                          }}
                        />
                      </div>
                    );
                  })
                : // 211 字家属话术
                  Array.from({ length: 3 }).map((_, k) => {
                    const q = enter(frame, base + 46 + k * 5, 10);
                    return (
                      <div
                        key={k}
                        style={{
                          width: ['100%', '92%', '58%'][k],
                          height: 8,
                          borderRadius: 4,
                          marginBottom: 14,
                          background: `linear-gradient(90deg, ${C.lime}AA, ${C.lime}11)`,
                          opacity: q,
                          transform: `scaleX(${q})`,
                          transformOrigin: 'left center',
                        }}
                      />
                    );
                  })}
            </div>
          </div>
        );
      })}

      {/* 质量徽标 */}
      <div style={{ position: 'absolute', left: M, top: 726, display: 'flex', gap: 18 }}>
        {checks.map((t, i) => {
          const p = enter(frame, base + 58 + i * 8, 14);
          return (
            <div
              key={t}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 10,
                padding: '11px 20px',
                borderRadius: 999,
                border: `1px solid ${C.lime}44`,
                background: `${C.lime}0F`,
                opacity: p,
                transform: `translateY(${(1 - p) * 14}px)`,
                whiteSpace: 'nowrap',
              }}
            >
              <span style={{ fontFamily: F.mono, fontSize: 20, fontWeight: 900, color: C.lime }}>✓</span>
              <span style={{ fontFamily: F.cn, fontSize: 22, fontWeight: 600, color: 'rgba(255,255,255,0.88)' }}>
                {t}
              </span>
            </div>
          );
        })}
      </div>

      <div
        style={{
          position: 'absolute',
          left: M,
          top: 876,
          fontFamily: F.cn,
          fontSize: 24,
          color: C.dim,
          opacity: note,
          whiteSpace: 'nowrap',
        }}
      >
        研判结论 → 话术 → 处置记录，守护员零门槛
      </div>
    </>
  );
};

/* ============ 场景 ============ */
export const B4BriefRiskScript: React.FC = () => {
  const frame = useCurrentFrame();

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <Backdrop accent={C.violet} second={C.cyan} intensity={0.9} gridOpacity={0.04}>
        <SceneIndex n="04" from={0} accent={C.violet} />

        {/* 横向轨道 */}
        <div
          style={{
            position: 'absolute',
            left: 0,
            top: 0,
            width: BLOCK * 3,
            height: 1080,
            transform: `translateX(${trackX(frame)}px)`,
          }}
        >
          <div style={{ position: 'absolute', left: 0, top: 0, width: BLOCK, height: 1080 }}>
            <BlockBriefing base={0} />
          </div>
          <div style={{ position: 'absolute', left: BLOCK, top: 0, width: BLOCK, height: 1080 }}>
            <BlockRisk base={BLOCK_FRAMES} />
          </div>
          <div style={{ position: 'absolute', left: BLOCK * 2, top: 0, width: BLOCK, height: 1080 }}>
            <BlockScript base={BLOCK_FRAMES * 2} />
          </div>
        </div>

        {/* 切换时的扫光，强化「滑过去」的物理感 */}
        <LightSweep from={126} dur={24} color="rgba(255,255,255,0.32)" />
        <LightSweep from={266} dur={24} color="rgba(255,255,255,0.32)" />
      </Backdrop>
    </AbsoluteFill>
  );
};
