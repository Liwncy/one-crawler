# one-crawler

一个基于 **Spring Boot 3 + Maven 多模块** 的爬虫/通用后台脚手架项目。

从当前仓库结构和源码来看，这个项目并不只是“单一爬虫程序”，而是一个带有：

- 启动入口层（`one-boot`）
- 通用基础层（`one-common`）
- 业务模块层（`one-modules`）
- 扩展工具层（`one-extend`）

的多模块工程。其中 **`one-spiders`** 是当前最明确的业务能力模块，主要用于承载爬虫任务定义、任务接口和基于 **WebMagic** 的执行能力。

---

## 1. 项目定位

`one-crawler` 目前更像一个“**爬虫能力 + 后台基础设施**”的实验型/脚手架型仓库，已经具备：

- Spring Boot 启动能力
- 多模块依赖管理
- 通用 Web/异常处理/响应封装
- MyBatis-Plus 基础支持
- WebMagic 爬虫公共封装
- 若干具体采集任务示例
- 基于数据库表结构的代码生成器

从现有代码看，项目的主要方向包括：

1. **爬虫采集**：采集网页、下载图片、导出 JSON 文本
2. **后台基础建设**：提供统一响应体、异常处理、分页/基础实体等能力
3. **开发提效**：通过代码生成器快速生成业务模板代码

---

## 2. 项目结构

```text
one-crawler/
├── pom.xml                  # 根聚合工程，统一依赖/版本/仓库配置
├── README.md
├── one-boot/                # Spring Boot 启动模块
├── one-common/              # 通用基础模块集合
│   ├── one-common-bom/      # 通用模块 BOM
│   ├── one-common-core/     # 核心工具、常量、异常、响应封装
│   ├── one-common-crawler/  # 爬虫抽象层（Spider / Adapter / Pipeline）
│   ├── one-common-web/      # Web 层能力、异常处理、基础控制器
│   └── one-common-mybatis/  # MyBatis-Plus 基础封装
├── one-adapter/             # 爬虫框架适配层
│   └── one-adapter-webmagic/# WebMagic 适配器实现
├── one-extend/              # 扩展模块
│   └── one-generator/       # 基于数据库表结构的代码生成器
└── one-modules/             # 业务模块集合
    ├── one-demo/            # 演示模块（当前代码较少）
    └── one-spiders/         # 爬虫任务模块
```

---

## 3. 技术栈分析

根据根目录 `pom.xml` 与各模块依赖，当前项目主要使用了以下技术：

### 基础框架

- **Java**：已统一到 `17`
- **Spring Boot**：`3.4.3`
- **Maven**：多模块聚合构建
- **Lombok**

### Web / 基础设施

- `spring-boot-starter-web`
- `spring-boot-starter-undertow`
- `spring-boot-starter-actuator`
- `spring-boot-admin`
- `springdoc-openapi`

### 数据访问

- **MyBatis**
- **MyBatis-Plus**
- **MySQL 驱动**
- **p6spy**

### 爬虫相关

- **WebMagic** `1.0.3`
- **Selenium** `3.141.59`
- **PhantomJSDriver** `1.2.0`

### 工具与扩展

- **Hutool**
- **MapStruct Plus**
- **EasyExcel**
- **Velocity**
- **Freemarker**（代码生成器）

此外，根工程还预留了诸如 `Sa-Token`、`Redisson`、`Lock4j`、`Warm-Flow`、`AWS S3`、`sms4j` 等依赖版本管理，说明这个仓库后续可能还会承载更多后台能力。

---

## 4. 模块说明

### 4.1 `one-boot`

项目启动模块，主类为：

- `me.liwncy.OneApplication`

特征：

- 标准 `@SpringBootApplication`
- 使用 `BufferingApplicationStartup` 记录启动过程
- 提供 `OneServletInitializer`，支持传统 Servlet 容器部署
- 依赖了业务模块：
  - `one-demo`
  - `one-spiders`

默认端口在 `one-boot/src/main/resources/application.yml` 中配置为：

- `9090`

---

### 4.2 `one-common`

#### `one-common-core`

核心基础模块，包含：

