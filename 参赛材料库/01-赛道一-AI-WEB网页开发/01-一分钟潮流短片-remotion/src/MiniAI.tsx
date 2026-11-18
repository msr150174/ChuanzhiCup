import React from 'react';
import { AbsoluteFill, Sequence } from 'remotion';
import { ProgressBar } from './components/Chrome';
import { C } from './theme';
import { M1Intro } from './scenes/mini/M1Intro';
import { M2Chat } from './scenes/mini/M2Chat';
import { M3Fallback } from './scenes/mini/M3Fallback';
import { M4RuleDesign } from './scenes/mini/M4RuleDesign';
import { M5Report } from './scenes/mini/M5Report';

/**
 * 小程序 AI 能力讲解 · 60 秒（1800 帧）
 * M1 0–210      开场：老人不会用复杂 App
 * M2 210–600    「小护」对话 + 真实健康数据上下文（13s）
 * M3 600–1020   三层降级：Ollama → Coze → 规则库（14s）
 * M4 1020–1380  规则库 12 条的设计细节 + 适老化（12s）
 * M5 1380–1800  睡眠分析 / 健康报告 + 收尾（14s）
 */
export const MiniAI: React.FC = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <Sequence from={0} durationInFrames={210}>
        <M1Intro />
      </Sequence>
      <Sequence from={210} durationInFrames={390}>
        <M2Chat />
      </Sequence>
      <Sequence from={600} durationInFrames={420}>
        <M3Fallback />
      </Sequence>
      <Sequence from={1020} durationInFrames={360}>
        <M4RuleDesign />
      </Sequence>
      <Sequence from={1380} durationInFrames={420}>
        <M5Report />
      </Sequence>
      <ProgressBar accent={C.lime} />
    </AbsoluteFill>
  );
};
