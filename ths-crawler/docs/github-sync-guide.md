---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/github-sync-guide.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782170652949
    ReservedCode2: ""
---
# GitHub 同步方案设计文档

> 创建时间：2026-06-22
> 关联项目：ths-crawler（项目ID: 7652034661715165474）

---

## 1. 方案选型背景

### 用户实际场景
- 经常在路途中通过**手机端**发起指令让Agent工作
- 产出代码需要即时落库到GitHub，不能等回到Mac前才操作
- Mac不总是在线，无法依赖本地Git操作
- 需要手机浏览器随时查看GitHub上的代码变更

### 两种方案对比

| 维度 | 方案A：Agent直接push（PAT） | 方案B：Agent打包，用户本地push |
|------|---------------------------|-------------------------------|
| 路途中手机发指令 | ✅ 一句话搞定 | ❌ 必须等回到Mac前 |
| 手机看代码 | ✅ GitHub随时看 | ❌ 码还在项目空间等着 |
| 多设备同步 | ✅ Mac回家pull即可 | ❌ 回家还得手动解包覆盖 |
| 安全性 | ⚠️ PAT存Agent SECRET.md | ✅ Token不离用户手 |
| 依赖Agent可用 | ⚠️ 上下文重置后需读SECRET.md | ✅ 不依赖Agent |
| 适合场景 | 手机端远程协作 | 仅在电脑前工作时 |

### 最终选型：方案A（Agent直接push）
核心理由：用户的高频场景是"路上手机发活"，即时落库比安全顾虑优先级更高。安全风险通过权限最小化+过期时间控制。

---

## 2. 完整同步流程

### 触发条件
用户说出以下任一表达，Agent自动执行同步：
- "上传到GitHub"
- "推送到GitHub"
- "同步GitHub"

### 执行步骤

```
步骤1: 读取凭证
  → 从 SECRET.md 读取 GitHub仓库地址 和 PAT

步骤2: 拉取远程最新
  → git clone（首次）或 git pull --rebase（已有仓库）
  → 确保不覆盖Mac端推上去的内容

步骤3: 从项目空间下载代码
  → coze agent file list 拉清单
  → coze agent file download 逐文件下载到沙箱

步骤4: 组织目录结构
  → 后端: /ths-crawler/ → 标准 Maven 项目结构
  → 前端: /ths-crawler-ui/ → 标准 Vite + React 项目结构
  → 生成 .gitignore 排除编译产物/依赖/node_modules

步骤5: 检查差异
  → git status 查看新增/修改/删除文件
  → 有冲突时通知用户决策，不自动覆盖

步骤6: 提交
  → git add -A
  → 按 Conventional Commits 规范写 commit message
  → git commit

步骤7: 推送
  → git push origin <分支名>
  → 推送成功后告知用户 commit 信息和链接

步骤8: 确认
  → 输出: ✅ 已推送 / 📝 commit信息 / 📦 变更统计 / 🔗 commit链接
```

### 流程图

```
用户说"上传到GitHub"
       │
       ▼
  读SECRET.md取凭证 ──→ 缺失 → 提示用户提供
       │
       ▼
  拉取远程最新代码
       │
       ▼
  项目空间下载代码到沙箱
       │
       ▼
  组织目录 + .gitignore
       │
       ▼
  git status 查差异 ──→ 有冲突 → 通知用户
       │                        │
       ▼                    用户决策
  git add + commit            │
       │                     ▼
       ▼                  解决冲突后继续
  git push
       │
       ▼
  输出推送结果
```

---

## 3. Commit 规范

采用 [Conventional Commits](https://www.conventionalcommits.org/) 格式：

```
<类型>(<范围>): <简短描述>

<详细说明（可选）>
```

### 类型定义

| 类型 | 含义 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(api): 新增板块下钻成分股Top15接口` |
| `fix` | 修bug | `fix(service): 修复BigDecimal→double类型不匹配` |
| `refactor` | 重构（不改变功能） | `refactor(dao): JdbcTemplate替代MyBatis-Plus` |
| `docs` | 文档 | `docs: 补充Mac本地环境排查指南` |
| `style` | 样式/格式 | `style(ui): 移动端响应式适配≤768px` |
| `chore` | 构建/工具/脚本 | `chore(scripts): 新增sandbox-bootstrap幂等脚本` |
| `perf` | 性能优化 | `perf(crawl): 批量写入替代逐条INSERT` |
| `test` | 测试 | `test: 补充行业资金流接口测试用例` |

### 范围（scope）约定

| 范围 | 对应模块 |
|------|---------|
| `api` | Controller层 / REST接口 |
| `service` | Service层 / 业务逻辑 |
| `dao` | 数据访问层 |
| `model` | Entity / DTO / VO |
| `crawl` | 爬虫相关（Fetcher/Parser） |
| `ui` | 前端页面/组件 |
| `scripts` | Shell/Python脚本 |
| `docs` | 文档类 |
| `config` | 配置文件 |

### 判断规则
Agent根据本次变更内容自动判断类型，不需要用户每次指定。

---

## 4. 分支策略

### 默认分支：main
日常开发直接推main，项目处于早期不需要复杂的分支管理。

### Feature分支（按需）
当用户明确要求开发独立功能模块时：
```
git checkout -b feature/<功能名>
```
功能开发完成并验证后合并回main。

### 分支命名规范
| 前缀 | 用途 | 示例 |
|------|------|------|
| `feature/` | 新功能 | `feature/okhttp-fetcher` |
| `fix/` | bug修复 | `fix/mysql-encoding` |
| `refactor/` | 重构 | `refactor/dao-layer` |

---

## 5. .gitignore 配置

### 后端（Java/Maven）
```
target/
*.class
*.jar
*.war
.idea/
*.iml
.settings/
.project
.classpath
*.log
```

### 前端（React/Vite）
```
node_modules/
dist/
.vite/
*.local
```

### 通用
```
.DS_Store
Thumbs.db
*.swp
*.swo
*~
.env
.env.local
```

---

## 6. 安全约束

| 约束 | 说明 |
|------|------|
| PAT权限 | 仅勾选 `repo` 权限，不给 admin/delete 等 |
| PAT过期 | 建议30天，到期Agent提醒用户更换 |
| 存储位置 | SECRET.md（Agent私有文件，项目其他成员不可见） |
| 明文禁止 | 对话消息中绝不输出PAT原文 |
| 撤销机制 | 用户随时可在 GitHub → Settings → Developer settings 一键撤销 |

---

## 7. 异常处理

| 场景 | 处理方式 |
|------|---------|
| PAT过期 | 提示用户提供新PAT，存入SECRET.md后重试 |
| push冲突 | 不自动force push，通知用户决策 |
| 项目空间文件缺失 | 下载失败时列出缺失文件，告知用户 |
| 网络超时 | 重试1次，仍失败则告知用户稍后再试 |
| 沙箱不可用 | 提示用户等沙箱恢复后重试 |
| 仓库不存在 | 提示用户先在GitHub创建仓库 |

---

## 8. 待收信息

- [ ] GitHub仓库地址（如 `https://github.com/springwind/ths-crawler.git`）
- [ ] Personal Access Token（repo权限，30天过期）

收到后存入SECRET.md，首次推送全量代码。

---

## 9. 版本记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-06-22 | v1.0 | 初版，方案选型+完整流程+规范定义 |

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