- 常量类
- 工具类（字符串、日期、线程、反射、文件、正则、IP 等）
- 异常体系
- 统一响应对象 `R<T>`
- Spring / Servlet 辅助工具

其中 `R<T>` 提供了统一返回结构：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

#### `one-common-web`

Web 基础模块，包含：

- `BaseController`
- `GlobalExceptionHandler`
- Web 资源配置

目前全局异常处理比较完整，已经覆盖：

- 请求方式不支持
- 业务异常
- 参数校验异常
- 路由不存在
- 运行时异常
- 系统异常

#### `one-common-mybatis`

数据库基础模块，包含：

- MyBatis-Plus 配置
- `BaseEntity`
- `BaseMapperPlus`
- `PageQuery`
- `TableDataInfo`

适合后续快速搭建 CRUD 型业务模块。

#### `one-common-crawler`

这是本轮改造新增的爬虫抽象层，主要承担“框架无关”的统一能力：

- `AbstractSpider`：业务爬虫抽象基类
- `SpiderConfig`：统一任务配置
- `SpiderParseResult`：统一解析结果（数据 + 待抓取链接）
- `FrameworkAdapter`：爬虫框架适配器接口
- `CrawlerEngine`：统一调度入口
- `SpiderPipeline`：统一结果处理管道接口

这一步的核心价值是把“业务爬虫定义”和“底层抓取框架”解耦，为后续增加更多适配器做准备。

> 说明：旧的 `one-common-webmagic` 已完成迁移并从当前模块结构中移除；原先承担的能力已经分拆到 `one-common-crawler` 与 `one-adapter-webmagic` 中。

### 4.3 `one-adapter`

这是本轮改造新增的框架适配层，负责把统一的爬虫抽象层接到具体框架实现上。

#### `one-adapter-webmagic`

当前已落地的适配器模块，包含：

- `WebMagicAdapter`：把 `AbstractSpider` 适配到 WebMagic 执行链路
- `WebMagicConfig` / `WebMagicProperties`：适配器级配置
- 迁移后的 `SeleniumDownloader` / `WebDriverPool`

---

### 4.4 `one-modules`

#### `one-demo`

演示模块，目前源码很少，仅有空的 `CommonController`，更像是业务模块模板或占位模块。

#### `one-spiders`

这是当前仓库中最核心的业务模块，职责定位为“爬虫任务与接口模块”。

当前包含：

- 爬虫任务处理器（`PageProcessor`）
- 新版抽象蜘蛛实现（已开始迁移）
- 采集结果模型
- 结果落盘 Pipeline

目前可以看到的新版蜘蛛任务包括：

##### 1）`TestSpider`

- WebMagic 抽象层测试蜘蛛
- 默认采集 `https://www.baidu.com`

##### 2）`WeiXinBQBSpider`

- 采集微信公众号文章中的表情包图片
- 通过 `WeiXinBQBDownloadPipeline` 下载图片到本地目录
- 输出路径统一走 `one.crawler.output-path`

##### 3）`YujnApiSpider`

这是当前的文档抓取蜘蛛示例：

- 继承 `AbstractSpider`
- 通过 `SpiderConfig` 声明抓取入口和管道
- 由 `WebMagicAdapter` 执行
- 通过 `YujnApiApiFoxFilePipeline` 输出为 Apifox 导入文件

同时还补充了最小任务接口：

- `GET /spiders`：查看可用蜘蛛及元信息
- `GET /spiders/{name}`：查看指定蜘蛛详情
- `POST /spiders/run/{name}`：执行指定蜘蛛

当前返回的蜘蛛元信息已包含：

- `name`：任务名
- `description`：任务描述
- `framework`：底层框架类型
- `threadCount`：线程数
- `startUrls` / `startUrlCount`：启动地址信息
- `pipelines`：绑定的输出管道
- `status`：当前/最近一次运行状态（`READY` / `RUNNING` / `SUCCESS` / `FAILED`）
- `lastStartTime` / `lastEndTime` / `lastDurationMillis`：最近一次运行时间信息
- `lastErrorMessage`：最近一次失败原因

---

