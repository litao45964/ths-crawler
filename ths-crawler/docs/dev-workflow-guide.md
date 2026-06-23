# ths-crawler 开发协作链路与踩坑总结

> 2026-06-23 春风 & 小伍 共同整理 · 2026-06-23 v2更新（SSH/固定隧道/Flyway就绪后大幅修订）

---

## 一、四套环境定位

| 环境 | 角色 | 网络 | 持久化 | 操作方式 |
|------|------|------|--------|----------|
| **沙箱** | 代码生产车间：写代码、编译、验证 | 能访问GitHub（SSH over 443） | ❌ 重启丢失 | bash(默认) |
| **项目空间** | 持久留档：跨会话不丢、项目成员共享 | N/A（Coze内部） | ✅ 永久保存 | coze CLI upload/write |
| **GitHub** | 代码分发中枢：版本控制+云电脑pull来源+备份 | 公网，SSH over 443稳定 | ✅ 永久保存 | git push/pull |
| **云电脑** | 运行车间：git pull+编译部署+运行验证 | SSH over 443稳定，能访问同花顺 | ✅ 永久保存 | bash(desktop_name="cloud") |

---

## 二、协作链条（核心：GitHub作为代码分发中枢）

```
┌─────────┐  写代码+编译  ┌──────────┐   upload    ┌──────────┐
│  沙箱    │ ──────────→ │ 项目空间  │ ──────────→ │  留档     │
│(写+编译) │              │(持久留档) │             │          │
└─────────┘              └──────────┘              └──────────┘
     │
     │  git push (SSH over 443，稳定)
     ↓
┌──────────────────────────────────────────────────────────────┐
│                        GitHub 仓库                            │
│              https://github.com/litao45964/ths-crawler        │
│                    git@github.com:litao45964/ths-crawler.git  │
└──────────────────────────────────────────────────────────────┘
     │
     │  git pull (SSH over 443，稳定)
     ↓
┌──────────┐   编译+部署    ┌──────────────────────────────────┐
│  云电脑   │ ──────────→ │  https://ths.thsflow.xyz          │
│(运行车间) │              │  固定域名，随时可验证               │
└──────────┘              └──────────────────────────────────┘
```

### 推荐开发流程（v2简化版）

0. **沙箱崩溃/重启？** → `git clone git@github.com:litao45964/ths-crawler.git` 从GitHub拉代码基线（铁律：代码不从项目空间拼凑；非代码文件如不在GitHub可从项目空间获取）
1. **沙箱写代码** → 编译验证通过（铁律：编译是唯一真相）
2. **沙箱push到GitHub** → 先保命再留档（铁律：编译通过后先push GitHub，再上传项目空间）
3. **沙箱上传到项目空间** → 留档备查
4. **云电脑 git pull** → 增量同步，速度快
5. **云电脑编译运行** → `ths-service.sh restart` → 验证

> ⚡ **关键变化**：云电脑不再从项目空间download文件，改用 `git pull` 拿代码——速度快、增量同步、有版本记录。

---

## 三、这次遇到的问题（及解决状态）

### 问题1：云电脑访问GitHub超时 ✅ 已解决

**现象**：`git push` 卡住 60s+ 无响应，`curl github.com` 返回 000

**根因**：国内服务器访问GitHub受GFW影响，TCP 22端口被干扰

**解决**：SSH over 443端口。`~/.ssh/config` 配置 `Port 443` + `Hostname ssh.github.com`，连接稳定可靠

**教训**：GFW问题不要等，直接换端口

### 问题2：沙箱从项目空间下载文件太慢 ⚠️ 已绕过

**现象**：`coze agent file download` 单个文件 10s+，80个文件要十几分钟

**解决**：不再从项目空间批量下载。云电脑改用 `git pull` 从GitHub拉代码

### 问题3：沙箱↔云电脑无法直接传文件 ✅ 已绕过

**变通方案**：沙箱push GitHub → 云电脑git pull，速度和稳定性都OK

### 问题4：数据库表结构变更容易漏 ⚠️ 待治理

**现象**：沙箱改了Entity/Mapper代码编译通过，但云电脑MySQL表结构没同步ALTER，导致运行时报错

**根因**：DDL变更不在git管理范围内，靠人记忆手动执行

**解决方案**：引入Flyway数据库版本化管控，所有DDL/DML变更走迁移脚本，纳入git版本控制。详见 [数据库变更管控指南](./数据库变更管控指南.md)

---

## 四、各环境操作铁律

### 沙箱铁律
- ✅ 写代码、编译验证
- ✅ 编译通过后**先push GitHub再上传项目空间**（铁律：GitHub是保命线，先保命再留档）
- ✅ 上传文件到项目空间（coze CLI upload/write）
- ✅ git push到GitHub（SSH over 443，稳定）
- ✅ 崩溃/重启后从GitHub拉取代码基线（git clone，唯一代码源）
- ❌ 不要跑长期服务（重启丢失）
- ❌ 不要从项目空间download/read拼凑代码（太慢+缓存问题+版本不确定）
- ⚠️ 非代码文件（用户上传/纯文档/临时分析）如果不在GitHub里，可从项目空间获取

### 云电脑铁律
- ✅ git pull拿代码（SSH over 443，稳定）
- ✅ 编译运行、部署服务、对外暴露
- ✅ 访问同花顺（爬虫核心能力）
- ✅ ths-service.sh管理服务生命周期
- ❌ sudo只能跑apt/dpkg，其他被禁
- ❌ systemctl/service被禁，服务需nohup启动

### 项目空间铁律
- ✅ 所有产出文件的持久留档
- ✅ Agent工作目录的文件不跟项目走
- ✅ `coze agent file write` 比 `upload` 更可靠（直接覆盖无缓存问题）

