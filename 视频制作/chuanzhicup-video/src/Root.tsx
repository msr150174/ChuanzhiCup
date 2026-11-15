import { Composition } from 'remotion';
import { Intro } from './compositions/Intro';
import { TechStats } from './compositions/TechStats';
import { Demo } from './compositions/Demo';

/**
 * Remotion 根组件
 *
 * 每个 <Composition> 就是一个独立可渲染的视频。
 * Studio 里会把这些全部列出来，可以单独预览和渲染。
 */
export const RemotionRoot: React.FC = () => {
  return (
    <>
      {/* ===== 片头：3 秒，品牌 + 一句话价值 ===== */}
      <Composition
        id="Intro"
        component={Intro}
        durationInFrames={90}      // 30fps × 3s
        fps={30}
        width={1920}
        height={1080}
      />

      {/* ===== 技术数据动画：8 秒，数字滚动 ===== */}
      <Composition
        id="TechStats"
        component={TechStats}
        durationInFrames={240}     // 30fps × 8s
        fps={30}
        width={1920}
        height={1080}
      />

      {/* ===== 完整演示片（占位，后续接录屏）===== */}
      <Composition
        id="Demo"
        component={Demo}
        durationInFrames={450}     // 30fps × 15s
        fps={30}
        width={1920}
        height={1080}
      />
    </>
  );
};
