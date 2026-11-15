# Remotion 视频制作 · 安装与使用说明

> 环境已搭好并验证通过，本文档说明怎么用。
> 搭建时间：2026-11-16

---

## 一、已安装的东西

| 组件 | 版本 | 路径 | 状态 |
|---|---|---|---|
| **Node.js** | v24.12.0 | `D:\Program Files\nodejs` | 原有 ✅ |
| **pnpm** | 12.21.0 | `C:\Users\msr150174\AppData\Roaming\npm` | ✅ 本次安装 |
| **FFmpeg** | 9.0.2 full | WinGet Packages 目录 | ✅ 本次安装 |
| **Chrome** | 157.0.8103.79 | `C:\Program Files\Google\Chrome\Application` | ✅ 本次安装 |
| **Remotion** | 4.0.543 | 项目内 `node_modules` | ✅ 本次安装 |
| **剪映专业版** | 11.7.0 | — | 原有 ✅ |

### 验证命令

```powershell
ffmpeg -version          # 应有版本输出
pnpm --version           # 12.21.0
node --version           # v24.12.0
```

> ⚠️ 装完后**必须重开终端**才能认到新 PATH。

---

## 二、项目位置

```
D:\msr150174\项目\传智杯\视频制作\chuanzhicup-video\
├── package.json
├── tsconfig.json
├── remotion.config.ts          # 指定 Chrome 路径
├── src/
│   ├── index.ts                # 入口
│   ├── Root.tsx                # ★ 所有视频的注册处
│   └── compositions/
│       ├── Intro.tsx           # 片头（3秒）
│       ├── TechStats.tsx       # 技术数据动画（8秒）
│       └── Demo.tsx            # 演示片占位（15秒）
└── out/                        # 渲染输出
    ├── intro.mp4               # ✅ 已渲染，351KB
    ├── intro-frame60.png       # 验证截图
    ├── techstats-frame200.png
    └── techstats-frame220.png  # 修复后验证
```

---

## 三、怎么用

### 3.1 打开预览（Studio）

```powershell
cd "D:\msr150174\项目\传智杯\视频制作\chuanzhicup-video"
npx remotion studio --browser-executable="C:\Program Files\Google\Chrome\Application\chrome.exe"
```

浏览器打开 `http://localhost:3000`，左侧能看到 3 个视频，点开可实时预览、拖时间轴。

改代码保存后**自动热更新**，不用重启。

### 3.2 渲染成 MP4

```powershell
# 片头
npx remotion render Intro out/intro.mp4 --browser-executable="C:\Program Files\Google\Chrome\Application\chrome.exe"

# 技术数据动画
npx remotion render TechStats out/techstats.mp4 --browser-executable="C:\Program Files\Google\Chrome\Application\chrome.exe"

# 控制质量（crf 越小越清晰、文件越大，18-23 是合理区间）
npx remotion render Intro out/intro-hq.mp4 --crf 18 --browser-executable="C:\Program Files\Google\Chrome\Application\chrome.exe"
```

### 3.3 截单帧（做封面/检查）

```powershell
npx remotion still Intro out/cover.png --frame=60 --browser-executable="C:\Program Files\Google\Chrome\Application\chrome.exe"
```

---

## 四、已验证的渲染结果

| 项 | 规格 |
|---|---|
| 编码 | H.264 |
| 分辨率 | **1920×1080**（满足赛题 ≥1080p） |
| 帧率 | 30 fps |
| 时长 | 3.000 秒（90 帧） |
| 文件大小 | 351.9 KB |
| 中文渲染 | ✅ 正常（"Microsoft YaHei" 字体） |

---

## 五、三个组件的设计说明

### Intro.tsx —— 片头 3 秒

**设计依据**：赛题视频脚本明确要求「前 15 秒上价值，不要放 logo、不要放标题页」。

所以片头**直接抛问题**：

```
第 0-25 帧   独居老人在家摔倒，子女多久能知道？   ← 淡入上移
第 35-70 帧  长按 3 秒，家属立刻收到告警          ← 弹性放大
第 60-90 帧  底部渐变线展开
```

### TechStats.tsx —— 技术数据 8 秒

**数据全部来自项目实勘，不是编的**：

| 数字 | 含义 | 出处 |
|---|---|---|
| 153 | 后端接口 | `docs/API契约.md` |
| 36 | 前端页面 | `src/pages.json` |
| 12 | 数据库表 | `db/V1__init_schema.sql` |
| 14处 | 修复的原项目缺陷 | `docs/转换决策记录.md` |

