import React from 'react';
import { AbsoluteFill, interpolate, spring, useCurrentFrame, useVideoConfig } from 'remotion';
import { Backdrop, LightSweep } from '../components/Chrome';
import { C, EASE, F, enter } from '../theme';

/**
 * S7｜收尾｜120 帧（4s）
 * 0–4f    光带扫过
 * 4–40f   主 Slogan spring + 遮罩砸入
 * 42–70f  两行署名上浮
 * 90–120f 整体 scale 1→0.97、透明度 1→0，最后 6 帧收黑
 */

export const S7Finale: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // 主 Slogan 砸入
  const slam = spring({
    frame: frame - 4,
    fps,
    config: { damping: 15, stiffness: 140, mass: 0.7 },
  });
  const sloganScale = interpolate(slam, [0, 1], [1.22, 1]);
  const sloganOpacity = interpolate(frame, [4, 14], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  // 逐字遮罩：从左往右揭开
  const reveal = interpolate(frame, [4, 30], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });

  // 署名上浮
  const sig1 = enter(frame, 48, 16);
  const sig2 = enter(frame, 58, 16);
  const barA = enter(frame, 42, 16);

  // 整体拉远 + 淡出（114 帧起为纯底色，最后 6 帧收黑）
  const outScale = interpolate(frame, [90, 120], [1, 0.97], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const outOpacity = interpolate(frame, [90, 114], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });
  const blackout = interpolate(frame, [110, 116], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <AbsoluteFill style={{ opacity: outOpacity, transform: `scale(${outScale})` }}>
        <Backdrop accent={C.cyan} second={C.violet} intensity={1.1} gridOpacity={0.045}>
          {/* 光带扫过 */}
          <LightSweep from={-4} dur={22} color="rgba(255,255,255,0.55)" />

          <AbsoluteFill
            style={{
              justifyContent: 'center',
              alignItems: 'center',
              flexDirection: 'column',
              paddingBottom: 20,
            }}
          >
            {/* Slogan 背光 */}
            <div
              style={{
                position: 'relative',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <div
                style={{
                  position: 'absolute',
                  width: 1500,
                  height: 620,
                  borderRadius: '50%',
                  background: `radial-gradient(circle, ${C.cyan}26 0%, ${C.violet}14 42%, transparent 68%)`,
                  filter: 'blur(46px)',
                  opacity: sloganOpacity * 0.95,
                  transform: `scale(${sloganScale})`,
                }}
              />

              <div
                style={{
                  position: 'relative',
                  display: 'flex',
                  alignItems: 'baseline',
                  opacity: sloganOpacity,
                  transform: `scale(${sloganScale})`,
                  clipPath: `inset(0 ${(1 - reveal) * 100}% 0 0)`,
                  fontFamily: F.cn,
                  fontSize: 96,
                  fontWeight: 900,
                  lineHeight: 1.2,
                  letterSpacing: -1,
                  whiteSpace: 'nowrap',
                }}
              >
                <span style={{ color: C.ink, textShadow: '0 8px 40px rgba(0,0,0,0.7)' }}>
                  让每一次异常，
                </span>
                <span
                  style={{
                    color: C.cyan,
                    textShadow: `0 0 60px ${C.cyan}66, 0 8px 40px rgba(0,0,0,0.7)`,
                  }}
                >
                  都有人接住。
                </span>
              </div>
            </div>

            {/* 主副线 */}
            <div
              style={{
                marginTop: 40,
                width: 132 * barA,
                height: 2,
                background: C.cyan,
                boxShadow: `0 0 22px ${C.cyan}`,
                opacity: barA,
              }}
            />

            {/* 署名两行 */}
            <div
              style={{
                marginTop: 44,
                fontFamily: F.cn,
                fontSize: 36,
                fontWeight: 700,
                letterSpacing: 6,
                color: C.cyan,
                opacity: sig1,
                transform: `translateY(${(1 - sig1) * 22}px)`,
                whiteSpace: 'nowrap',
              }}
            >
              AI 节律守护 · 智能无感
            </div>
            <div
              style={{
                marginTop: 18,
                fontFamily: F.cn,
                fontSize: 28,
                fontWeight: 500,
                letterSpacing: 3,
                color: C.dim,
                opacity: sig2,
                transform: `translateY(${(1 - sig2) * 22}px)`,
                whiteSpace: 'nowrap',
              }}
            >
              第九届传智杯 · 赛道一 AI WEB 网页开发
            </div>
          </AbsoluteFill>
        </Backdrop>
      </AbsoluteFill>

      {/* 最后 6 帧收黑 */}
      <AbsoluteFill style={{ backgroundColor: C.bg, opacity: blackout }} />
    </AbsoluteFill>
  );
};
