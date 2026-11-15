# 项目源码说明（给 AI 阅读用）

> **重要前提：这是一份「阅读用」的源码副本，不需要运行。**
>
> 为了让 AI 快速理解项目，本副本**已排除所有重依赖与构建产物**（`node_modules` 约 393MB、
> `target`、`dist`、旧压缩包等），保留了全部**源代码、配置、文档**。
>
> **不要把本目录当作可运行项目去启动。** 如果需要运行，请回到原始项目目录
> `D:\msr150174\项目\智能无感`。

---

## 一、这是什么项目

**AI 节律守护 · 智能无感** —— 面向独居老人的 AI 健康守护微信小程序。

| 项 | 内容 |
|---|---|
| 定位 | 老年人健康守护 + 家属远程监护 |
| 形态 | 微信小程序 + Spring Boot 后端 + Vue 后台管理 |
| 核心能力 | 三层 AI 降级、多模态分析、跌倒检测、SOS 告警 |
| 团队 | 3 人 |
| 参赛 | 传智杯 2026 赛道二（AI 微信小程序开发挑战赛） |

---

## 二、目录地图

```
06-项目源码-智能无感/
│
├── README.md                       ★ 项目总说明（先读这个）
├── 项目分析报告.md                   ★ 项目分析（含往届对比）
├── 源码恢复方案.md                    源码恢复记录
├── .gitignore
│
├── 转换-Java后端/guardian-server/    ★ 后端主体
│   ├── pom.xml                     ★ Maven 依赖定义
│   ├── .env.example                ★ 环境变量模板（已脱敏，无真实密钥）
│   ├── start.bat / stop.bat         一键启动脚本
│   ├── src/main/java/com/guardian/
│   │   ├── controller/             25 个 Controller（接口入口）
│   │   ├── service/                业务逻辑
│   │   ├── mapper/                 MyBatis 数据访问
│   │   ├── entity/                 数据实体
│   │   ├── ai/                     ★ AI 层（三层降级核心）
│   │   ├── common/                 公共组件
│   │   ├── config/                 配置（Redis/CORS）
│   │   ├── dto/                    数据传输对象
│   │   └── interceptor/            拦截器
│   └── src/main/resources/
│       └── application.yml         ★ 主配置（密钥已改为环境变量）
│
├── 转换-uniapp前端/guardian-uniapp/  ★ 小程序前端
│   ├── package.json                ★ 前端依赖定义
│   ├── project.config.json          微信小程序项目配置
│   ├── vite.config.ts
│   └── src/
│       ├── pages/                  ★ 36 个页面
│       ├── services/               接口层（api.ts + request.ts）
│       ├── stores/                 Pinia 状态管理
│       ├── utils/                  工具函数
│       ├── styles/                 设计令牌
│       └── pages.json              路由 + tabBar
│
├── 智能无感-后台管理-Vue项目/guardian-admin/   ★ 后台管理端
│   ├── package.json
│   └── src/
│       ├── views/                  9 个视图
│       ├── layouts/                布局
│       ├── router/                 路由
│       └── api/                    接口封装
│
├── db/
│   └── V1__init_schema.sql         ★ 12 张表建表脚本
│
├── docs/                           ★ 项目技术文档
│   ├── API契约.md                   ★ 153 个接口定义
│   ├── 转换决策记录.md               ★ 14 处缺陷分析与修正决策
│   ├── 安全审计报告.md                密钥泄露审计（已脱敏）
│   ├── VTJ转换方案.md                技术栈转换方案
│   ├── 页面分析-前5页.md              逐页分析（93KB）
│   ├── 页面分析-后7页.md              逐页分析（120KB）
│   └── AI规则库原文.ts / .txt        规则库源码与说明
│
├── 文档/                            设计素材
│   ├── 智能无感-后台管理-原型规格.json
│   └── 智能无感-后台管理-设计稿元数据.json
│
├── VTJ设计源码/                      设计稿
│   ├── 智能无感-原型.json
│   ├── 1.vue / 2.vue
│   └── README.md
│
└── _临时脚本-非项目代码/              31 个开发期辅助脚本（与业务无关，可忽略）
```

---

## 三、★ 依赖清单（**重点：本副本未包含依赖包，此处列出实际使用**）

### 3.1 后端 · Maven 依赖

**基础环境**

| 项 | 版本 | 说明 |
|---|---|---|
| JDK | **17** | `pom.xml` 中 `<java.version>17</java.version>` |
| Spring Boot | **3.2.5** | `spring-boot-starter-parent` |
| 构建工具 | Maven | |

**直接依赖**

