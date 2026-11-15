import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';

/**
 * 片头 —— 3 秒
 *
 * 设计要点：赛题视频脚本明确要求「前 15 秒上价值，不要放 logo」。
 * 所以这里直接上问题，不上品牌。
 */
export const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // 第一句：0-40 帧淡入并上移
  const q1Opacity = interpolate(frame, [0, 25], [0, 1], {
    extrapolateRight: 'clamp',
  });
  const q1Y = interpolate(frame, [0, 30], [40, 0], {
    extrapolateRight: 'clamp',
  });

  // 第二句：35-70 帧，弹性放大
  const q2Spring = spring({
    frame: frame - 35,
    fps,
    config: { damping: 14, stiffness: 120 },
  });

  // 底部细线：60-90 帧展开
  const lineWidth = interpolate(frame, [60, 90], [0, 100], {
    extrapolateRight: 'clamp',
  });

  // 淡出
  const fadeOut = interpolate(frame, [80, 90], [1, 0.9], {
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill
      style={{
        background: 'linear-gradient(160deg, #0B1220 0%, #14213D 100%)',
        justifyContent: 'center',
        alignItems: 'center',
        fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif',
        opacity: fadeOut,
      }}
    >
      <div
        style={{
          opacity: q1Opacity,
          transform: `translateY(${q1Y}px)`,
          fontSize: 64,
          color: '#8FA8C8',
          letterSpacing: 4,
          marginBottom: 40,
        }}
      >
        独居老人在家摔倒，子女多久能知道？
      </div>

      <div
        style={{
          transform: `scale(${q2Spring})`,
          opacity: q2Spring,
          fontSize: 96,
          fontWeight: 700,
          color: '#FFFFFF',
          letterSpacing: 2,
        }}
      >
        长按 3 秒，家属立刻收到告警
      </div>

      <div
        style={{
          marginTop: 60,
          height: 4,
          width: `${lineWidth}%`,
          maxWidth: 600,
          background: 'linear-gradient(90deg, #2F6BFF, #5A8BFF)',
          borderRadius: 2,
        }}
      />
    </AbsoluteFill>
  );
};
