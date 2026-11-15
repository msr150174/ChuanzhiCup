import {
  AbsoluteFill,
  interpolate,
  Sequence,
  useCurrentFrame,
} from 'remotion';

/**
 * 完整演示片占位 —— 15 秒
 *
 * 用途：后续把 OBS 录屏（public/ 目录）接进来，
 * 用 <Sequence> 做时间轴编排。
 *
 * 现在这一版是纯占位，让 Studio 能正常预览和渲染。
 */
export const Demo: React.FC = () => {
  const frame = useCurrentFrame();

  const fadeIn = interpolate(frame, [0, 20], [0, 1], {
    extrapolateRight: 'clamp',
  });

  return (
    <AbsoluteFill
      style={{
        background: '#0B1220',
        fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif',
        justifyContent: 'center',
        alignItems: 'center',
      }}
    >
      {/* 段落一：0-150 帧 */}
      <Sequence from={0} durationInFrames={150}>
        <AbsoluteFill
          style={{ justifyContent: 'center', alignItems: 'center' }}
        >
          <div
            style={{
              opacity: fadeIn,
              fontSize: 72,
              color: '#E2E8F0',
              fontWeight: 700,
            }}
          >
            AI 节律守护 · 智能无感
          </div>
          <div
            style={{
              marginTop: 24,
              fontSize: 32,
              color: '#64748B',
              opacity: fadeIn,
            }}
          >
            面向独居老人的 AI 健康守护智能体
          </div>
        </AbsoluteFill>
      </Sequence>

      {/* 段落二：150-300 帧 —— 留给录屏 */}
      <Sequence from={150} durationInFrames={150}>
        <AbsoluteFill
          style={{
            justifyContent: 'center',
            alignItems: 'center',
            border: '2px dashed #334155',
            margin: 80,
            borderRadius: 24,
          }}
        >
          <div style={{ fontSize: 40, color: '#475569' }}>
            【此处接入 OBS 录屏】
          </div>
          <div style={{ marginTop: 16, fontSize: 24, color: '#334155' }}>
            把 mp4 放到 public/，然后用 &lt;OffthreadVideo src=&#123;staticFile('...')&#125; /&gt;
          </div>
        </AbsoluteFill>
      </Sequence>

      {/* 段落三：300-450 帧 */}
      <Sequence from={300} durationInFrames={150}>
        <AbsoluteFill
          style={{ justifyContent: 'center', alignItems: 'center' }}
        >
          <div style={{ fontSize: 48, color: '#5A8BFF', fontWeight: 700 }}>
            团队信息待填
          </div>
          <div style={{ marginTop: 20, fontSize: 28, color: '#64748B' }}>
            传智杯 2026 · 赛道二 AI 微信小程序开发挑战赛
          </div>
        </AbsoluteFill>
      </Sequence>
    </AbsoluteFill>
  );
};