| 依赖 | 版本 | 用途 |
|---|---|---|
| `spring-boot-starter-web` | 继承 3.2.5 | Web 框架（内嵌 Tomcat） |
| `spring-boot-starter-validation` | 继承 3.2.5 | 参数校验 |
| `spring-boot-starter-data-redis` | 继承 3.2.5 | Redis 缓存（AI 响应缓存、会话） |
| `mybatis-spring-boot-starter` | **3.0.3** | ORM（注解式，非 XML） |
| `mysql-connector-j` | 继承 3.2.5 | MySQL 驱动 |
| `lombok` | **1.18.48** | 简化 POJO（@Data / @Slf4j） |
| `hutool-all` | **5.8.27** | 工具库（JSON / StrUtil / 加密） |
| `httpclient5` | 继承 3.2.5 | HTTP 客户端（调 Ollama / Coze） |
| `tencentcloud-sdk-java-ocr` | **3.2.18** | 腾讯云 OCR（图像识别） |
| `tencentcloud-sdk-java-sms` | **3.2.18** | 腾讯云短信（验证码） |
| `spring-boot-starter-test` | 继承 3.2.5 | 测试 |

**外部服务依赖（非 Maven 包）**

| 服务 | 版本要求 | 用途 | 是否必需 |
|---|---|---|---|
| **MySQL** | **8.0** | 主数据库（12 张表） | ✅ 必需 |
| **Redis** | **5.0+** | 缓存、会话 | ✅ 必需 |
| **Ollama** | 可选 | 本地大模型（qwen2.5:7b） | ⭕ AI 第一层 |
| **Coze** | 可选 | 云端智能体（需 Token） | ⭕ AI 第二层 |

> 💡 AI 三层降级：Ollama → Coze → 规则库。前两层都可缺失，规则库永远可用。

---

### 3.2 前端 · uni-app 依赖（小程序）

**基础环境**

| 项 | 版本 |
|---|---|
| Node.js | **18+** |
| uni-app | **3.0.0-4020920240930001** |
| Vue | **^3.4.21** |
| TypeScript | **^5.4.3** |
| Vite | **^5.2.6** |

**dependencies**

| 包 | 版本 | 用途 |
|---|---|---|
| `@dcloudio/uni-app` | 3.0.0-4020920240930001 | uni-app 核心 |
| `@dcloudio/uni-components` | 同上 | 内置组件 |
| `@dcloudio/uni-h5` | 同上 | H5 平台支持 |
| `@dcloudio/uni-mp-weixin` | 同上 | **微信小程序平台** |
| `pinia` | ^2.1.7 | 状态管理 |
| `vue` | ^3.4.21 | Vue 3 |

**devDependencies**

| 包 | 版本 | 用途 |
|---|---|---|
| `@dcloudio/types` | ^3.4.8 | uni-app 类型定义 |
| `@dcloudio/uni-automator` | 3.0.0-... | 自动化测试 |
| `@dcloudio/uni-cli-shared` | 3.0.0-... | CLI 共享 |
| `@dcloudio/uni-stacktracey` | 3.0.0-... | 错误堆栈映射 |
| `@dcloudio/vite-plugin-uni` | 3.0.0-... | Vite 插件 |
| `@types/node` | ^20.11.30 | Node 类型 |
| `@vue/runtime-core` | ^3.4.21 | Vue 运行时类型 |
| `sass` | ^1.72.0 | SCSS 编译 |
| `typescript` | ^5.4.3 | TS 编译 |
| `vite` | ^5.2.6 | 构建工具 |

**npm scripts**

| 命令 | 作用 |
|---|---|
| `npm run dev:mp-weixin` | 微信小程序开发（watch） |
| `npm run build:mp-weixin` | 微信小程序生产构建 |
| `npm run dev:h5` | H5 开发（可用于赛道一） |
| `npm run build:h5` | H5 构建 |

> ⚠️ **未包含**：`node_modules`（175MB / 18073 文件）
> 需要时执行 `npm install` 重新安装（依赖由 `package-lock.json` 锁定）。

---

### 3.3 后台管理 · Vue 依赖

**基础环境**

| 项 | 版本 |
|---|---|
| Vue | **^3.5.42** |
| TypeScript | **~6.0.2** |
| Vite | **^8.3.0** |

**dependencies**

| 包 | 版本 | 用途 |
|---|---|---|
| `vue` | ^3.5.42 | Vue 3 |
| `vue-router` | 4 | 路由 |
| `pinia` | ^2.2.6 | 状态管理 |
| `element-plus` | ^2.14.7 | UI 组件库 |
| `@element-plus/icons-vue` | ^2.3.2 | 图标 |
| `axios` | ^1.20.0 | HTTP 请求 |
| `echarts` | ^6.1.0 | 图表（Dashboard 数据看板） |

**devDependencies**

| 包 | 版本 |
|---|---|
| `@vitejs/plugin-vue` | ^6.0.8 |
| `@vue/tsconfig` | ^0.9.1 |
| `vue-tsc` | ^3.3.11 |
| `typescript` | ~6.0.2 |
| `vite` | ^8.3.0 |
| `sass` | ^1.105.1 |
| `@types/node` | ^24.13.3 |

---

### 3.4 Python 依赖（辅助脚本）

根目录与 `_临时脚本-非项目代码/` 下有若干 `.py` 脚本，为开发期辅助工具，**非项目运行必需**。

若需要运行这些脚本，可能用到：

