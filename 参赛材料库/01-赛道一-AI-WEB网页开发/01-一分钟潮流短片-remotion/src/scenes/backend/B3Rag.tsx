import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption, LightSweep } from '../../components/Chrome';
import { GlassCard, Rule } from '../../components/ui';
import {
  ChatBubble,
  EventStream,
  ResultBar,
  SceneIndex,
  SectionTitle,
} from '../../components/ai';
import type { StreamEvent } from '../../components/ai';
import { C, EASE, F, enter } from '../../theme';

/**
 * B3｜护理知识 RAG｜场景内 0–420 帧（成片 22–36s）
 *
 * 版式：左列「提问 + SSE 事件流 + token 推流」，右列「知识卡片（带出处）」，
 * 下方三个技术点横排，底部同一槽位先出知识库规模、再被「无命中」结论条顶替。
 *
 * 动画只用 interpolate / enter（内部 Easing），逐帧可渲染，无任何 CSS 动画。
 */

const M = 140;
const LEFT_W = 780;
const RIGHT_X = 960;
const RIGHT_W = 820;
const TOP = 316;
const BAR_TOP = 840;
const BAR_W = 1640;
const TECH_TOP = 684;

const QUERY = '老人跌倒后的黄金处置时间是多少？';

const EVENTS: StreamEvent[] = [
  { tag: 'retrieval', text: '检索 49 块 · 命中 3 块', color: C.cyan },
  { tag: 'token', text: '14 tokens 逐条推出', color: C.violet },
  { tag: 'done', text: '流式结束 · 每条带出处', color: C.lime },
];

const TECHS = [
  { n: '01', name: '中文 n-gram TF-IDF', sub: '离线可跑、零额度', accent: C.cyan },
  { n: '02', name: '标题词加权 ×3', sub: '', accent: C.violet },
  { n: '03', name: '同义词扩展', sub: '跌倒 = 摔倒 = 摔了', accent: C.lime },
];

/** token 宽度序列：只表达「14 条逐 token 推出」，不承载语义 */
const TOKEN_W = [28, 22, 34, 26, 30, 20, 36, 24, 30, 26, 32, 22, 28, 26];

/* ============ 数字滚动 ============ */
const Count: React.FC<{
  to: number;
  from: number;
  dur?: number;
  fmt?: (n: number) => string;
}> = ({ to, from, dur = 26, fmt }) => {
  const frame = useCurrentFrame();
  const p = interpolate(frame, [from, from + dur], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  const v = Math.round(to * p);
  return <>{fmt ? fmt(v) : v.toLocaleString('en-US')}</>;
};

/* ============ token 推流条 ============ */
const TokenStrip: React.FC<{ from: number }> = ({ from }) => {
  const frame = useCurrentFrame();
  const label = enter(frame, from, 12);
  const n = Math.round(
    interpolate(frame, [from + 6, from + 48], [0, 14], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    }),
  );

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        marginTop: 24,
        opacity: label,
        transform: `translateY(${(1 - label) * 12}px)`,
      }}
    >
      <span
        style={{
          width: 132,
          flexShrink: 0,
          fontFamily: F.mono,
          fontSize: 17,
          fontWeight: 600,
          letterSpacing: 1,
          color: C.dimmer,
          fontVariantNumeric: 'tabular-nums',
          whiteSpace: 'nowrap',
        }}
      >
        {n}/14 TOKENS
      </span>
      {TOKEN_W.map((w, i) => {
        const p = enter(frame, from + 6 + i * 3, 8);
        return (
          <div
            key={i}
            style={{
              width: w,
              height: 22,
              borderRadius: 5,
              border: `1px solid ${C.violet}77`,
              background: `linear-gradient(180deg, ${C.violet}BB, ${C.violet}22)`,
              opacity: 0.2 + 0.8 * p,
              transform: `scaleY(${0.24 + 0.76 * p})`,
            }}
          />
        );
      })}
    </div>
  );
};

