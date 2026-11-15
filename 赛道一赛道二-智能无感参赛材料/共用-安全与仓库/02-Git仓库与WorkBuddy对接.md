# Git 仓库与 WorkBuddy 云端对接

> 目标：把 `智能无感` 纳入 Git 管理，并与 WorkBuddy 云端仓库对接。
> 前置：**先完成 `01-密钥安全处理.md`**，否则可能把密钥推到公网。

---

## 一、当前状态

| 项 | 状态 |
|---|---|
| `D:\msr150174\项目\智能无感` | ❌ **不是 Git 仓库**（无 `.git`） |
| `.gitignore` | ✅ 已创建（本次） |
| 密钥脱敏 | ✅ 已完成（本次） |
| 历史压缩包 | `project_20261014_084912.tar.gz`（39.5MB）等，已加入忽略 |

> 💡 **好消息**：因为还不是 Git 仓库，**没有历史泄露问题**。
> 现在建立仓库，第一次提交就是干净的。

---

## 二、建议的仓库结构

### 关键决策：一个仓库还是分开？

| 方案 | 说明 | 建议 |
|---|---|---|
| **单仓多模块**（monorepo） | 后端 + 前端 + 后台管理 + 文档放一个仓库 | ✅ **推荐** |
| 三个独立仓库 | 后端仓、前端仓、后台仓 | ❌ 评审要读三个仓库，体验差 |

赛道二要求交**一个 ZIP**，赛道一要求**一个仓库链接**。单仓最省事。

### 目录规划

```
intelligent-guardian/                  ← 仓库根
├── README.md                          ← 必须，含评审账号
├── .gitignore
├── .env.example                       ← 只在后端目录放一份
├── docs/
│   ├── 参赛文档/
│   │   ├── 赛道二-作品说明文档.pdf
│   │   ├── 赛道一-技术文档.pdf
│   │   └── 创新点说明.md
│   ├── API契约.md
│   ├── 架构图.png
│   ├── 安全审计报告.md
│   └── 页面分析-*.md
├── backend/                           ← 原 转换-Java后端/guardian-server
│   ├── pom.xml
│   ├── .env.example
│   ├── start.bat / stop.bat
│   └── src/
├── frontend/                          ← 原 转换-uniapp前端/guardian-uniapp
│   ├── package.json
│   └── src/
├── admin/                             ← 原 智能无感-后台管理-Vue项目/guardian-admin
│   ├── package.json
│   └── src/
├── db/
│   └── V1__init_schema.sql
└── materials/
    ├── 体验二维码.png
    ├── 截图/
    └── 演示视频.mp4                   ← 或用 Release 附件
```

> ⚠️ **重命名目录要谨慎**：`start.bat` 等脚本里可能有相对路径，改名后要测一次能否启动。
> 若时间紧，**保持原目录名也能参赛**，只是不够整洁。

---

## 三、建立 Git 仓库的步骤

### 步骤 1：确认密钥已清理（**不可跳过**）

```powershell
cd "D:\msr150174\项目\智能无感"

# 扫描真实密钥
Get-ChildItem -Recurse -File -Include *.yml,*.yaml,*.properties,*.env*,*.bat,*.ts,*.vue,*.java,*.md |
  Where-Object { $_.FullName -notmatch 'node_modules|target|_解压预览|\\dist\\' } |
  Select-String -Pattern 'pat_[A-Za-z0-9]{30,}|LQBBZ-IVMYZ|65310f1244e78ae9'

# 无输出 = 安全
```

### 步骤 2：初始化

```powershell
cd "D:\msr150174\项目\智能无感"
git init -b main
git add -A
git status --short | Select-Object -First 30    # 检查没有不该提交的
```

**检查重点**：`git status` 输出里**不应出现**：

- `.env`（非 example）
- `node_modules/`
- `dist/`
- `target/`
- `*.tar.gz`
- `_解压预览/`