| 包 | 用途 |
|---|---|
| `requests` | HTTP 请求（接口联调） |
| `python-docx` | 生成 .docx 文档 |
| `openpyxl` | 读写 Excel |

> 这些脚本与业务逻辑无关，AI 阅读时可**直接忽略 `_临时脚本-非项目代码/` 整个目录**。

---

## 四、★ 核心代码位置（AI 优先读这些）

### 4.1 AI 三层降级（本项目最大亮点）

| 文件 | 内容 |
|---|---|
| `转换-Java后端/guardian-server/src/main/java/com/guardian/ai/AiService.java` | **三层降级编排**（Ollama → Coze → 规则库），14.9KB |
| `转换-Java后端/guardian-server/src/main/java/com/guardian/ai/LocalRuleProvider.java` | **12 条规则库兜底**，9.2KB |
| `转换-Java后端/guardian-server/src/main/java/com/guardian/controller/AiController.java` | AI 接口（7 个） |
| `转换-Java后端/guardian-server/src/main/java/com/guardian/controller/AiExtraController.java` | AI 扩展接口 |

### 4.2 多模态与安全

| 文件 | 内容 |
|---|---|
| `转换-Java后端/.../controller/MonitorController.java` | **跌倒检测、语音/图像/视频分析**（43.4KB，最大 Controller） |
| `转换-Java后端/.../controller/GuardianController.java` | 守护逻辑、SOS |
| `转换-Java后端/.../controller/AlertController.java` | 告警 |

### 4.3 前端关键文件

| 文件 | 内容 |
|---|---|
| `转换-uniapp前端/guardian-uniapp/src/pages.json` | 36 个页面路由 |
| `转换-uniapp前端/guardian-uniapp/src/services/api.ts` | 130 个接口封装（22.6KB） |
| `转换-uniapp前端/guardian-uniapp/src/services/request.ts` | 请求拦截、JWT 注入 |
| `转换-uniapp前端/guardian-uniapp/src/pages/ai-chat/index.vue` | AI 对话页 |
| `转换-uniapp前端/guardian-uniapp/src/pages/guardian/index.vue` | SOS 长按（3 秒） |

### 4.4 必须读的文档

| 文件 | 为什么重要 |
|---|---|
| `README.md` | 项目全貌、启动方式、修正的 14 处缺陷 |
| `docs/API契约.md` | 153 个接口定义，写文档 d 项必用 |
| `docs/转换决策记录.md` | 14 处缺陷的分析与修正决策 |
| `docs/安全审计报告.md` | 5 类密钥泄露审计（已脱敏） |
| `db/V1__init_schema.sql` | 12 张表结构 |

---

## 五、未包含的内容（及如何补回）

| 未包含 | 原因 | 如需恢复 |
|---|---|---|
| `node_modules/`（前端 175MB / 18073 文件） | 体积大、AI 无需阅读 | `npm install` |
| `node_modules/`（后台 218MB / 14353 文件） | 同上 | 同上 |
| `target/`（Java 编译产物） | 构建生成 | `mvn compile` |
| `dist/`（小程序构建产物） | 含旧编译代码 | `npm run build:mp-weixin` |
| `_解压预览/`（50MB） | 旧项目解压副本 | 无需 |
| `*.tar.gz`（历史打包 39MB） | 压缩包 | 无需 |
| `.env`（真实环境变量） | **安全问题，绝不含密钥** | 参照 `.env.example` |
| `.git/`、`.idea/` | 版本控制与 IDE 配置 | 无需 |

> ⚠️ **`.env.example` 中所有值为空占位符**，真实密钥在本项目的原始目录中，
> 不在本副本内。这是刻意的安全设计。

---

## 六、给 AI 的阅读建议

### 如果目标是「写参赛文档」
```
1. README.md                    → 项目全貌
2. docs/API契约.md              → 接口清单（写 d 项用）
3. docs/转换决策记录.md          → 14 处缺陷（写 f 项用）
4. ai/AiService.java            → 三层降级（写 b/d 项用）
5. controller/MonitorController.java → 多模态能力（写 b 项用）
```

### 如果目标是「理解架构」
```
1. application.yml              → 配置结构
2. db/V1__init_schema.sql       → 数据模型
3. controller/ 目录浏览          → 25 个 Controller 的职责
4. src/pages.json               → 前端 36 页结构
5. src/services/api.ts          → 前后端契约
```

### 可直接忽略的
- `_临时脚本-非项目代码/`（31 个脚本，与业务无关）
- `文档/`、`VTJ设计源码/`（设计素材，非代码）
- `package-lock.json`（依赖锁文件，除非排查版本冲突）

---

## 七、关键数字速查

| 指标 | 数值 |
|---|---|
| 后端代码 | 67 个 Java 文件 / **14,732 行** |
| 后端接口 | **153 个** |
| Controller 数量 | **25 个** |
| 前端页面 | **36 个** / 17,525 行 Vue |
| 数据库表 | **12 张** |
| AI 规则库 | **12 条** |
| 后台管理视图 | **9 个** |
| 修复的原项目缺陷 | **14 处** |
| 修复的密钥泄露 | **5 类** |