/* ============ 右侧知识卡片 ============ */
const KnowledgeCard: React.FC = () => {
  const frame = useCurrentFrame();
  const p = enter(frame, 100, 20);
  const sec = enter(frame, 124, 16);
  const q = enter(frame, 148, 18);

  return (
    <div
      style={{
        position: 'absolute',
        left: RIGHT_X,
        top: TOP,
        width: RIGHT_W,
        opacity: p,
        transform: `translateY(${(1 - p) * 30}px)`,
      }}
    >
      <GlassCard accent={C.cyan} padding={36} radius={22}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span
            style={{
              fontFamily: F.mono,
              fontSize: 17,
              fontWeight: 600,
              letterSpacing: 3,
              color: C.dimmer,
            }}
          >
            KNOWLEDGE BASE
          </span>
          <span
            style={{
              padding: '6px 14px',
              borderRadius: 8,
              border: `1px solid ${C.lime}55`,
              background: `${C.lime}14`,
              fontFamily: F.cn,
              fontSize: 20,
              fontWeight: 700,
              color: C.lime,
              whiteSpace: 'nowrap',
            }}
          >
            命中 3 块
          </span>
        </div>

        <div
          style={{
            marginTop: 20,
            fontFamily: F.cn,
            fontSize: 34,
            fontWeight: 800,
            letterSpacing: 0.5,
            color: C.ink,
            whiteSpace: 'nowrap',
          }}
        >
          《养老护理处置规范》
        </div>

        <div
          style={{
            marginTop: 8,
            fontFamily: F.cn,
            fontSize: 44,
            fontWeight: 900,
            letterSpacing: 1,
            color: C.cyan,
            textShadow: `0 0 42px ${C.cyan}66`,
            opacity: sec,
            transform: `translateY(${(1 - sec) * 16}px)`,
            whiteSpace: 'nowrap',
          }}
        >
          第 3.2 节
        </div>

        <Rule from={136} width={RIGHT_W - 72} color={C.cyan} style={{ marginTop: 20 }} />

        <div
          style={{
            marginTop: 20,
            display: 'flex',
            gap: 16,
            opacity: q,
            transform: `translateY(${(1 - q) * 14}px)`,
          }}
        >
          <div style={{ width: 3, background: C.cyan, boxShadow: `0 0 14px ${C.cyan}` }} />
          <div
            style={{
              fontFamily: F.cn,
              fontSize: 26,
              lineHeight: 1.6,
              color: 'rgba(255,255,255,0.82)',
              whiteSpace: 'nowrap',
            }}
          >
            跌倒后最初数分钟是判断伤情的关键窗口…
          </div>
        </div>
      </GlassCard>
    </div>
  );
};

/* ============ 知识库规模条 ============ */
const SCALE = [
  { v: 9353, unit: '字', color: C.lime },
  { v: 277, unit: '行', color: C.ink },
  { v: 49, unit: '块', color: C.cyan },
];

const ScaleStrip: React.FC<{ from: number; out: number }> = ({ from, out }) => {
  const frame = useCurrentFrame();
  const p = enter(frame, from, 16) * (1 - enter(frame, out, 8));
  const y = (1 - enter(frame, from, 16)) * 16;

  return (
    <div
      style={{
        position: 'absolute',
        left: M,
        top: BAR_TOP,
        width: BAR_W,
        height: 72,
        display: 'flex',
        alignItems: 'center',
        gap: 40,
        padding: '0 30px',
        borderRadius: 14,
        border: `1px solid ${C.line}`,
        background: 'rgba(255,255,255,0.045)',
        opacity: p,
        transform: `translateY(${y}px)`,
      }}
    >
      <span
        style={{
          fontFamily: F.mono,
          fontSize: 17,
          fontWeight: 600,
          letterSpacing: 3,
          color: C.dimmer,
          whiteSpace: 'nowrap',
        }}
      >
        知识库规模
      </span>

      {SCALE.map((s, i) => (
        <React.Fragment key={s.unit}>
          <div style={{ display: 'flex', alignItems: 'baseline', gap: 8 }}>
            <span
              style={{
                fontFamily: F.mono,
                fontSize: 40,
                fontWeight: 900,
                letterSpacing: -1,
                color: s.color,
                fontVariantNumeric: 'tabular-nums',
                textShadow: `0 0 32px ${s.color}44`,
              }}
            >
              <Count to={s.v} from={from + 4 + i * 4} />
            </span>
            <span style={{ fontFamily: F.cn, fontSize: 20, color: C.dim }}>{s.unit}</span>
          </div>
          <div style={{ width: 1, height: 30, background: C.line }} />
        </React.Fragment>
      ))}

      <span
        style={{
          marginLeft: 'auto',
          fontFamily: F.cn,
          fontSize: 20,
          color: C.dim,
          whiteSpace: 'nowrap',
        }}
      >
        单块 ≤500 字
      </span>
    </div>
  );
};

