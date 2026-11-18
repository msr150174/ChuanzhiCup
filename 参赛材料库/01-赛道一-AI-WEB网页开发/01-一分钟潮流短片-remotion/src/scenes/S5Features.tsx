import React from 'react';
import { AbsoluteFill, interpolate, useCurrentFrame } from 'remotion';
import { Backdrop, Caption, LightSweep } from '../components/Chrome';
import { Kicker, Rule, Tag } from '../components/ui';
import { C, EASE, F, enter } from '../theme';

/**
 * S5｜功能快切｜360 帧（12s）
 * 四张功能卡每 90 帧一换：右侧滑入 → 停留 → 向上淡出
 * 左上角 01–04 超大序号水印常驻，底部字幕贯穿全场
 */

type Feature = {
  index: string;
  title: string;
  content: string;
  kicker: string;
  accent: string;
  tags: { text: string; mono?: boolean }[];
};

const FEATURES: Feature[] = [
  {
    index: '01',
    title: '数据总览',
    content: '7 个指标卡 · 近 7/14/30 天双线趋势 · ECharts 6',
    kicker: 'DASHBOARD',
    accent: C.cyan,
    tags: [{ text: '指标卡' }, { text: '双线趋势' }, { text: 'ECharts 6', mono: true }],
  },
  {
    index: '02',
    title: '告警处置',
    content: '按状态/级别筛选 · 填写处理说明 · 状态留痕闭环',
    kicker: 'ALERTS',
    accent: C.violet,
    tags: [{ text: '状态筛选' }, { text: '级别筛选' }, { text: '状态留痕' }],
  },
  {
    index: '03',
    title: '用户与设备',
    content: '手机号 + 验证码登录 · 角色与启停用 · 设备绑定关系',
    kicker: 'USERS',
    accent: C.lime,
    tags: [{ text: '验证码登录' }, { text: '角色与启停用' }, { text: '设备绑定' }],
  },
  {
    index: '04',
    title: '系统监控',
    content: 'MySQL · Redis · JVM 指标 · 30 秒自动刷新',
    kicker: 'SYSTEM',
    accent: C.amber,
    tags: [{ text: 'MySQL', mono: true }, { text: 'Redis', mono: true }, { text: 'JVM', mono: true }],
  },
];

const CARD_LIFE = 90;

