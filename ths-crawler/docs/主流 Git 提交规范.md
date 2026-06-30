# 一、核心规范：Angular Commit Message 规范（主流 Git 提交规范）

你这条 `feat: 初始提交 - 同花顺行业资金流向爬虫系统` 完全遵循 **Conventional Commits（约定式提交）**，行业通用别名：Angular 提交规范，是开源、企业后端 / 前端项目标准。

## 1\. 格式拆解

标准模板：

```Plain Text
<type>[可选scope]: <描述>
[空行]
<详细正文，多行列表、变更说明、配套资源>
```

### 1）type 类型（开头关键词）

你用的 `feat` 是最常用类型，含义：**新增功能、全新模块、新系统**
完整常用 type 对照表：

|类型|含义|适用场景|
|---|---|---|
|feat|新功能|新增页面、接口、完整系统、业务模块（你本次爬虫系统属于 feat）|
|fix|缺陷修复|修复 bug、数据异常、接口报错|
|docs|文档更新|仅改 README、接口文档、注释，无代码变更|
|style|代码风格|空格、格式化、分号，不改变逻辑|
|refactor|重构|代码结构优化，无新增功能、无修复 bug|
|perf|性能优化|接口提速、爬虫并发优化、SQL 优化|
|test|测试相关|新增单元测试、集成测试、测试脚本|
|chore|工程杂项|构建脚本、依赖升级、CI/CD、部署配置|
|ci|CI 流水线|Github Actions、Jenkins 配置变更|
|build|构建工具|打包、编译、依赖、Vite/Maven 配置|

### 2）scope（可选，你这条省略了）

用于限定模块，示例：

```Plain Text
feat(crawler): 同花顺行业资金流向爬虫系统
feat(web): 新增ECharts资金趋势图表
```

### 3）标题行 `: ` 后简短描述

`初始提交 - 同花顺行业资金流向爬虫系统`：一句话概括本次提交整体内容，控制在 50 字符内最佳。

### 4）空行 \+ 多行正文（你的列表部分）

标题换行空一行后，写详细变更清单，支持 `-` 无序列表，说明：

- 技术栈、新增文件 / 类数量

- 核心业务功能

- 配套文档、脚本、SQL、工具等附属资源

## 二、为什么判定是 Conventional Commits（Angular 规范）

1. 强制 `type: 简短描述` 前缀结构；

2. 支持标题 \+ 空行 \+ 详细正文的分段写法；

3. 统一 type 语义，配合工具自动化：

    - 自动生成 CHANGELOG 版本更新日志

    - SemVer 语义化版本号自动升级（feat → minor 次版本号提升）

    - Git 提交记录筛选、代码评审快速分类

4. 前后端、Java/React/Python 全技术栈通用，SpringBoot、React 开源项目标配。

## 三、对比其他提交规范（区分开）

1. **Gitmoji 规范**：会带表情符号（✨ feat: xxx），你这条没有，排除；

2. **简易单行规范**：无统一 type，随便写日志，不区分 feat/fix，不符合；

3. **企业自定义规范**：只是在 Conventional Commits 基础上加额外规则，底层还是这套。

## 四、优化示例（贴合你项目的标准写法）

### 标准版（带 scope 爬虫模块）

```Plain Text
feat(crawler): 新增同花顺行业资金流向完整爬虫系统

- 后端: Spring Boot 3.2 + JdbcTemplate + MySQL + Redis (35个Java类)
- 前端: React 18 + Vite 5 + TypeScript + Ant Design 5 + ECharts 5
- 功能: 行业资金流排行/趋势分析/共振信号/移动端适配
- 文档: 设计文档/API示例/部署指南/GitHub同步方案
- 脚本: 多环境启动脚本/Python数据抓取/测试数据SQL
```

### 极简单行（小提交使用）

```Plain Text
feat: 初始化同花顺行业资金流向爬虫前后端整套系统
```

## 五、配套工具（该规范生态）

- 校验：commitlint \+ husky（提交时自动校验格式错误）

- 日志生成：standard\-version /semantic\-release

- IDE 插件：VSCode Conventional Commits 快速生成提交模板


