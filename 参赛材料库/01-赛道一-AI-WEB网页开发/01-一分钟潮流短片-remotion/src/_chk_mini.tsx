import React from 'react';
import { AbsoluteFill, Composition, registerRoot, Sequence } from 'remotion';
import { ProgressBar } from './components/Chrome';
import { C } from './theme';
import { M1Intro } from './scenes/mini/M1Intro';
import { M2Chat } from './scenes/mini/M2Chat';
import { M3Fallback } from './scenes/mini/M3Fallback';

/** 临时自检入口：只挂 M1/M2/M3，帧号与 MiniAI 一致 */
const Chk: React.FC = () => (
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
    <ProgressBar accent={C.lime} />
  </AbsoluteFill>
);

const Root: React.FC = () => (
  <Composition id="ChkMini" component={Chk} durationInFrames={1800} fps={30} width={1920} height={1080} />
);

registerRoot(Root);
