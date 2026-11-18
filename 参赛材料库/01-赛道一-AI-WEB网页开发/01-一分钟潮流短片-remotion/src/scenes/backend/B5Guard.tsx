import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { Backdrop } from '../../components/Chrome';
import { Kicker, Rule, Tag } from '../../components/ui';
import { SceneIndex } from '../../components/ai';
import { C, EASE, F, enter } from '../../theme';

/**
 * B5｜五道护栏 + 审计 + 收尾｜场景内 0–300 帧（成片 50–60s）
 *
 * 0–108   五道护栏 01–05 逐条砸入，右侧 guardOuter() 出口说明
 * 108–182 ai_call_logs + 六个 log_type 标签
 * 182–232 大字「每一步，都可核验」
 * 232–268 署名
 * 268–300 整体淡出
 */

const M = 140;
const ROW_TOP = 236;
const ROW_H = 104;

const GUARDS = [
  { n: '01', name: '只读', desc: '只读连接，不改一行数据', accent: C.cyan },
  { n: '02', name: '表白名单', desc: '只允许访问白名单内的表', accent: C.cyan },
  { n: '03', name: '禁 SELECT *', desc: '星号查询直接拒绝', accent: C.violet },
  { n: '04', name: '强制 LIMIT 1000', desc: '结果集封顶 1000 行', accent: C.lime },
  { n: '05', name: '5s 超时', desc: '超过 5 秒直接掐断', accent: C.lime },
];

const LOG_TYPES = ['triage', 'query', 'rag', 'briefing', 'profile', 'script'];

const GuardRow: React.FC<{
  i: number;
  n: string;
  name: string;
  desc: string;
  accent: string;
  out: number;
}> = ({ i, n, name, desc, accent, out }) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();
  const start = 3 + i * 7;
  const local = frame - start;
  const sp = local <= 0 ? 0 : spring({ frame: local, fps, config: { damping: 13, stiffness: 220, mass: 0.55 } });

  const a = Math.min(1, sp * 1.6) * (1 - out);
  const scale = 1 + (1 - sp) * 0.34;
  const dx = (1 - sp) * -64;

  return (
    <div
      style={{
        position: 'absolute',
        left: M,
        top: ROW_TOP + i * ROW_H,
        width: 1330,
        height: ROW_H,
        display: 'flex',
        alignItems: 'center',
        gap: 30,
        opacity: a,
        transform: `translate(${dx}px, ${out * -34}px) scale(${scale})`,
        transformOrigin: 'left center',
      }}
    >
      <span
        style={{
          width: 88,
          fontFamily: F.mono,
          fontSize: 38,
          fontWeight: 900,
          letterSpacing: 1,
          color: accent,
          fontVariantNumeric: 'tabular-nums',
          textShadow: `0 0 34px ${accent}55`,
        }}
      >
        {n}
      </span>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        <span
          style={{
            fontFamily: F.cn,
            fontSize: 50,
            fontWeight: 800,
            letterSpacing: 1,
            color: C.ink,
            whiteSpace: 'nowrap',
          }}
        >
          {name}
        </span>
        <span
          style={{
            fontFamily: F.cn,
            fontSize: 21,
            fontWeight: 500,
            letterSpacing: 0.5,
            color: C.dim,
            whiteSpace: 'nowrap',
          }}
        >
          {desc}
        </span>
      </div>
      <div
        style={{
          flex: 1,
          height: 1,
          background: `linear-gradient(90deg, ${accent}77, ${accent}00)`,
          opacity: 0.7,
        }}
      />
    </div>
  );
};

