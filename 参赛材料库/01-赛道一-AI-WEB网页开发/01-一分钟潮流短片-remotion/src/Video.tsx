import React from 'react';
import { AbsoluteFill, Sequence } from 'remotion';
import { ProgressBar } from './components/Chrome';
import { S1Hook } from './scenes/S1Hook';
import { S2Problem } from './scenes/S2Problem';
import { S3Reveal } from './scenes/S3Reveal';
import { S4AiStack } from './scenes/S4AiStack';
import { S5Features } from './scenes/S5Features';
import { S6Scale } from './scenes/S6Scale';
import { S7Finale } from './scenes/S7Finale';

/**
 * 60 秒主时间线
 *
 * 每个场景内部 useCurrentFrame() 从 0 开始（Sequence 会重置帧号），
 * 场景时长见下方 durationInFrames，与 theme.ts 的 SCENES 保持一致。
 */
export const Teaser: React.FC = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: '#05060A' }}>
      <Sequence from={0} durationInFrames={240} name="S1 Hook">
        <S1Hook />
      </Sequence>
      <Sequence from={240} durationInFrames={270} name="S2 Problem">
        <S2Problem />
      </Sequence>
      <Sequence from={510} durationInFrames={240} name="S3 Reveal">
        <S3Reveal />
      </Sequence>
      <Sequence from={750} durationInFrames={420} name="S4 AI Stack">
        <S4AiStack />
      </Sequence>
      <Sequence from={1170} durationInFrames={360} name="S5 Features">
        <S5Features />
      </Sequence>
      <Sequence from={1530} durationInFrames={150} name="S6 Scale">
        <S6Scale />
      </Sequence>
      <Sequence from={1680} durationInFrames={120} name="S7 Finale">
        <S7Finale />
      </Sequence>
      <ProgressBar />
    </AbsoluteFill>
  );
};
