import { Easing, interpolate } from 'remotion';

/** ===== 成片基础参数 ===== */
export const W = 1920;
export const H = 1080;
export const FPS = 30;
export const TOTAL_FRAMES = 1800; // 60s

/** ===== 潮流短片配色 =====
 * 深空黑底 + 霓虹青主色 + 紫罗兰副色 + 酸性绿点缀 + 警示红
 */
export const C = {
  bg: '#05060A',
  bgSoft: '#0A0C14',
  panel: 'rgba(255,255,255,0.045)',
  line: 'rgba(255,255,255,0.10)',
  ink: '#FFFFFF',
  dim: '#8A93A8',
  dimmer: '#4A5163',
  cyan: '#00E5FF',
  violet: '#7B5CFF',
  lime: '#C6FF3D',
  warn: '#FF4D3D',
  amber: '#FFB020',
};

/** ===== 字体 =====
 * 中文：微软雅黑（本机已装，Bold 可用）
 * 英文/数字：Source Code Pro Black（本机已装）用于科技感数字
 * Impact 用于超大冲击字
 */
export const F = {
  cn: '"Microsoft YaHei", "PingFang SC", "Hiragino Sans GB", sans-serif',
  mono: '"Source Code Pro", "Consolas", monospace',
  impact: 'Impact, "Arial Black", sans-serif',
};

/** ===== 缓动 ===== */
export const EASE = {
  out: Easing.bezier(0.16, 1, 0.3, 1),
  inOut: Easing.bezier(0.65, 0, 0.35, 1),
  snap: Easing.bezier(0.34, 1.56, 0.64, 1),
};

/** 0→1 入场进度 */
export const enter = (frame: number, delay: number, dur = 16) =>
  interpolate(frame, [delay, delay + dur], [0, 1], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.out,
  });

/** 1→0 出场进度 */
export const leave = (frame: number, start: number, dur = 12) =>
  interpolate(frame, [start, start + dur], [1, 0], {
    extrapolateLeft: 'clamp',
    extrapolateRight: 'clamp',
    easing: EASE.inOut,
  });

/** 区间淡入淡出，返回 0→1→0 */
export const windowFade = (
  frame: number,
  inAt: number,
  outAt: number,
  fade = 10,
) =>
  Math.min(
    interpolate(frame, [inAt, inAt + fade], [0, 1], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    }),
    interpolate(frame, [outAt - fade, outAt], [1, 0], {
      extrapolateLeft: 'clamp',
      extrapolateRight: 'clamp',
    }),
  );

/** 章节时间轴（帧，绝对） */
export const SCENES = [
  { id: 'S1', name: 'Hook 跌倒', from: 0, dur: 240 },
  { id: 'S2', name: 'Problem 链条断了', from: 240, dur: 270 },
  { id: 'S3', name: 'Reveal 作品亮相', from: 510, dur: 240 },
  { id: 'S4', name: 'AI 三级降级', from: 750, dur: 420 },
  { id: 'S5', name: '功能快切', from: 1170, dur: 360 },
  { id: 'S6', name: '工程规模', from: 1530, dur: 150 },
  { id: 'S7', name: '收尾', from: 1680, dur: 120 },
] as const;
