# AI 节律守护 · 智能无感

> 从 Taro + React + NestJS + Supabase 全量转换为 **uni-app(Vue3) + Java(Spring Boot) + MySQL + Redis + 双 AI**

---

## 一、这是什么

一个**老年人健康守护**微信小程序。核心能力：

| 模块 | 功能 |
|---|---|
| **节律守护** | 作息打卡、睡眠评分、天气、周历、今日活动 |
| **居家守护** | SOS 紧急求助（长按 3 秒）、设备状态、告警 |
| **健康监测** | 语音/图像/文本三模态分析、跌倒检测 |
| **智能助手** | AI 健康对话（三层降级） |
| **个人中心** | 用户资料、家庭成员、用药提醒、健康报告 |

---

## 二、快速开始

### 前置要求

| 组件 | 版本 | 位置 | 开机自启 |
|---|---|---|---|
| JDK | 17+ | `C:\Users\msr150174\.jdks\openjdk-25` | — |
| MySQL | 8.0 | Windows 服务 `MYSQL80`，安装于 `D:\mysql` | 需手动开启一次 |
| Redis | 5.0+ | `D:\redis` | ✅ 已配置 |
| Node.js | 18+ | `D:\Program Files\nodejs` | — |

### 一条命令启动全部

只需双击一个文件：

```
转换-Java后端\guardian-server\start.bat
```

**它会自动完成 5 步**：

```
[1/3] MySQL      检查服务，没启动就启动
[2/3] Database   数据库不存在或表不全 → 自动建库建表（12 张）
[3/3] Redis      检查进程，没启动就启动
[4/5] Compile    编译 Java（--release 17 + -parameters）
[5/5] Backend    启动 Spring Boot，轮询等待就绪
```

**成功输出**：

```
============================================================
                   STARTED OK
============================================================

   status : UP
   mysql  : True  (12 tables)
   redis  : True
   ai     : rule  (fallback: rule)

  api    : http://localhost:3000/api
  stop   : run stop.bat
```

**不需要手动建库**——脚本会自己判断并初始化。

### 停止

```
转换-Java后端\guardian-server\stop.bat
```

会询问是否一并停止 Redis 和 MySQL（默认保留，推荐）。

### 首次使用：开启 MySQL 开机自启（**可选，但建议做一次**）

MySQL 服务默认是「手动」启动，重启电脑后不会自己起来。

**双击运行一次即可**（会自动请求管理员权限）：

```
转换-Java后端\guardian-server\enable-mysql-autostart.bat
```

**Redis 已配置好开机自启**（启动文件夹快捷方式），无需操作。

### 启动前端

```powershell
cd 转换-uniapp前端\guardian-uniapp
npm install
npm run dev:mp-weixin
```

**微信开发者工具导入**：`转换-uniapp前端\guardian-uniapp\dist\dev\mp-weixin`

### 登录

| 项 | 值 |
|---|---|
| 手机号 | 任意 11 位（如 `13800000001`） |
| 验证码 | **看后端控制台日志** |

开发模式下短信不真发，验证码打印在日志：

```
【开发模式】手机号 13800000001 的验证码为 268549（300 秒内有效）
```

> ⚠️ 验证码有 **60 秒**频率限制。

---

## 三、技术栈

### 后端

| 类别 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 17 |
| 框架 | Spring Boot | 3.2.5 |
| ORM | MyBatis | 3.5.7（注解式） |
| 数据库 | MySQL | 8.0 |
| 缓存 | Redis | 7 |
| 工具 | Lombok / Hutool | 1.18.48 / 5.8.27 |

**规模**：60 个 Java 文件，22 个 Controller，**153 个接口**

### 前端

| 类别 | 技术 | 版本 |
|---|---|---|
| 框架 | uni-app | 3.0 |
| 语言 | Vue 3 + TypeScript | 3.4 / 5.4 |
| 构建 | Vite | 5.2 |
| 状态 | Pinia | 2.1 |
| 样式 | SCSS | 1.72 |

**规模**：**36 个页面**，19,639 行

### 数据库

**12 张表**（从 PostgreSQL/Drizzle 转换）：

```
users                用户（长辈/家属）
checkin_records      作息打卡
devices              智能设备
alerts               健康告警
diet_records         饮食记录
elder_info           长辈健康档案
medicine_reminders   用药提醒
monitor_data         监测数据
exercise_records     运动记录
family_bindings      家属绑定
medicine_records     服药记录
health_check         探活
```

**PostgreSQL → MySQL 类型转换**：

| PG | MySQL |
|---|---|
| `serial` | `BIGINT AUTO_INCREMENT` |
| `jsonb` | `JSON` |
| `timestamp with time zone` | `DATETIME(3)` |
| `numeric(p,s)` | `DECIMAL(p+2,s)` |
| `gen_random_uuid()` | 应用层生成 |

