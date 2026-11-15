# AI 节律守护 · VTJ 设计源码转换方案

> 目标：把原 Taro+React 项目转成 **VTJ 可设计**的单文件源码
> 交付：`1.vue`（含 12 个页面）+ mock 数据，可直接导入 VTJ 继续设计

---

## 一、原项目信息

| 项 | 值 |
|---|---|
| 项目名 | ai-rhythm-guardian v3.0.0 |
| 技术栈 | Taro 4.1.9 + React 18 + Tailwind 4 + Zustand |
| 页面数 | 36（本次转 12 个常用页） |
| 后端 | NestJS + Supabase（PostgreSQL） |
| 接口数 | 136 |

---

## 二、设计系统（从 app.css 提取）

### 配色

```scss
/* 主色 —— 灰绿 */
--color-primary:              #8FA68E;
--color-primary-container:    #D4E0D0;   /* 浅绿容器 */
--color-on-primary:           #ffffff;
--color-on-primary-container: #2F3A2F;

/* 背景 —— 暖米色 */
--color-background:                #F4EFE6;
--color-surface:                   #FFF9EF;   /* 卡片 */
--color-surface-container-lowest:  #FFFFFF;
--color-surface-container:         #F0EBE0;
--color-surface-container-high:    #E8E3D8;

/* 文字 */
--color-on-surface:         #2F2A24;
--color-on-surface-variant: #7D746B;

/* 语义色 */
--color-success: #8FA68E;
--color-warning: #D4A574;
--color-error:   #B88466;
--color-danger:  #E55A5A;

/* 强调（tabBar 选中） */
--accent: #5B9A7A;
```

### 圆角

```scss
--radius-sm:  0.75rem;   /* 12px */
--radius-md:  1rem;      /* 16px */
--radius-lg:  1.25rem;   /* 20px */
--radius-xl:  1.5rem;    /* 24px */
--radius-2xl: 2rem;      /* 32px */
--radius-full: 9999px;
```

### 阴影

```scss
--shadow-card:   0 4px 16px rgba(143,166,142,0.12);
--shadow-float:  0 8px 24px rgba(143,166,142,0.16);
--shadow-dialog: 0 16px 48px rgba(143,166,142,0.20);
```

**风格定位**：温暖、柔和、适老化 —— 暖米底 + 低饱和灰绿，长时间看不疲劳。

---

## 三、12 个页面清单

| # | 页面 | 路由 | 作用 | 原规模 |
|---|---|---|---|---|
| 1 | 登录 | `login` | 手机号 + 验证码 | 394 行 |
| 2 | 注册 | `register` | 新用户注册 | 282 行 |
| 3 | **节律守护** | `home` | 打卡 + 天气 + 活动 | 1101 行 |
| 4 | **居家守护** | `guardian` | SOS + 围栏 + 状态 | 882 行 |
| 5 | **健康监测** | `monitor` | 语音/视频/手动录入 | 1256 行 |
| 6 | **智能助手** | `ai-chat` | AI 健康对话 | 997 行 |
| 7 | **个人中心** | `profile` | 用户信息 + 入口 | 797 行 |
| 8 | 守护面板 | `guardian-panel` | 家属视角总览 | 516 行 |
| 9 | 健康报告 | `health-report` | 周报 / 月报 | 864 行 |
| 10 | 家庭成员 | `family` | 绑定管理 | 696 行 |
| 11 | 用药提醒 | `medicine-reminder` | 提醒管理 | 518 行 |
| 12 | 设置 | `settings` | 系统设置 | 631 行 |

**加粗的 5 个是 tabBar 主页面。**

---

## 四、tabBar 结构

| 顺序 | 页面 | 文案 | 图标 |
|---|---|---|---|
| 1 | home | 节律守护 | moon |
| 2 | guardian | 居家守护 | shield |
| 3 | monitor | 健康监测 | heart-pulse |
| 4 | ai-chat | 智能助手 | message-circle |
| 5 | profile | 个人中心 | user |

**tabBar 配色**：
```
未选中: #7D746B
选中:   #8FA68E
背景:   #FFF9EF
```

---

## 五、交付格式

### 单文件 `1.vue`

按上次智跑校园的模式，把 12 个页面 SFC 依次排列在一个文件里：

```
<template>  ... 登录页  ...  </template>
<script setup lang="ts"> ... </script>
<style scoped> ... </style>

<template>  ... 注册页  ...  </template>
<script setup lang="ts"> ... </script>
<style scoped> ... </style>

... 共 12 组 ...
```

**导入 VTJ 后**，每个 `<template>` 块会被识别为一个独立页面。

### 数据用 mock

页面里的接口调用**先不接后端**，用本地 mock 数据：

```ts
const __state = reactive({
  // 打卡记录（mock）
  checkInRecords: [
    { date: '2026-06-20', sleepTime: '23:10', wakeTime: '06:40', score: 92 },
    { date: '2026-06-21', sleepTime: '22:45', wakeTime: '06:30', score: 95 }
  ],
  // 天气（mock）
  weather: { temp: 25, weather: '晴', humidity: 60, wind: '微风' },
  // 今日活动（mock）
  activity: { steps: 6820, distance: 4.8, calories: 265, outdoorTime: 45 }
})
```

**好处**：纯粹用来调界面，不会被后端拖住。

---

## 六、与原项目的差异说明

| 项 | 原项目 | VTJ 版 |
|---|---|---|
| 框架 | Taro + React | Vue 3 + Element Plus |
| 图标 | lucide-react-taro | emoji（后续可换） |
| 样式 | Tailwind + CSS 变量 | SCSS + CSS 变量（保留原配色） |
| 数据 | NestJS 接口 | **本地 mock** |
| 状态 | Zustand | Vue reactive |
| 页面数 | 36 | **12**（常用页） |

**保留的**：
- ✅ 完整配色系统（138 个 CSS 变量）
- ✅ 页面布局与交互逻辑
- ✅ tabBar 结构
- ✅ 业务规则（睡眠评分算法等）

---

## 七、业务规则（从源码提取）

### 睡眠评分算法

```ts
function calculateSleepScore(hours: number): number {
  if (hours >= 7 && hours <= 9) return Math.min(95 + (hours - 7) * 2, 100)  // 7-9h 最优
  if (hours >= 6 && hours < 7) return 70 + (hours - 6) * 20                  // 6-7h 一般
  if (hours > 9) return 80                                                   // 过长
  return Math.max(50, hours * 10)                                            // 不足
}
```

### 健康评分维度

| 维度 | 权重 |
|---|---|
| 作息规律 | 30% |
| 用药依从 | 25% |
| 运动量 | 20% |
| 饮食均衡 | 15% |
| 监测异常 | 10% |

### SOS 触发

```
长按 3 秒 → 倒计时 3 秒 → 触发
松开 → 取消
```

---

## 八、执行计划

| 阶段 | 内容 |
|---|---|
| 1 | 生成 `1.vue` 骨架 + 设计变量 + 5 个 tabBar 页 |
| 2 | 补 7 个常用页（登录/注册/面板/报告/家庭/用药/设置） |
| 3 | 校验语法 + 渲染测试 |
| 4 | 打包交付 |

---

## 九、交付物

```
D:\msr150174\项目\智能无感\VTJ设计源码\
├── 1.vue                     ★ 单文件（12 个页面）
├── README.md                 导入说明
└── 设计变量参考.scss          配色与圆角（备用）
```