const Card: React.FC<{ f: Feature; start: number }> = ({ f, start }) => {
  const frame = useCurrentFrame();
  const local = frame - start;
  if (local < -8 || local >= CARD_LIFE) return null;

  // 入场：translateX 120→0 + 透明度 0→1，14 帧（提前 6 帧起，避免换卡瞬间空帧）
  const inA = interpolate(frame, [start - 6, start + 8], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });
  // 出场：向上淡出，12 帧（与下一张入场衔接）
  const outA = interpolate(frame, [start + 78, start + CARD_LIFE], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.inOut,
  });

  const a = Math.min(inA, outA);
  const x = (1 - inA) * 120;
  const y = (1 - outA) * -44;

  const titleA = enter(frame, start - 6, 18);
  const bodyA = enter(frame, start - 4, 18);
  const activeIdx = Math.floor(frame / CARD_LIFE);

  return (
    <div
      style={{
        position: 'absolute',
        left: 140,
        top: 300,
        width: 1640,
        height: 500,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        opacity: a,
        transform: `translate3d(${x}px, ${y}px, 0)`,
        borderRadius: 28,
        padding: 52,
        background:
          'linear-gradient(155deg, rgba(255,255,255,0.078), rgba(255,255,255,0.018))',
        border: `1px solid ${C.line}`,
        boxShadow: `inset 0 1px 0 rgba(255,255,255,0.10), 0 30px 80px rgba(0,0,0,0.55), 0 0 90px ${f.accent}14`,
        backdropFilter: 'blur(14px)',
        overflow: 'hidden',
      }}
    >
      {/* 顶部细线 */}
      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          height: 2,
          background: `linear-gradient(90deg, ${f.accent}, ${f.accent}00 72%)`,
          opacity: 0.9,
        }}
      />
      {/* 卡内左下角淡光 */}
      <div
        style={{
          position: 'absolute',
          width: 620,
          height: 620,
          left: -220,
          bottom: -380,
          borderRadius: '50%',
          background: `radial-gradient(circle, ${f.accent}22 0%, transparent 66%)`,
          filter: 'blur(20px)',
        }}
      />

      {/* 首行：序号 + Kicker */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          position: 'relative',
        }}
      >
        <div
          style={{
            fontFamily: F.mono,
            fontSize: 20,
            fontWeight: 600,
            letterSpacing: 6,
            color: 'rgba(255,255,255,0.40)',
          }}
        >
          {f.index} / 04
        </div>
        <Kicker text={f.kicker} from={start + 4} color={f.accent} />
      </div>

      {/* 标题 */}
      <div
        style={{
          position: 'relative',
          marginTop: 22,
          fontFamily: F.cn,
          fontSize: 96,
          fontWeight: 900,
          lineHeight: 1.1,
          letterSpacing: -1,
          color: C.ink,
          opacity: titleA,
          transform: `translateY(${(1 - titleA) * 26}px)`,
          clipPath: `inset(${(1 - titleA) * 100}% 0 0 0)`,
          whiteSpace: 'nowrap',
        }}
      >
        {f.title}
      </div>

      <Rule from={start + 10} width={240} color={f.accent} style={{ marginTop: 22 }} />

      {/* 内容行（逐字使用分镜原文） */}
      <div
        style={{
          position: 'relative',
          marginTop: 26,
          fontFamily: F.cn,
          fontSize: 44,
          fontWeight: 500,
          lineHeight: 1.4,
          letterSpacing: 0.5,
          color: 'rgba(255,255,255,0.88)',
          opacity: bodyA,
          transform: `translateY(${(1 - bodyA) * 20}px)`,
          whiteSpace: 'nowrap',
        }}
      >
        {f.content}
      </div>

      {/* 短标签 */}
      <div
        style={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          marginTop: 32,
        }}
      >
        {f.tags.map((t, i) => (
          <Tag
            key={t.text}
            text={t.text}
            color={f.accent}
            from={start - 2 + i * 3}
            mono={t.mono}
          />
        ))}
      </div>

      {/* 卡内进度刻度 */}
      <div
        style={{
          position: 'absolute',
          right: 52,
          bottom: 46,
          display: 'flex',
          alignItems: 'center',
          gap: 8,
        }}
      >
        {FEATURES.map((_, i) => {
          const on = i <= activeIdx;
          return (
            <div
              key={i}
              style={{
                width: i === activeIdx ? 46 : 20,
                height: 3,
                background: on ? f.accent : 'rgba(255,255,255,0.16)',
                boxShadow: i === activeIdx ? `0 0 14px ${f.accent}` : 'none',
                opacity: on ? 1 : 0.6,
              }}
            />
          );
        })}
      </div>
    </div>
  );
};

export const S5Features: React.FC = () => {
  const frame = useCurrentFrame();
  const active = Math.min(FEATURES.length - 1, Math.floor(frame / CARD_LIFE));

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <Backdrop accent={FEATURES[active].accent} second={C.violet} intensity={0.95} gridOpacity={0.055}>
        {/* 左上角超大序号水印（交叉淡入淡出） */}
        {FEATURES.map((f, i) => {
          const start = i * CARD_LIFE;
          const wmIn = interpolate(frame, [start - 6, start + 4], [0, 1], {
            extrapolateLeft: 'clamp',
            extrapolateRight: 'clamp',
          });
          const wmOut =
            i === FEATURES.length - 1
              ? 1
              : interpolate(frame, [start + 80, start + 88], [1, 0], {
                  extrapolateLeft: 'clamp',
                  extrapolateRight: 'clamp',
                });
          return (
            <div
              key={f.index}
              style={{
                position: 'absolute',
                left: 130,
                top: 6,
                fontFamily: F.mono,
                fontSize: 280,
                fontWeight: 900,
                lineHeight: 1,
                letterSpacing: -8,
                color: C.ink,
                opacity: 0.1 * wmIn * wmOut,
                whiteSpace: 'nowrap',
              }}
            >
              {f.index}
            </div>
          );
        })}

        {/* 四张功能卡 */}
        {FEATURES.map((f, i) => (
          <Card key={f.index} f={f} start={i * CARD_LIFE} />
        ))}

        {/* 换卡时的斜向扫光 */}
        {FEATURES.map((_, i) =>
          i === 0 ? null : (
            <LightSweep key={i} from={i * CARD_LIFE - 6} dur={16} color="rgba(255,255,255,0.12)" />
          ),
        )}

        {/* 底部字幕：全程显示 */}
        <Caption text="AI 发现 → 人来闭环 → 全程留痕" from={8} to={380} accent={C.cyan} />
      </Backdrop>
    </AbsoluteFill>
  );
};
