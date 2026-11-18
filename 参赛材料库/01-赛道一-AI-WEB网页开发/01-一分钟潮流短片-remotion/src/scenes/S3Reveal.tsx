import React from 'react';
import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';
import { Backdrop, Caption, LightSweep } from '../components/Chrome';
import { Kicker, Rule, Tag } from '../components/ui';
import { C, F, enter } from '../theme';

/**
 * S3｜Reveal 作品亮相｜240 帧（8s）
 *
 * 关键时间点（场景内相对帧）：
 *   6       Kicker「GUARDIAN ADMIN / WEB」入场
 *   12      光带斜扫（17.4s）
 *   16      主标题第一行「AI 节律守护」spring 弹入（damping 16 / stiffness 90）
 *   28      主标题第二行「智能无感」弹入
 *   48      副标上浮
 *   62 起   技术栈标签依次上浮（间隔 8 帧）
 *   70–232  底部字幕
 */

/** 主标题单行：spring 缩放 0.92→1 + 青/紫错位残影 */
const TitleLine: React.FC<{ text: string; size: number; p: number }> = ({
  text,
  size,
  p,
}) => {
  const scale = interpolate(p, [0, 1], [0.92, 1]);
  const y = interpolate(p, [0, 1], [46, 0]);
  const op = Math.min(1, p * 1.6);

  const base: React.CSSProperties = {
    fontFamily: F.cn,
    fontSize: size,
    fontWeight: 900,
    letterSpacing: -5,
    lineHeight: 1.08,
    whiteSpace: 'nowrap',
  };

  return (
    <div
      style={{
        position: 'relative',
        opacity: op,
        transform: `translateY(${y}px) scale(${scale})`,
        transformOrigin: 'left center',
      }}
    >
      {/* 错位残影：青 -6px / 紫 +6px，透明度 0.25 */}
      <div
        style={{
          ...base,
          position: 'absolute',
          left: 0,
          top: 0,
          color: C.cyan,
          opacity: 0.25,
          transform: 'translate(-6px, 2px)',
        }}
      >
        {text}
      </div>
      <div
        style={{
          ...base,
          position: 'absolute',
          left: 0,
          top: 0,
          color: C.violet,
          opacity: 0.25,
          transform: 'translate(6px, -2px)',
        }}
      >
        {text}
      </div>
      <div
        style={{
          ...base,
          position: 'relative',
          color: C.ink,
          textShadow: `0 0 80px ${C.cyan}55`,
        }}
      >
        {text}
      </div>
    </div>
  );
};

export const S3Reveal: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const title1 = spring({
    frame: Math.max(0, frame - 12),
    fps,
    config: { damping: 16, stiffness: 90, mass: 0.9 },
  });
  const title2 = spring({
    frame: Math.max(0, frame - 24),
    fps,
    config: { damping: 16, stiffness: 90, mass: 0.9 },
  });

  const sub = enter(frame, 44, 18);
  const ruleGrow = enter(frame, 36, 22);

  // 右侧结构竖线（杂志感）：首帧已半显，避免硬切后出现空画面
  const vline = enter(frame, -14, 26);

  return (
    <AbsoluteFill>
      <Backdrop accent={C.cyan} second={C.violet} intensity={1} />

      {/* 右侧结构线 */}
      <div
        style={{
          position: 'absolute',
          right: 140,
          top: 200,
          width: 1,
          height: 640 * vline,
          background: `linear-gradient(180deg, transparent, ${C.cyan}88, transparent)`,
          opacity: vline * 0.7,
        }}
      />
      {[0, 1, 2].map((i) => (
        <div
          key={i}
          style={{
            position: 'absolute',
            right: 140,
            top: 200 + i * 320,
            width: 28 * vline,
            height: 1,
            background: C.cyan,
            opacity: vline * 0.6,
          }}
        />
      ))}

      {/* Kicker */}
      <div style={{ position: 'absolute', left: 140, top: 196 }}>
        <Kicker text="GUARDIAN ADMIN / WEB" from={4} />
      </div>

      {/* 主标题两行 */}
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 252,
          display: 'flex',
          flexDirection: 'column',
          gap: 4,
        }}
      >
        <TitleLine text="AI 节律守护" size={168} p={title1} />
        <TitleLine text="智能无感" size={168} p={title2} />
      </div>

      {/* 分割线 + 副标 */}
      <div style={{ position: 'absolute', left: 140, top: 636 }}>
        <Rule from={36} width={420} color={`${C.cyan}88`} />
      </div>
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 668,
          opacity: sub,
          transform: `translateY(${(1 - sub) * 22}px)`,
          fontFamily: F.cn,
          fontSize: 40,
          fontWeight: 500,
          letterSpacing: 1,
          color: 'rgba(255,255,255,0.80)',
          whiteSpace: 'nowrap',
        }}
      >
        运营管理 Web 端 · 守护链条的最后一公里
      </div>

      {/* 技术栈标签 */}
      <div
        style={{
          position: 'absolute',
          left: 140,
          top: 780,
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          opacity: Math.min(1, ruleGrow * 1.2),
        }}
      >
        <Tag text="Vue 3" color={C.cyan} from={56} mono />
        <Tag text="TypeScript" color={C.cyan} from={64} mono />
        <Tag text="Element Plus" color={C.violet} from={72} mono />
        <Tag text="Spring Boot 3.2.5" color={C.violet} from={80} mono />
        <Tag text="ECharts 6" color={C.lime} from={88} mono />
      </div>

      {/* 右侧视觉锚点：细圆环 + 竖向标签，平衡左侧大标题的视觉重量 */}
      <div
        style={{
          position: 'absolute',
          right: 110,
          top: 190,
          width: 640,
          height: 640,
          opacity: vline,
        }}
      >
        <div
          style={{
            position: 'absolute',
            inset: 0,
            borderRadius: '50%',
            border: `1px solid ${C.cyan}33`,
            boxShadow: `inset 0 0 140px ${C.cyan}14`,
          }}
        />
        <div
          style={{
            position: 'absolute',
            inset: 64,
            borderRadius: '50%',
            border: `1px solid ${C.violet}26`,
          }}
        />
        <div
          style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontFamily: F.mono,
            fontSize: 20,
            letterSpacing: 12,
            color: `${C.cyan}77`,
            writingMode: 'vertical-rl',
          }}
        >
          GUARDIAN ADMIN
        </div>
      </div>

      {/* 光带斜扫：17.4s */}
      <LightSweep from={12} dur={22} color="rgba(150,240,255,0.30)" />

      {/* 底部字幕 */}
      <Caption
        text="让 AI 发现的每一条异常，都有人接住。"
        from={64}
        to={232}
        accent={C.cyan}
      />
    </AbsoluteFill>
  );
};