### GitHub铁律
- ✅ 代码分发中枢+版本控制+备份
- ✅ SSH over 443，PAT存SECRET.md
- ✅ Conventional Commits规范：feat/fix/refactor/docs/chore

### 数据库变更铁律 🆕
- ✅ 所有DDL变更走Flyway迁移脚本，禁止手动ALTER
- ✅ 迁移脚本纳入git，随代码同步到云电脑
- ✅ 版本号格式：`V{YYYYMMDD}{序号}__{描述}.sql`
- ✅ 云电脑git pull后重启应用，Flyway自动执行新迁移
- ❌ 不要在云电脑手动改表结构（会跟Flyway基线冲突）

---

## 五、未来开发新功能的推荐路径

### 场景A：小改动（改几个Java文件，无表结构变更）

```
沙箱改代码 → mvn compile验证 → 上传项目空间 → push GitHub →
云电脑 git pull → mvn compile → ths-service.sh restart → 验证
```

全程5分钟内完成。

### 场景B：小改动+表结构变更

```
沙箱改代码 → 新增Flyway迁移脚本(如V2026062401__add_xxx.sql) →
mvn compile验证 → 上传项目空间+迁移脚本 → push GitHub →
云电脑 git pull → ths-service.sh restart(Flyway自动执行迁移) → 验证
```

### 场景C：大改动（新增功能模块，涉及多文件+表结构）

```
沙箱写全部新文件 → 新增Flyway迁移脚本 → 整体编译验证 →
批量上传项目空间 → push GitHub →
云电脑 git pull → mvn clean package → ths-service.sh restart →
验证API → 验证前端 → 验证数据库结构
```

### 场景D：纯前端功能

```
沙箱写React代码 → 上传项目空间 → push GitHub →
云电脑 git pull → npm build → cp -r dist/* /var/www/ths-crawler/ →
验证 https://ths.thsflow.xyz
```

### 场景E：紧急hotfix（云电脑直改）

```
云电脑vim改代码 → 编译验证 → ths-service.sh restart →
git add + commit + push（补回GitHub）
```

⚠️ 此路径跳过沙箱和项目空间，改完务必push回GitHub保持同步

---

## 六、关键命令速查

```bash
# === 沙箱：项目空间操作 ===
coze agent file list --project-id 7652034661715165474 --project-dir /ths-crawler
coze agent file upload --project-id 7652034661715165474 --local-file-path xxx --project-dir /ths-crawler/src/xxx
coze agent file write  --project-id 7652034661715165474 --project-file-path /ths-crawler/xxx --content "xxx"

# === 云电脑：Git操作 ===
cd /root/ths-crawler-repo
git pull                                    # 拉取最新代码
git add -A && git commit -m "feat: xxx" && git push  # 推送变更

# === 云电脑：服务管理 ===
/root/ths-service.sh start                 # 启动全部服务
/root/ths-service.sh stop                  # 停止全部服务
/root/ths-service.sh restart               # 重启全部服务
/root/ths-service.sh status                # 查看服务状态

# === 云电脑：手动编译部署 ===
cd /root/ths-crawler-repo/ths-crawler && mvn clean package -DskipTests
cd /root/ths-crawler-repo/ths-crawler-ui && npm run build && cp -r dist/* /var/www/ths-crawler/

# === 云电脑：Flyway数据库迁移 ===
# Flyway在Spring Boot启动时自动执行，也可手动触发：
cd /root/ths-crawler-repo/ths-crawler && mvn flyway:info    # 查看迁移状态
cd /root/ths-crawler-repo/ths-crawler && mvn flyway:migrate  # 手动执行迁移

# === 云电脑：MySQL直连 ===
mysql -uroot -proot ths_crawler -e "SHOW TABLES;"
mysql -uroot -proot ths_crawler -e "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"

# === 公网验证 ===
curl https://ths.thsflow.xyz/api/industry-flow/latest?topN=5

# === Cloudflare隧道 ===
cloudflared tunnel run ths-flow              # 启动固定隧道
cat /root/.cloudflared/config.yml            # 查看隧道配置
```

---

## 七、待优化项

| # | 事项 | 状态 | 说明 |
|---|------|------|------|
| 1 | 云电脑→沙箱文件通道 | ❌ 待解决 | 目前只能通过项目空间中转或GitHub |
| 2 | Flyway实际接入 | 🔨 进行中 | 指南已写完，待添加依赖+迁移脚本+编译验证 |
| 3 | Playwright浏览器自动化 | 🔜 下一步 | 云电脑已有Chrome 146，待开发90行业全覆盖爬取 |
| 4 | dev-workflow-guide自动化 | 💡 远期 | 每次重大基础设施变更后自动提醒更新本文档 |

---

## 八、基础设施现状速查

| 组件 | 配置 | 备注 |
|------|------|------|
| 云电脑公网IP | 49.233.146.84 | 安全组封锁，不可直连 |
| 固定域名 | https://ths.thsflow.xyz | Cloudflare Tunnel → localhost:8080 |
| Spring Boot | 端口8100 | Nginx反代8080→前端+8100→后端 |
| MySQL | localhost:3306, root/root, ths_crawler库 | 5张表，105行数据 |
| Redis | localhost:6379, 无密码 | 缓存层 |
| 开机自启 | crontab @reboot → /root/startup.sh | 5个服务自动拉起 |
| SSH | ~/.ssh/id_ed25519, Port 443 | GitHub SSH over 443 |
| Flyway | 待接入 | baseline-on-migrate=true, V1基线+V2起增量 |

---

*文档生成时间：2026-06-23 09:38*
*v2更新时间：2026-06-23 23:31（新增铁律：编译通过后先push GitHub再上传项目空间）*

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
