import React from 'react';
import { AbsoluteFill, Sequence } from 'remotion';
import { ProgressBar } from './components/Chrome';
import { C } from './theme';
import { B1Intro } from './scenes/backend/B1Intro';
import { B2Nl2Sql } from './scenes/backend/B2Nl2Sql';
import { B3Rag } from './scenes/backend/B3Rag';
import { B4BriefRiskScript } from './scenes/backend/B4BriefRiskScript';
import { B5Guard } from './scenes/backend/B5Guard';

/**
 * 后台 AI 能力讲解 · 60 秒（1800 帧）
 * B1 0–180      开场：后台不是看板，是 AI 值守台
 * B2 180–660    NL2SQL 数据问答（16s）
 * B3 660–1080   RAG 知识助手（14s）
 * B4 1080–1500  简报 / 风险画像 / 处置话术（14s）
 * B5 1500–1800  五道护栏 + 审计 + 收尾（10s）
 */
export const BackendAI: React.FC = () => {
  return (
    <AbsoluteFill style={{ backgroundColor: C.bg }}>
      <Sequence from={0} durationInFrames={180}>
        <B1Intro />
      </Sequence>
      <Sequence from={180} durationInFrames={480}>
        <B2Nl2Sql />
      </Sequence>
      <Sequence from={660} durationInFrames={420}>
        <B3Rag />
      </Sequence>
      <Sequence from={1080} durationInFrames={420}>
        <B4BriefRiskScript />
      </Sequence>
      <Sequence from={1500} durationInFrames={300}>
        <B5Guard />
      </Sequence>
      <ProgressBar accent={C.cyan} />
    </AbsoluteFill>
  );
};
