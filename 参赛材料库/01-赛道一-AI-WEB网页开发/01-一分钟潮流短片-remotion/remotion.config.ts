import { Config } from '@remotion/cli/config';

/**
 * Remotion 全局配置
 * 本机只装了 Chrome，显式指定可执行文件，避免 Remotion 找不到浏览器。
 */
Config.setBrowserExecutable('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe');
Config.setVideoImageFormat('jpeg');
Config.setOverwriteOutput(true);
Config.setConcurrency(4);
