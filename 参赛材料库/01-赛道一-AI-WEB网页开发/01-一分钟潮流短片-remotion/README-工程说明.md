# 60 秒潮流短片 · Remotion 工程说明

> 作品：AI 节律守护 · 智能无感 —— 运营管理 Web 端
> 赛事：第九届传智杯 · 赛道一 AI WEB 网页开发
> 规格：1920×1080 / 30fps / 1800 帧 / 60 秒 / 无音频

## 一、这是什么

这是一支**纯代码生成的 60 秒潮流科技短片**，用于赛道一作品的路演开场、答辩片头或社交传播。
画面全部由 Remotion（React）逐帧渲染，不含实拍素材、不含 AI 生成视频，可随时改文案、改数字、改配色后重新渲染。

分镜与文案见同目录 `00-分镜脚本-60秒潮流版.md`。

## 二、目录结构

```
01-一分钟潮流短片-remotion/
├─ 00-分镜脚本-60秒潮流版.md   # 逐镜脚本（唯一内容依据）
├─ README-工程说明.md
├─ package.json
├─ remotion.config.ts          # 指定本机 Chrome，输出 jpeg 帧
├─ tsconfig.json
├─ public/                     # 预留静态资源目录（本片未使用外部素材）
├─ out/                        # 渲染产物
└─ src/
   ├─ index.ts                 # registerRoot
   ├─ Root.tsx                 # Composition 注册（id = Teaser）
   ├─ Video.tsx                # 60 秒主时间线（7 个 Sequence）
   ├─ theme.ts                 # 配色 / 字体 / 缓动 / 场景时间轴
   ├─ components/
   │  ├─ Chrome.tsx            # Backdrop 背景、Caption 字幕、LightSweep、Flash、ProgressBar
   │  └─ ui.tsx                # Kicker、Headline、GlassCard、Tag、StatBig、Rule、FlowLine
   └─ scenes/
      ├─ S1Hook.tsx            # 0–8s   跌倒三连句
      ├─ S2Problem.tsx         # 8–17s  告警链条断裂
      ├─ S3Reveal.tsx          # 17–25s 作品亮相
      ├─ S4AiStack.tsx         # 25–39s 三级 AI 降级（核心）
      ├─ S5Features.tsx        # 39–51s 四大功能快切
      ├─ S6Scale.tsx           # 51–56s 工程规模数字
      └─ S7Finale.tsx          # 56–60s Slogan 收尾
```

## 三、怎么跑

```bash
# 1. 安装依赖（首次）
npm install

# 2. 预览（浏览器打开 Remotion Studio，可逐帧拖动）
npm run dev

# 3. 渲染成片
npx remotion render Teaser out/teaser-60s.mp4 --codec=h264 --crf=18 --pixel-format=yuv420p

# 4. 只导出某一帧检查排版
npx remotion still Teaser out/frame-300.png --frame=300
```

环境要求：Node.js ≥ 18；本机 Chrome 路径已在 `remotion.config.ts` 中固定为
`C:\Program Files\Google\Chrome\Application\chrome.exe`，换机器需改这一行。

## 四、改内容怎么改

| 想改什么 | 改哪里 |
|---|---|
| 文案、字幕 | 对应 `src/scenes/Sx*.tsx` 里的字符串 |
| 时间长度 | `src/Video.tsx` 的 `durationInFrames` + `src/theme.ts` 的 `SCENES` |
| 配色 | `src/theme.ts` 的 `C` |
| 字体 | `src/theme.ts` 的 `F` |
| 背景网格/光斑强度 | `<Backdrop intensity={} gridOpacity={} />` |
| S3 右侧圆环装饰 | `src/scenes/S3Reveal.tsx` 的「右侧视觉锚点」块 |

> 修改后必须保持「7 段总帧数 = 1800」，否则片尾进度条与收尾节奏会错位。

## 五、内容口径

片中所有数字与能力描述均取自 `../参赛材料-定稿-2026-11-16/01-技术文档-七章.md` 的源码实测口径：

- Web 端 7 页 / 8 路由；后端 67 个 Java 文件 / 17,565 行；25 个 Controller；184 个方法级接口；12 张表；小程序 36 页
- AI 链路：Redis 缓存（TTL 300s）→ Ollama `qwen2.5:7b` → Coze 云端（流式 SSE）→ 本地规则库 12 条
- **未出现** RAG、Agent 工作流、Function Calling、MCP、视觉跌倒识别、ASR/TTS 等未实现能力

修改文案时请沿用同一口径，避免与提交的技术文档、演示视频口播互相矛盾。

## 六、成片与验收

- 成片：`out/AI节律守护-智能无感-60s潮流版.mp4`
  1920×1080 / 30fps / 60.000s / 1800 帧 / H.264 / 约 11.4 MB / 无音轨
- 分段渲染：设备单次命令有 20 分钟上限，整片一次渲染会超时，因此改为两段渲染后无损拼接：

```bash
npx remotion render Teaser out/part1.mp4 --frames=0-899 --concurrency=8 --crf=18
npx remotion render Teaser out/part2.mp4 --frames=900-1799 --concurrency=8 --crf=18
# out/list.txt 内容：file 'part1.mp4' / file 'part2.mp4'
ffmpeg -f concat -safe 0 -i list.txt -c copy "AI节律守护-智能无感-60s潮流版.mp4"
```

- 抽帧验收覆盖：S1 三连句、S2 断链、S3 主标题与右侧圆环、S4 三级降级与 `provider = rule`、
  S5 四张功能卡、S6 五行数字终值（184 / 25 / 12 / 17,565 / 36）、S7 收尾；
  899→900 拼接点无断裂，末帧近纯黑（全局进度条在最后 14 帧淡出）。
- 全片无外部素材依赖，改完文案直接重渲即可；实测单帧成本约 0.2 秒（8 并发）。
