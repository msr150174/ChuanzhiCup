import React from 'react';
import { Composition } from 'remotion';
import { Teaser } from './Video';
import { BackendAI } from './BackendAI';
import { MiniAI } from './MiniAI';
import { FPS, H, TOTAL_FRAMES, W } from './theme';

export const RemotionRoot: React.FC = () => {
  return (
    <>
      <Composition
        id="Teaser"
        component={Teaser}
        durationInFrames={TOTAL_FRAMES}
        fps={FPS}
        width={W}
        height={H}
      />
      <Composition
        id="BackendAI"
        component={BackendAI}
        durationInFrames={1800}
        fps={FPS}
        width={W}
        height={H}
      />
      <Composition
        id="MiniAI"
        component={MiniAI}
        durationInFrames={1800}
        fps={FPS}
        width={W}
        height={H}
      />
    </>
  );
};