### 步骤 3：首次提交

```powershell
git commit -m "chore: 初始化 AI 节律守护项目

- Java 后端（Spring Boot 3.2.5 + MyBatis + MySQL + Redis）
- uni-app 前端（Vue 3 + TS，36 页面）
- Vue 后台管理
- 数据库初始化脚本
- 安全：移除硬编码密钥，改为环境变量注入"
```

### 步骤 4：对接 WorkBuddy 云端

WorkBuddy 的仓库对接方式取决于其具体配置。通用流程：

```powershell
# 方式 A：如果 WorkBuddy 已创建了远端仓库
git remote add origin <workbuddy 提供的仓库地址>
git push -u origin main

# 方式 B：先推到 GitHub，再由 WorkBuddy 关联
git remote add origin https://github.com/<你的账号>/<仓库名>.git
git push -u origin main
```

> 📌 **需要你确认**：WorkBuddy 的云端仓库地址和认证方式（HTTPS + Token？SSH Key？）。
> 告诉我后我可以帮你配置。

---

## 四、与赛道四仓库的关系

你目前有两个项目要管：

| 项目 | 仓库 | 赛道 |
|---|---|---|
| `传智杯`（文档 + 科研 Agent 方案） | `github.com/msr150174/ChuanzhiCup` | 赛道三、四 |
| `智能无感` | 待建 | 赛道一、二 |

**建议分开两个仓库**，理由：

1. 赛道二要求交**一个 ZIP**，独立仓库打包更干净
2. 赛道一要求**仓库 Public**，两个作品混在一起会混淆评委
3. 评审看的是"一个完整作品"，不是"一个大杂烩"

---

## 五、README 模板（**评审第一眼看的东西**）

```markdown
# AI 节律守护 · 智能无感

> 面向独居老人的 AI 健康守护智能体小程序

## 项目简介

（2-3 句话说清：解决什么问题、给谁用、怎么解决）

## 核心功能

| 模块 | 功能 |
|---|---|
| 节律守护 | 作息打卡、睡眠评分、天气、周历 |
| 居家守护 | SOS 紧急求助、设备状态、告警 |
| 健康监测 | 语音/图像/文本三模态分析、**跌倒检测** |
| 智能助手 | AI 健康对话（三层降级） |
| 个人中心 | 资料、家庭成员、用药提醒、健康报告 |

## 技术架构

（架构图）

### 三层 AI 降级设计

```
① Ollama 本地模型  →  ② Coze 云端模型  →  ③ 规则库兜底
```

任何一层失败自动降级，**用户永远能拿到回复**。

## 快速开始

### 前置要求
- JDK 17+、MySQL 8.0、Redis 5.0+、Node.js 18+

### 启动

```bash
# 1. 配置环境变量
cd backend
cp .env.example .env
# 填入：COZE_API_TOKEN、WX_APP_ID、WX_APP_SECRET、JWT_SECRET 等

# 2. 启动后端（自动检测并创建数据库）
start.bat

# 3. 启动前端
cd frontend
npm install
npm run dev:mp-weixin
# 微信开发者工具导入 dist/dev/mp-weixin
```

## 评审演示账号

- 手机号：`13800000001`
- 验证码：见后端控制台日志（开发模式打印）
- 体验二维码：`materials/体验二维码.png`

## 团队信息

- 学校：
- 成员：
- 组别：

## 许可证

（如有）
```

---

## 六、检查清单

- [ ] 密钥扫描无输出
- [ ] `git status` 中无 `.env` / `node_modules` / `dist` / `target` / `*.tar.gz`
- [ ] `.gitignore` 生效（`git check-ignore -v dist` 有输出）
- [ ] 首次提交信息清晰
- [ ] 远端仓库已配置（WorkBuddy 或 GitHub）
- [ ] 推送成功，用**无痕窗口**能打开仓库
- [ ] README 含评审账号与启动方式
