import { remotionConfig } from 'remotion';

/**
 * Remotion 全局配置
 *
 * 定位 Chrome 可执行文件 —— 本机只装了 Chrome（Edge 兼容性有风险）。
 */
export const config = {
  // 显式指定 Chrome，避免 Remotion 找不到浏览器
  browserExecutable: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
};
