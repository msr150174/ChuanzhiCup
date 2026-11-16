# AI 节律守护 · 智能无感 —— 运营管理 Web 端

> 第九届传智杯 · 赛道一「AI WEB 网页开发」参赛作品
> 本 README 面向评审与部署者，内容以仓库实际源码为准。

---

## 一、项目介绍

面向独居/空巢老人健康守护场景的**运营管理 Web 工作台**。系统由三端组成：

- **运营管理 Web 端（本仓库主体）**：Vue 3 + TypeScript + Element Plus，7 个页面，负责数据总览、告警处置、用户与设备管理、反馈回复、系统监控。
- **微信小程序端**：uni-app + Vue 3，36 个页面，供老人打卡、SOS 求助、AI 对话与家属远程查看。
- **服务端**：Spring Boot 3.2.5 + MyBatis + MySQL 8 + Redis，25 个 Controller / 184 个方法级端点 / 12 张表；AI 能力采用 Ollama → Coze → 本地规则库三层降级编排。

**Web 端与 AI 的关系**：Web 管理端不直接调用大模型，AI 推理集中在后端 `AiService`；管理端通过 `/admin/*` 接口消费 AI 与业务规则产生的告警、统计与反馈，并完成人工处置闭环。详见《01-技术文档-七章.md》第 3–5 章。

### 已实现能力边界

| 已实现 | 未实现 / 占位（不计入交付功能） |
|---|---|
| 三层降级 AI 编排、Redis 响应缓存、健康上下文注入、`provider` 可观测 | RAG / 向量检索 / 知识库 |
| 腾讯云通用 OCR（文字识别）、腾讯位置服务 | 视觉大模型、跌倒姿态识别（CV） |
| 后端密钥中转、前端零密钥 | ASR 语音转写、TTS 语音合成 |
| 7 个管理页面、路由守卫、ECharts 趋势、字段适配层 | 微信支付、订阅消息、云开发 |
| 12 条本地规则库兜底 | 真实天气数据（当前 `mock:true`） |

> 默认配置下 `COZE_API_TOKEN` / `COZE_BOT_ID` 为空且 Ollama 关闭，AI 对话实际由规则库提供；演示前请按下文注入配置。

---

## 二、技术架构

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5、vue-router 4、Pinia 2.2、Element Plus 2.14、ECharts 6.1、axios 1.20 |
| 构建 | Vite 8.3、TypeScript 6.0、`vue-tsc -b && vite build` |
| 后端 | Spring Boot 3.2.5、Java 17、MyBatis 3.0.3、Hutool 5.8.27、HttpClient5 |
| 数据 | MySQL 8（库 `znwg`，12 张表）、Redis（验证码 / token / AI 缓存） |
| AI | Ollama `qwen2.5:7b` → Coze `/v3/chat`（流式 SSE）→ 本地规则库（12 条） |
| 第三方 | 腾讯云通用 OCR SDK、腾讯位置服务 HTTP API |

架构图见 `assets/系统架构图.svg`。

**鉴权说明**：实际机制为 Redis 存储的随机 UUID token（7 天有效），`requireUser` / `requireAdmin` 校验；**不是 JWT**。配置文件中残留的 `guardian.jwt.*` 未被代码使用。

---

## 三、安装与运行

### 环境要求

| 组件 | 版本 |
|---|---|
| JDK | 17（`pom.xml` 固定 `release=17`） |
| Node.js | ≥ 20 |
| MySQL | 8.x |
| Redis | 7.x |
| 可选 | Ollama 或 Coze Token / Bot ID |

### 启动后端

```bash
cd 转换-Java后端/guardian-server
cp .env.example .env       # 填入实际值；.env 已被 .gitignore 排除，切勿提交
# Windows：运行 start.bat（检查 MySQL/Redis → 建库建表 → 编译 → 启动）
# 或：mvn spring-boot:run
curl http://localhost:3000/api/health/ping
```

> `start.bat` 内 JDK / MySQL / Redis 为开发机绝对路径，换机器请修改。`application.yml` 中所有敏感项均为 `${ENV:}` 占位。

### 启动 Web 端

```bash
cd 智能无感-后台管理-Vue项目/guardian-admin
npm ci
npm run dev       # http://localhost:5177，/api 代理至 http://localhost:3000
```

生产构建：`npm run build`，产物 `dist/`；由 Nginx 托管静态资源并将 `/api` 反代至后端。

### 初始化数据

执行 `db/V1__init_schema.sql`，会创建 12 张表并写入两个演示用户：

| 手机号 | 角色 | 说明 |
|---|---|---|
| `13800000001` | 张爷爷 | 演示老人账号 |
| `13800000002` | 张小明 | 演示家属账号 |

---

## 四、评审演示路径

1. 打开 `http://localhost:5177` → 跳转登录页。
2. 输入演示手机号 → 获取验证码。**开发模式下验证码打印在后端控制台日志**（`AuthService` dev-mode），请以日志为准，不要使用任何文档里写死的验证码。
3. 进入「数据总览」→ 切换 7 / 14 / 30 天趋势。
4. 进入「告警处置」→ 按状态筛选 → 填写处理说明 → 提交，观察状态闭环。
5. 进入「用户管理」→ 查看详情 / 启用停用。
6. 进入「系统监控」→ 查看 MySQL / Redis / JVM 状态（30 秒自动刷新）。
7. 如需演示 AI 降级：在后端关闭 Ollama 或清空 Coze Token 后发起一次 AI 分析，观察返回 `provider=rule` 且无报错。

> 若在线演示需登录账号，请在此处补充**专用测试账号与口令**（由团队在赛前创建并填入）。当前 README 不提供写死的通用口令，避免与源码实际行为不符。

---

## 五、安全说明

- 前端产物与仓库中**不含任何 AI / 云服务密钥**；全部由后端环境变量注入。
- `.env.example` 为全空值模板；`.gitignore` 排除 `.env` / `.env.*`。
- 提交前请执行密钥检索：`git log -p | grep -E "pat_|sk-|AKIA"`，以及构建产物检索。
- `WebConfig` 当前为 `allowedOriginPatterns("*")`，生产部署应收紧至具体域名。

---

## 六、团队信息

> ⚠️ **待团队补齐后提交**（赛题要求 README 含团队信息）。

| 项 | 内容 |
|---|---|
| 学校 | 待填 |
| 省份 / 赛区 | 待填 |
| 组别 | 待填（A / B / C / 研究生，选错将取消资格） |
| 成员 1（姓名 / 专业 / 分工） | 待填 |
| 成员 2（姓名 / 专业 / 分工） | 待填 |
| 成员 3（姓名 / 专业 / 分工） | 待填 |
| 指导教师（≤2 名） | 待填 |

---

## 七、目录说明

```
参赛材料库/06-项目源码-智能无感/
├── 智能无感-后台管理-Vue项目/guardian-admin/   ← 赛道一提交主体
├── 转换-uniapp前端/guardian-uniapp/            ← 小程序端（赛道二主体）
├── 转换-Java后端/guardian-server/              ← 共用后端
├── db/                                        ← 建表脚本
├── docs/                                      ← API 契约、转换决策、安全审计
└── README.md                                  ← 项目总说明
```

## 八、许可与原创性

本作品为团队独立开发，借助 AI 辅助编程工具编写并经团队成员理解与验证；项目存在从 `ai-rhythm-guardian v3.0.0`（Taro + React）到当前版本的技术迭代，非往届参赛作品。迭代记录见 `docs/转换决策记录.md`、`docs/安全审计报告.md`。