### 4.5 `one-extend`

#### `one-generator`

这是一个基于数据库表结构的代码生成器，核心类为：

- `me.liwncy.generator.CodeGenerator`

目前能力：

- 读取 MySQL 表结构与字段注释
- 通过 Freemarker 模板生成：
  - Entity
  - BO
  - VO
  - Controller
  - Service
  - Mapper
  - Mapper XML

当前代码里内置了以下默认信息：

- 数据库：`jdbc:mysql://localhost:3306/one-crawler`
- 用户名：`root`
- 密码：`root123`
- 目标表：`sys_user`
- 生成包名：`me.liwncy.demo`
- 默认输出模块：`one-modules/one-demo`

也就是说，这个生成器目前是一个“**可用但需要按本地环境改参数**”的工具类，而不是零配置即用的插件。

---

## 5. 当前代码现状总结

结合现有源码，可以把项目状态概括为：

### 已具备

- 多模块 Maven 结构完整
- Spring Boot 启动链路完整
- 公共基础层已初步成型
- 爬虫任务示例已经存在，且可通过 `main` 方法独立执行
- 代码生成器可作为开发辅助工具使用

### 目前仍偏“脚手架 / 开发中”

- 对外 HTTP 接口很少，当前仓库中几乎没有实际业务 API Controller
- 若干模块仍以占位或样例代码为主
- 部分配置项与实际代码路径存在不一致
- 本地路径、数据库地址、账号密码等信息存在硬编码现象
- 生产级别的任务调度、持久化、监控闭环尚未完全体现

如果你后续要把它作为正式项目继续推进，建议优先补齐：

1. 配置外置化
2. 爬虫任务调度机制
3. 任务结果持久化
4. API 层与管理后台
5. 单元测试 / 集成测试
6. Docker 化与部署说明

---

## 6. 环境要求

根据项目配置，建议准备以下环境：

- **JDK 17 或以上**（根工程属性如此声明）
- **Maven 3.8+**
- **MySQL 8.x**
- Windows 环境下调试会更方便，但当前主链路输出路径已经支持配置化

> 当前改造链路上的模块编译版本已经统一到 Java 17。

---

## 7. 配置说明

### 7.1 应用基础配置

文件：`one-boot/src/main/resources/application.yml`

主要配置：

- 服务名：`one-crawler`
- 端口：`9090`
- Profile：`@profiles.active@`
- MyBatis-Plus 包扫描与 Mapper XML 路径

支持的 Maven Profile：

- `local`
- `dev`（默认）
- `prod`

### 7.2 数据源配置

文件：

- `one-boot/src/main/resources/application-dev.yml`
- `one-boot/src/main/resources/application-prod.yml`

当前仓库里都配置成了本地 MySQL：

- 数据库：`one-crawler`
- 用户名：`root`
- 密码：`root123`

> 这些配置明显更适合本地开发环境，正式使用前请先替换。

### 7.3 WebMagic 配置

文件：`one-boot/src/main/resources/application.yml`

当前统一通过 `one.crawler.*` 和 `one.crawler.webmagic.*` 管理爬虫输出目录、重试、超时和编码等配置。

---

## 8. 构建与启动

### 8.1 全量构建

在项目根目录执行：

```powershell
mvn clean package -DskipTests
```

### 8.2 启动 Spring Boot 应用

可直接运行启动类：

- `me.liwncy.OneApplication`

或在根目录执行：

```powershell
mvn spring-boot:run -pl one-boot
```

启动后默认访问地址：

```text
http://localhost:9090/
```

> 说明：从当前源码搜索结果看，仓库里几乎还没有正式对外业务接口，因此应用启动后更多体现的是基础框架与依赖装配能力。

---

### 9. 如何运行爬虫任务

当前推荐通过应用提供的统一任务接口运行蜘蛛。

### 9.1 示例任务

- `me.liwncy.spiders.task.TestSpider`

### 9.2 微信表情包采集

- `me.liwncy.spiders.task.WeiXinBQBSpider`

作用：

- 解析微信公众号文章页面中的图片
- 下载到 `one.crawler.output-path/weixin/` 目录