export const B5Guard: React.FC = () => {
  const frame = useCurrentFrame();

  const guardsOut = enter(frame, 108, 18);
  const audit = enter(frame, 116, 20);
  const codeTag = enter(frame, 34, 16);
  const big = enter(frame, 182, 20);
  const sign1 = enter(frame, 232, 18);
  const sign2 = enter(frame, 240, 18);
  const outAll = interpolate(frame, [268, 297], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.inOut,
  });

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <Backdrop accent={C.lime} second={C.cyan} intensity={0.9} gridOpacity={0.045}>
        <SceneIndex n="05" from={0} accent={C.lime} />

        <AbsoluteFill style={{ opacity: outAll }}>
          {/* 护栏区标题 */}
          <div style={{ position: 'absolute', left: M, top: 130, opacity: 1 - guardsOut }}>
            <Kicker text="GUARDRAILS" from={0} color={C.lime} />
            <Rule from={6} width={320} color={C.lime} style={{ marginTop: 16 }} />
          </div>

          {GUARDS.map((g, i) => (
            <GuardRow key={g.n} i={i} n={g.n} name={g.name} desc={g.desc} accent={g.accent} out={guardsOut} />
          ))}

          {/* 右侧：统一出口说明 */}
          <div
            style={{
              position: 'absolute',
              right: M,
              top: 400,
              width: 330,
              opacity: codeTag * (1 - guardsOut),
              transform: `translateY(${(1 - codeTag) * 20}px)`,
            }}
          >
            <div
              style={{
                fontFamily: F.mono,
                fontSize: 15,
                fontWeight: 600,
                letterSpacing: 4,
                color: C.dimmer,
                textAlign: 'right',
              }}
            >
              CODE
            </div>
            <div
              style={{
                marginTop: 12,
                padding: '14px 20px',
                borderRadius: 12,
                border: `1px solid ${C.cyan}55`,
                background: `${C.cyan}0D`,
                boxShadow: `0 0 50px ${C.cyan}22, inset 0 1px 0 rgba(255,255,255,0.08)`,
                fontFamily: F.mono,
                fontSize: 26,
                fontWeight: 700,
                color: C.cyan,
                whiteSpace: 'nowrap',
                textAlign: 'center',
              }}
            >
              guardOuter()
            </div>
            <div
              style={{
                marginTop: 22,
                fontFamily: F.cn,
                fontSize: 22,
                fontWeight: 600,
                lineHeight: 1.6,
                color: C.ink,
                textAlign: 'right',
              }}
            >
              所有 AI 查询
              <br />
              共用一套护栏
            </div>
            <div
              style={{
                marginTop: 10,
                fontFamily: F.cn,
                fontSize: 18,
                fontWeight: 400,
                lineHeight: 1.7,
                color: C.dim,
                textAlign: 'right',
              }}
            >
              模型负责生成
              <br />
              代码负责兜底
            </div>
          </div>

          {/* 审计区标题 */}
          <div
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: 150,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              opacity: audit,
              transform: `translateY(${(1 - audit) * 24}px)`,
            }}
          >
            <Kicker text="AUDIT LOG" from={116} color={C.cyan} />
            <div
              style={{
                marginTop: 18,
                fontFamily: F.mono,
                fontSize: 64,
                fontWeight: 900,
                letterSpacing: 1,
                color: C.ink,
                textShadow: `0 0 60px ${C.cyan}33`,
                whiteSpace: 'nowrap',
              }}
            >
              ai_call_logs
            </div>
          </div>

          {/* 六个 log_type */}
          <div
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: 300,
              display: 'flex',
              justifyContent: 'center',
              gap: 16,
            }}
          >
            {LOG_TYPES.map((t, i) => (
              <Tag key={t} text={t} mono from={124 + i * 5} color={[C.cyan, C.lime, C.violet][i % 3]} />
            ))}
          </div>

          {/* 结论大字 */}
          <div
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: 440,
              display: 'flex',
              justifyContent: 'center',
              alignItems: 'baseline',
              opacity: big,
              transform: `translateY(${(1 - big) * 34}px) scale(${0.97 + big * 0.03})`,
              whiteSpace: 'nowrap',
            }}
          >
            <span
              style={{
                fontFamily: F.cn,
                fontSize: 104,
                fontWeight: 900,
                letterSpacing: -2,
                color: C.ink,
              }}
            >
              每一步，
            </span>
            <span
              style={{
                fontFamily: F.cn,
                fontSize: 104,
                fontWeight: 900,
                letterSpacing: -2,
                color: C.cyan,
                textShadow: `0 0 60px ${C.cyan}55`,
              }}
            >
              都可核验
            </span>
          </div>

          {/* 署名 */}
          <div
            style={{
              position: 'absolute',
              left: 0,
              right: 0,
              top: 640,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
          >
            <div
              style={{
                fontFamily: F.cn,
                fontSize: 36,
                fontWeight: 700,
                letterSpacing: 4,
                color: C.cyan,
                opacity: sign1,
                transform: `translateY(${(1 - sign1) * 18}px)`,
                whiteSpace: 'nowrap',
              }}
            >
              AI 节律守护 · 智能无感
            </div>
            <div
              style={{
                marginTop: 22,
                fontFamily: F.cn,
                fontSize: 28,
                fontWeight: 500,
                letterSpacing: 3,
                color: C.dim,
                opacity: sign2,
                transform: `translateY(${(1 - sign2) * 16}px)`,
                whiteSpace: 'nowrap',
              }}
            >
              后台 AI 值守台 · 6 个能力
            </div>
          </div>

        </AbsoluteFill>
      </Backdrop>
    </AbsoluteFill>
  );
};