---

## 四、双 AI 架构

### 三层降级（**永不报错**）

```
① Ollama（本地模型）  ← 最快最省钱，需本地部署
      ↓ 失败
② Coze（云端模型）    ← 能力强，需 Token
      ↓ 失败
③ 规则库（本地兜底）  ← 永远可用
```

**任何一层失败都自动降级，用户永远拿到回复。**

### 规则库（12 条规则）

移植自原项目 `generateOfflineResponse()`：

| # | 关键词 | 回复 |
|---|---|---|
| 1 | 你好/您好/嗨/hi | 自我介绍「小护」 |
| 2 | 健康/身体/体质 | 带健康评分 |
| 3 | 步数/运动/走路 | 带当日步数 |
| 4 | 睡眠/睡觉/失眠 | 带睡眠时长 |
| 5 | 提醒/服药/吃药 | 引导设置提醒 |
| 6 | 心情/情绪/压力 | 情绪陪伴 |
| 7 | 吃/饮食/饭 | 饮食建议 |
| 8 | 位置/在哪 | 位置查询 |
| 9 | 紧急/求救/sos | SOS 引导 |
| 10 | 家人/家属/绑定 | 家人绑定引导 |
| 11 | 谢谢/感谢 | 礼貌回应 |
| 12 | 再见/晚安 | 告别 |

### 配置 AI

编辑 `application.yml`：

```yaml
guardian:
  ai:
    ollama:
      enabled: true                          # 开启本地模型
      base-url: http://localhost:11434
      model: qwen2.5:7b
    coze:
      enabled: true
      api-token: ${COZE_API_TOKEN:}          # ★ 从环境变量读，不写死
      bot-id: ${COZE_BOT_ID:}
```

**环境变量**（不要提交到版本库）：

```powershell
$env:COZE_API_TOKEN = "pat_你的新Token"
$env:COZE_BOT_ID = "你的BotID"
```

---

## 五、🔴 安全说明

### 原项目的严重问题

**发现 5 处密钥泄露**：

| 类别 | 数量 | 原位置 |
|---|---|---|
| Coze Token | **3 个** | `server/.env` + `coze.config.ts` + `ai-chat/index.tsx` |
| 高德 Key | 2 个 | `server/.env` |

**根因**：密钥放在了**前端**（编译进小程序包，反编译即可获取）。

### 新版的处理

```
❌ 原架构
   小程序 ──(带 Token)──> Coze API
                ↑ Token 可被提取

✅ 新架构
   小程序 ──> Java 后端 ──(带 Token)──> Coze API
              （无密钥）    （密钥存环境变量）
```

### 你仍需做的事

| # | 事项 |
|---|---|
| 1 | **吊销那 5 个泄露的密钥** |
| 2 | 生成新 Token，只放在后端环境变量 |
| 3 | 注册真实小程序 AppID（现在是空的） |
| 4 | 配置服务器域名白名单（上线时） |

**详细清单**：`docs\安全审计报告.md`

---

## 六、目录结构

```
D:\msr150174\项目\智能无感\
├── 转换-Java后端\guardian-server\    Java 后端（60 文件，153 接口）
│   ├── src\main\java\com\guardian\
│   │   ├── controller\   22 个控制器
│   │   ├── service\       6 个服务
│   │   ├── mapper\       11 个数据访问
│   │   ├── entity\       11 个实体
│   │   ├── ai\            双 AI（AiService + LocalRuleProvider）
│   │   ├── common\        响应信封/异常/工具
│   │   └── config\        Redis/CORS
│   ├── src\main\resources\application.yml
│   ├── start.bat          一键启动
│   ├── stop.bat           停止
│   └── build-helper.ps1   编译辅助
│
├── 转换-uniapp前端\guardian-uniapp\  uni-app 前端（36 页面，19,639 行）
│   ├── src\pages\        36 个页面
│   ├── src\services\     接口层（request + api，覆盖 153 接口）
│   ├── src\stores\       状态管理
│   ├── src\utils\        工具函数
│   ├── src\styles\       设计令牌
│   └── src\pages.json    路由 + tabBar
│
├── db\V1__init_schema.sql   12 张表建表脚本
├── docs\                    8 份文档
├── VTJ设计源码\              12 页设计源码（可导入 VTJ）
└── dist\                    原项目小程序产物
```

---

## 七、已验证项

| 验证 | 结果 |
|---|---|
| MySQL 建表 | ✅ 12 张表创建成功 |
| 后端编译 | ✅ 退出码 0，67 个 class |
| 后端启动 | ✅ `status=UP`，redis 连通 |
| **接口总数** | ✅ **153 个**（目标 136） |
| **契约覆盖** | ✅ **130/130 全部实现** |
| **接口冒烟测试** | ✅ **25/25 通过** |
| **端到端联调** | ✅ **17/17 模块全部通过** |
| **前端构建** | ✅ **36 页全部编译，158 个产物文件** |
| AI 规则库 | ✅ 8/8 关键词正确命中 |