### 9.3 遇见 API 转 Apifox 导入文件

- `me.liwncy.spiders.task.YujnApiSpider`

作用：

- 抓取 `api.yujn.cn` 接口文档页面
- 转换为 Apifox 可进一步处理的 JSON 数据
- 输出到 `one.crawler.output-path` 目录

开发调试入口：

- IDE 直接运行：`me.liwncy.spiders.SpiderDevApplication`
- 可在类内修改 `DEFAULT_SPIDER_NAME` 手动切换默认 spider
- 也支持命令行参数：`--list`、`--spider`、`--output`、`--refresh-login`、`--browser-user-data-dir`
- 仍兼容旧的定位参数形式：第一个参数是 `spiderName`，第二个参数是输出目录
- 默认输出目录为仓库根目录下的 `data/`，而不是 `one-spiders` 运行目录下
- 输出目录支持指定为任意磁盘绝对路径，例如：`D:/crawler-output`
- PowerShell 脚本：`scripts/run-spider-dev.ps1`
- 例如运行 `yujn-api` 时，默认输出文件为：`<仓库根目录>/data/yujn-api-apifox.jsonl`

登录型站点开发支持：

- 登录型 Spider 可继承 `me.liwncy.spiders.support.login.AbstractLoginSpider`
- 登录配置通过 `getLoginConfig()` 提供，当前内置的是 **Selenium + 人工登录/图片验证码** 方案
- 登录态编排由 `LoginSessionManager` 负责，底层登录实现通过 `LoginSupport` 接口接入，当前默认实现为 `SeleniumManualLoginSupport`
- 首次运行或登录态过期时，会自动打开浏览器，等待手工完成登录和验证码输入
- 手工登录时程序会先自动检测是否已完成登录；如果长时间未检测到，再提示你回控制台按回车继续，避免浏览器还没输完账号密码程序就退出
- 登录型任务会优先从受保护目标页进入；如果网站自动跳转到登录页，就在同一浏览器上下文中完成登录，再继续回到目标页面，避免登录成功后又被再次要求登录
- 登录成功后会缓存 Cookie 到：`<仓库根目录>/data/auth/<spiderName>-session.json`
- 下次运行会优先复用缓存登录态；如果需要强制重新登录，可追加 `--refresh-login`
- 如果想复用本机 Chrome 资料目录，可追加 `--browser-user-data-dir "D:/ChromeProfile/SpiderDev"`
- 已提供模板类：`me.liwncy.spiders.task.LoginDemoSpider`
- 模板默认会出现在 `--list` 结果中，名称为 `login-demo`，你可以直接复制/改名或在原类上替换常量：`LOGIN_URL`、`PROTECTED_URL`、`BROWSER_USER_DATA_DIR`
- 模板类里保留了 `readValue()` 示例，可通过系统属性或环境变量读取账号密码，例如：`spider.login.demo.username`
- 模板还支持直接覆盖这些属性：`spider.login.demo.login-url`、`spider.login.demo.protected-url`、`spider.login.demo.session-ttl-minutes`、`spider.login.demo.manual-captcha`、`spider.login.demo.expected-keyword`
- 如已知登录表单的 CSS 选择器，可继续配置：`spider.login.demo.username-selector`、`spider.login.demo.password-selector`、`spider.login.demo.submit-selector`，模板会自动填充账号密码并尝试点击提交
- 如需在发现仍停留登录页时直接失败，可配置：`spider.login.demo.login-page-keyword`；默认 `fail-when-login-page-detected=true`
- 如需在自动提交后额外等待页面跳转，可配置：`spider.login.demo.wait-after-submit-millis`
- 例如通过脚本运行模板时，可这样传入系统属性：

```powershell
Set-Location "D:\Workspace\one-crawler"
.\scripts\run-spider-dev.ps1 -SpiderName login-demo -RefreshLogin -SystemProperty @(
    "spider.login.demo.login-url=https://example.com/login",
    "spider.login.demo.protected-url=https://example.com/protected",
    "spider.login.demo.expected-keyword=个人中心",
    "spider.login.demo.login-page-keyword=登录",
    "spider.login.demo.username-selector=input[name='username']",
    "spider.login.demo.password-selector=input[type='password']",
    "spider.login.demo.submit-selector=button[type='submit']"
)
```

