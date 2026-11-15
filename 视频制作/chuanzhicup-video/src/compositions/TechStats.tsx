import {
  AbsoluteFill,
  interpolate,
  spring,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';

/**
 * 技术数据动画 —— 8 秒
 *
 * 全部数据来自项目实际核查，不是编的：
 *   153 接口 / 36 页面 / 12 张表 / 14 处缺陷修复 / 3 层降级
 */

type Stat = {
  value: number;
  suffix: string;
  label: string;
  delay: number;
};

const STATS: Stat[] = [
  { value: 153, suffix: '', label: '后端接口', delay: 0 },
  { value: 36, suffix: '', label: '前端页面', delay: 20 },
  { value: 12, suffix: '', label: '数据库表', delay: 40 },
  { value: 14, suffix: '处', label: '修复的原项目缺陷', delay: 60 },
];

export const TechStats: React.FC = () => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  // 标题
  const titleOpacity = interpolate(frame, [0, 20], [0, 1], {
    extrapolateRight: 'clamp',
  });

  // 结尾三层降级
  const layersSpring = spring({
    frame: frame - 150,
    fps,
    config: { damping: 16, stiffness: 100 },
  });

  return (
    <AbsoluteFill
      style={{
        background: 'linear-gradient(180deg, #0F172A 0%, #1E293B 100%)',
        fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif',
        padding: 100,
      }}
    >
      <div
        style={{
          opacity: titleOpacity,
          fontSize: 56,
          fontWeight: 700,
          color: '#E2E8F0',
          marginBottom: 80,
        }}
      >
        工程规模
      </div>

      {/* 四个数字 */}
      <div style={{ display: 'flex', gap: 60 }}>
        {STATS.map((s) => {
          const local = Math.max(0, frame - 10 - s.delay);
          const progress = Math.min(1, local / 60);
          // 缓动计数
          const eased = 1 - Math.pow(1 - progress, 3);
          const display = Math.round(s.value * eased);
          const appear = interpolate(frame, [s.delay, s.delay + 15], [0, 1], {
            extrapolateLeft: 'clamp',
            extrapolateRight: 'clamp',
          });

          return (
            <div
              key={s.label}
              style={{
                opacity: appear,
                transform: `translateY(${(1 - appear) * 30}px)`,
                flex: 1,
                background: 'rgba(47, 107, 255, 0.12)',
                border: '1px solid rgba(90, 139, 255, 0.4)',
                borderRadius: 20,
                padding: '40px 30px',
                textAlign: 'center',
              }}
            >
              <div
                style={{
                  fontSize: 96,
                  fontWeight: 800,
                  color: '#5A8BFF',
                  lineHeight: 1,
                  fontVariantNumeric: 'tabular-nums',
                }}
              >
                {display}
                <span style={{ fontSize: 40, marginLeft: 6 }}>{s.suffix}</span>
              </div>
              <div
                style={{
                  fontSize: 24,
                  color: '#94A3B8',
                  marginTop: 20,
                }}
              >
                {s.label}
              </div>
            </div>
          );
        })}
      </div>

      {/* 三层降级 */}
      <div
        style={{
          marginTop: 90,
          opacity: layersSpring,
          transform: `translateY(${(1 - layersSpring) * 40}px)`,
        }}
      >
        <div style={{ fontSize: 36, color: '#64748B', marginBottom: 30 }}>
          三层 AI 降级 —— 用户永远看不到报错
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
          {['① Ollama 本地模型', '② Coze 云端模型', '③ 规则库兜底'].map(
            (label, i) => {
              const appear = interpolate(
                frame,
                [155 + i * 12, 168 + i * 12],
                [0, 1],
                { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' }
              );
              // 第三层用绿色高亮，并在出现后加脉冲
              const isFallback = i === 2;
              const pulse = isFallback
                ? 1 + 0.04 * Math.sin((frame - 180) / 6)
                : 1;
              return (
                <div
                  key={label}
                  style={{ display: 'flex', alignItems: 'center', gap: 20 }}
                >
                  <div
                    style={{
                      opacity: appear,
                      transform: `scale(${appear * pulse})`,
                      padding: '20px 32px',
                      background:
                        i === 2
                          ? 'rgba(24, 151, 90, 0.2)'
                          : 'rgba(47, 107, 255, 0.15)',
                      border: `2px solid ${
                        i === 2 ? '#18975A' : '#2F6BFF'
                      }`,
                      borderRadius: 14,
                      fontSize: 28,
                      color: i === 2 ? '#4ADE80' : '#93B4FF',
                      fontWeight: 600,
                    }}
                  >
                    {label}
                  </div>
                  {i < 2 && (
                    <div
                      style={{
                        opacity: appear,
                        fontSize: 32,
                        color: '#475569',
                      }}
                    >
                      →
                    </div>
                  )}
                </div>
              );
            }
          )}
        </div>
      </div>
    </AbsoluteFill>
  );
};