数字用**缓动计数**（从 0 滚到目标值），比静态数字更有科技感。

后半段展示三层降级：
```
① Ollama 本地模型  →  ② Coze 云端模型  →  ③ 规则库兜底（绿色高亮 + 脉冲）
```

### Demo.tsx —— 演示片占位 15 秒

三段时间轴（各 5 秒）：
1. 品牌 + 定位
2. **【此处接入 OBS 录屏】** ← 留给你放真实操作录像
3. 团队信息（待填）

---

## 六、下一步：接入真实录屏

### 步骤

1. **用 OBS 录小程序操作**，存成 mp4
2. 把 mp4 放到 `chuanzhicup-video/public/` 目录（没有就新建）
3. 修改 `Demo.tsx`：

```tsx
import { OffthreadVideo, staticFile } from 'remotion';

<Sequence from={150} durationInFrames={150}>
  <OffthreadVideo
    src={staticFile('recording.mp4')}
    style={{ width: '100%', height: '100%', objectFit: 'contain' }}
  />
</Sequence>
```

4. 重新渲染

> 💡 **但更实际的建议**：录屏本身已经是完整视频了，用剪映把它和 Remotion 生成的片头/数据动画**拼接**起来，比在 Remotion 里编排更快。

**推荐工作流**：

```
Remotion 渲染  →  intro.mp4（3秒片头）
                  techstats.mp4（8秒数据）
        ↓
剪映导入三个文件：
    1. intro.mp4        （开头）
    2. OBS 录屏.mp4      （主体，5-6分钟）
    3. techstats.mp4    （技术亮点段）
        ↓
剪映：拼接 + 配音 + 自动字幕 + 导出
        ↓
最终视频.mp4（5-8分钟，1080p）
```

---

## 七、常见问题

### Q1: 渲染报错 "browserExecutable was specified as '\"'"

**原因**：用 `cmd /c` 调用时引号被吃掉。

**解决**：在 PowerShell 里直接运行，**不要套 cmd**，路径用双引号包住。

### Q2: 报 "unable to verify the first certificate"

**原因**：国内网络环境的 SSL 证书问题（`create-video` 脚手架会触发）。

**解决**：
```powershell
$env:NODE_OPTIONS = "--use-system-ca"
```
或手工建项目（本项目就是这么建的，已绕过）。

### Q3: 中文显示成方块

**解决**：组件里已指定字体：
```
fontFamily: '"Microsoft YaHei", "PingFang SC", sans-serif'
```
如果仍有问题，确认 Windows 装了微软雅黑。

### Q4: 渲染很慢

90 帧约 20 秒，正常。整片 8 分钟（14400 帧）约需 30-60 分钟。

**提速**：
```powershell
--concurrency=4     # 并发数（按 CPU 核心数设）
```

### Q5: 想改视频尺寸

在 `Root.tsx` 里改 `<Composition width height />`。

- 横屏 1080p：`1920×1080`（**参赛用这个**）
- 竖屏（小程序截图友好）：`1080×1920`

---

## 八、常用 API 速查

```tsx
import {
  useCurrentFrame,      // 当前帧号
  useVideoConfig,       // { fps, width, height, durationInFrames }
  interpolate,          // 数值映射
  spring,               // 弹性动画
  AbsoluteFill,         // 全屏容器
  Sequence,             // 时间轴切片：from / durationInFrames
  staticFile,           // 引用 public/ 资源
  OffthreadVideo,       // 嵌视频
  Img,                  // 嵌图片（自动等加载）
  Audio,                // 嵌音频
} from 'remotion';
```

**核心心法**：视频没有"状态"，每一帧都是纯粹由 `frame` 计算出来的画面。所以可重放、可精准定位、可批量改参数重渲染。

---

## 九、时间提醒

**距截止还有 2 天（11-16 → 11-18）。**

Remotion 已经能用了，但它**不是必需的**。优先级排序：

| 优先级 | 事项 |
|---|---|
| 🔴 P0 | 赛道二 20 张截图 |
| 🔴 P0 | 赛道二 20 项测试记录 |
| 🔴 P0 | 后台 P0 三项整改（等扣子） |
| 🟡 P1 | 演示视频录制（**用 OBS + 剪映就够**） |
| 🟢 P2 | 用 Remotion 做片头美化（已备好，随时可用） |

**片头已经做好并验证过了，直接用就行。** 别在 Remotion 上花太多时间——评委不会因为片头是程序化的就给高分，但会因为缺测试记录扣分。