文书网开发任务：

- 已新增任务：`me.liwncy.spiders.task.WenshuCourtSpider`
- 任务名称：`wenshu-court`
- 任务会先复用登录骨架获取 Cookie，再使用 Selenium 打开目标列表初始页、自动输入检索条件并从渲染后的 DOM 中提取：标题、法院、案号、裁判日期、裁判理由摘要、文书详情 `docId`、关联文书等信息
- 默认目标页只保留列表页 `pageId`，不会再带之前遗留的 `s11` / `s16` 检索条件；如需指定检索条件，请通过 `spider.wenshu.court.target-url` 自行覆盖
- 文书网任务默认会复用浏览器资料目录：`<仓库根目录>/data/browser/wenshu-court`，首次登录后下次可直接沿用同一浏览器登录态
- 如果渲染失败，或搜索框未加载完成，会把最后拿到的页面源码落到：`<仓库根目录>/data/debug/wenshu-court-last-page.html`
- 可覆盖的系统属性包括：`spider.wenshu.court.login-url`、`spider.wenshu.court.target-url`、`spider.wenshu.court.base-url`、`spider.wenshu.court.search-keyword`、`spider.wenshu.court.expected-keyword`、`spider.wenshu.court.browser-user-data-dir`、`spider.wenshu.court.render-wait-millis`

```powershell
Set-Location "D:\Workspace\one-crawler"
.\scripts\run-spider-dev.ps1 -SpiderName wenshu-court -RefreshLogin -SystemProperty @(
    "spider.wenshu.court.expected-keyword=（2018）鲁1482破申3号",
    "spider.wenshu.court.render-wait-millis=10000"
)
```

---

## 10. 代码生成器使用说明

生成器主类：

- `me.liwncy.generator.CodeGenerator`

使用前建议先修改以下常量：

- `JDBC_URL`
- `JDBC_USER`
- `JDBC_PWD`
- `TABLE_NAME`
- `BASE_PACKAGE`
- `PROJECT_PATH`

然后执行主类即可生成模板代码。

如果你想通过 Maven 运行，可参考模块中的 `exec-maven-plugin` 配置，在 `one-extend/one-generator` 模块下执行类似命令：

```powershell
mvn exec:java
```

---

## 11. 这个项目适合做什么

从当前形态看，这个仓库适合用于：

- 快速孵化爬虫任务
- 验证 WebMagic + Selenium 的采集方案
- 作为 Spring Boot 多模块脚手架继续扩展
- 为后续后台管理系统提供基础公共层
- 做代码生成与业务模块模板化开发

如果你的目标是“快速做一个有管理界面的爬虫平台”，那么这个项目已经有不错的骨架，但还需要继续补齐：

- 任务管理接口
- 任务调度中心
- 结果查询接口
- 数据库存储模型
- 前端页面/管理后台

---

## 12. 后续优化建议

建议优先做以下改造：

### 1）进一步收口剩余硬编码与兼容入口

当前主链路已经基本完成配置化，但仍建议继续清理旧兼容类和历史入口中的残余实现。

### 2）统一 Java 版本

根工程与主要改造链路模块已经统一到 Java 17，剩余模块也建议保持一致。

### 3）增加真正的业务 API

目前项目 API 层偏弱，建议增加：

- 任务创建
- 任务启动/停止
- 结果分页查询
- 日志查看

### 4）增加持久化

目前很多结果仍然直接写文件，建议逐步转为：

- MySQL
- Elasticsearch
- MinIO / S3

### 5）补充测试与部署文档

建议增加：

- 单元测试
- 集成测试
- Dockerfile
- docker-compose
- 数据库初始化脚本

---

## 13. 一句话总结

`one-crawler` 当前是一个 **以 WebMagic 爬虫能力为核心、结合 Spring Boot 多模块基础架构的后台脚手架项目**。它已经有较完整的分层和扩展方向，但业务层仍在完善中，比较适合作为后续继续演进的基础仓库。