### 端到端验证覆盖

```
[1]  登录/注册      [2]  作息打卡      [3]  今日状态
[4]  健康评分      [5]  运动记录      [6]  饮食记录
[7]  AI 对话×3     [8]  SOS 求救      [9]  告警列表
[10] 统计概览      [11] 健康监测      [12] 提交反馈
[13] 天气          [14] 设备列表      [15] 安全区检查
[16] 守护汇总      [17] 风险评估
```

### 运行时抽样（17/17 通过）

```
打卡 / 健康评分 / 守护汇总 / 风险评估 / 联系人
AI 对话 / AI 增强 / AI 分析 / 统计概览 / 天气
运动 / 饮食 / 用药 / 监测 / 设备 / 告警 / 位置安全区
```

---

## 八、修正的原项目缺陷

转换过程中修正了 **14 处**原项目问题：

| # | 原问题 | 修正 |
|---|---|---|
| 1 | `monitor` 跌倒检测 `handleEmergencySOS()` 写在 `if (detected)` **块外** → **每次都弹 SOS** | 移进块内 |
| 2 | `home` 睡眠评分环右侧三行是**硬编码假数据** | 改为真实计算 |
| 3 | `monitor` 有整套大字模式 CSS **但未挂载** | 挂载 + 开关 |
| 4 | `guardian` SOS 界面写「3秒」但代码实际 **1秒** | 按 3 秒实现 |
| 5 | `guardian` 设备/老人状态是**死值** | 从真实数据读 |
| 6 | 配色不统一（蓝/绿混用） | 统一暖米绿系 |
| 7 | 电话号码硬编码 | 从数据读 |
| 8 | catch 分支用闭包旧值 | 取最新值 |
| 9 | 统计口径不一致 → **可能算出负数** | 统一口径 + 保护 |
| 10 | `healthScore` 恒为 null | 真实计算 |
| 11 | 账号注销仅 toast | 明确提示「开发中」 |
| 12 | 设置项不持久化 | localStorage 持久化 |
| 13 | 5 处密钥泄露 | 改为后端中转 |
| 14 | `userId: "user_001"` 写死 | 按真实用户隔离 |

---

## 九、开发中踩到的坑（供参考）

### 后端

| 坑 | 现象 | 解法 |
|---|---|---|
| **MyBatis script 内 XML 转义** | `<script>` 内的 SQL 被当 XML 解析 | 块内比较符用 `&lt;`，**块外必须用真实 `<`** |
| **javac 中文输出** | `grep "error:"` **永远匹配不到** | 看**退出码**，不要 grep 文本 |
| **`Set-Content -Encoding UTF8` 加 BOM** | javac 报 `非法字符: '\ufeff'` | 用 `UTF8Encoding($false)` |
| **javac argfile 反斜杠转义** | `-d "D:\...\classes"` 解析失败 | argfile 内路径用**正斜杠** |
| **批处理必须 CRLF** | LF 换行导致 cmd 解析 `set` 失败 | 转 CRLF |
| **PowerShell 5.1 读中文脚本乱码** | 脚本执行异常 | 辅助脚本用**纯 ASCII** |

### 前端

| 坑 | 解法 |
|---|---|
| `URLSearchParams` 小程序不支持 | 手写查询串拼接 |
| `uni-app` 每次编译重置 `urlCheck` | vite 插件自动写回 |
| 小程序包反编译可提取密钥 | **密钥绝不放前端** |

---

## 十、下一步

| 优先级 | 事项 |
|---|---|
| 🔴 **P0** | **吊销泄露的 5 个密钥** |
| 🔴 P0 | 注册真实小程序 AppID |
| 🟡 P1 | 本地部署 Ollama（启用本地 AI） |
| 🟡 P1 | 配置 Coze Token（启用云端 AI） |
| 🟡 P2 | 补齐 ASR/TTS 语音能力 |
| 🟢 P3 | 接入真实天气 API |
| 🟢 P3 | 微信支付（SOS 服务等付费能力） |

---

## 十一、与智跑校园的一致性

本项目与「智跑校园」保持**完全相同的技术栈**：

| 维度 | 智跑校园 | 智能无感 |
|---|---|---|
| 前端 | uni-app + Vue 3 | **同** |
| 后端 | Spring Boot 3.2.5 | **同** |
| ORM | MyBatis 注解式 | **同** |
| 数据库 | MySQL 8 | **同** |
| 缓存 | Redis | **同** |
| AI | 双 AI + 规则库 | **同** |
| 启动 | start.bat 一键 | **同** |

**两个项目的运维方式、代码风格、部署流程完全一致。**