/* ============ 场景 ============ */
export const B3Rag: React.FC = () => {
  const frame = useCurrentFrame();
  // 结论条出现时压暗其他内容，把注意力交给「无命中」
  const dim = enter(frame, 350, 16);
  const soft = 1 - dim * 0.45;

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <Backdrop accent={C.cyan} second={C.violet} intensity={0.95} gridOpacity={0.045}>
        <SceneIndex n="03" from={0} accent={C.cyan} />

        <SectionTitle
          kicker="RAG KNOWLEDGE"
          title="护理知识 RAG"
          sub="答案必须带出处，没命中就说没命中"
          from={0}
          accent={C.cyan}
        />

        {/* 左列：提问 + 事件流 + token 推流 */}
        <div
          style={{
            position: 'absolute',
            left: M,
            top: TOP,
            width: LEFT_W,
            opacity: soft,
          }}
        >
          <div
            style={{
              fontFamily: F.mono,
              fontSize: 17,
              fontWeight: 600,
              letterSpacing: 3,
              color: C.dimmer,
              opacity: enter(frame, 26, 12),
            }}
          >
            守护员提问
          </div>
          <div style={{ marginTop: 12 }}>
            <ChatBubble
              role="user"
              text={QUERY}
              from={30}
              accent={C.cyan}
              maxWidth={720}
              fontSize={28}
            />
          </div>
          <div style={{ marginTop: 34 }}>
            <EventStream events={EVENTS} from={70} interval={20} width={LEFT_W} fontSize={21} />
          </div>
          <TokenStrip from={100} />
        </div>

        <KnowledgeCard />

        {/* 三个技术点 */}
        <div
          style={{
            position: 'absolute',
            left: M,
            top: TECH_TOP,
            width: BAR_W,
            display: 'flex',
            gap: 40,
            opacity: soft,
          }}
        >
          {TECHS.map((t, i) => {
            const p = enter(frame, 200 + i * 8, 18);
            return (
              <div
                key={t.n}
                style={{
                  width: 520,
                  height: 140,
                  padding: '18px 26px',
                  borderRadius: 18,
                  border: `1px solid ${t.accent}33`,
                  background: `linear-gradient(150deg, ${t.accent}12, rgba(255,255,255,0.02))`,
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'center',
                  opacity: p,
                  transform: `translateY(${(1 - p) * 26}px)`,
                }}
              >
                <div
                  style={{
                    fontFamily: F.mono,
                    fontSize: 16,
                    fontWeight: 700,
                    letterSpacing: 3,
                    color: t.accent,
                  }}
                >
                  {t.n}
                </div>
                <div
                  style={{
                    marginTop: 12,
                    fontFamily: F.cn,
                    fontSize: 30,
                    fontWeight: 800,
                    color: C.ink,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {t.name}
                </div>
                {t.sub ? (
                  <div
                    style={{
                      marginTop: 10,
                      fontFamily: F.cn,
                      fontSize: 21,
                      color: C.dim,
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {t.sub}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>

        {/* 底部同一槽位：规模条 → 结论条 */}
        <ScaleStrip from={300} out={348} />

        <div
          style={{
            position: 'absolute',
            left: M,
            top: BAR_TOP + 10,
            width: 1360,
            transform: `scale(${1.19 + 0.01 * enter(frame, 356, 18)})`,
            transformOrigin: 'left center',
          }}
        >
          <ResultBar
            label="无命中"
            value="知识库未覆盖该问题"
            from={356}
            accent={C.amber}
            width={1360}
          />
        </div>

        <LightSweep from={352} dur={26} color={`${C.amber}55`} />

        <Caption text="不依赖向量服务，断网也能检索" from={20} to={412} accent={C.cyan} />
      </Backdrop>
    </AbsoluteFill>
  );
};
