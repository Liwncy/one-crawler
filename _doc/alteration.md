# 结合你现有项目的改造建议

先说结论：你的骨架已经很好，**主要缺的是"框架适配层"和"任务调度/管理层"**。下面针对你的现有结构给出具体建议。

---

## 现有结构 vs 目标结构对比

```
one-crawler/
├── one-boot/                        ✅ 不动
├── one-common/
│   ├── one-common-core/             ✅ 不动
│   ├── one-common-web/              ✅ 不动
│   ├── one-common-mybatis/          ✅ 不动
│   └── one-common-webmagic/         ⚠️  改造 → 迁移到 one-common-crawler
│                                        作为爬虫公共抽象层
├── one-extend/
│   └── one-generator/               ✅ 不动
└── one-modules/
    ├── one-demo/                    ✅ 不动
    └── one-spiders/                 ⚠️  作为 spider 业务模块继续演进

+ 新增：
├── one-adapter/                     🆕 框架适配层（核心新增）
│   ├── one-adapter-webmagic/        WebMagic 适配器
│   ├── one-adapter-crawler4j/       Crawler4j 适配器（按需）
│   └── one-adapter-okhttp/          原生 OkHttp 适配器
└── one-modules/
    └── one-spiders/                 🆕 统一存放所有爬虫业务实现
```

---

## 重点改造点

### 1. `one-common-webmagic` → 改名为 `one-common-crawler`

现在这个模块是 WebMagic 专用的，要把它**抽象化**，变成与框架无关的公共层：

```
one-common-crawler/
├── AbstractSpider.java          # 爬虫业务基类（之前讨论的）
├── FrameworkAdapter.java        # 适配器接口
├── SpiderConfig.java            # 爬虫配置（含 framework 字段）
├── CrawlResult.java             # 统一结果模型
├── CrawlerEngine.java           # 统一引擎
├── pipeline/
│   ├── Pipeline.java            # Pipeline 接口
│   ├── ConsolePipeline.java
│   ├── FilePipeline.java        # 把现有写死路径的 Pipeline 改造到这里
│   └── DatabasePipeline.java
└── scheduler/
    └── UrlScheduler.java
```

> 原来 `one-common-webmagic` 里的 `WebMagicConfig`、`WebMagicProperties`、Selenium 下载器等，迁移到 `one-adapter-webmagic`
里。

---

### 2. 新增 `one-adapter` 模块组

```
one-adapter-webmagic/
├── WebMagicAdapter.java         # 实现 FrameworkAdapter
├── WebMagicConfig.java          # 从 one-common-webmagic 迁移过来
└── SeleniumDownloader.java      # 从 one-common-webmagic 迁移过来

one-adapter-okhttp/              # 轻量爬取，无需重型框架时用
└── OkHttpAdapter.java
```

**依赖关系：**

```
one-adapter-webmagic
    └── 依赖 one-common-crawler（使用 FrameworkAdapter 接口）
    └── 依赖 webmagic-core（真实框架）
```

---

### 3. 改造 `one-spiders` 里现有爬虫任务

你现有的三个任务直接改写成继承 `AbstractSpider`：

```java name=WeiXinBQBSpider.java
@Component
public class WeiXinBQBSpider extends AbstractSpider {

    @Override
    public SpiderConfig getConfig() {
        return SpiderConfig.builder()
            .name("weixin-bqb")
            .framework("spiders")
            .startUrls(List.of("https://..."))
            .threadCount(3)
            .pipelines(List.of("file"))       // 不再写死路径，走配置
            .build();
    }

    @Override
    public List<CrawlResult> parse(String html, String url) {
        // 原有解析逻辑搬过来
        return Jsoup.parse(html).select("img").stream()
            .map(el -> CrawlResult.builder()
                .field("imgUrl", el.attr("src"))
                .build())
            .collect(Collectors.toList());
    }
}
```

同时把 `D:\weixin\`、`D:\webmagic\` 这两处硬编码**迁移到配置文件**：

```yaml name=application-dev.yml
one:
  crawler:
    output-path: ./data/   # 统一出口，覆盖原来写死的 Windows 路径
```

---

### 4. 补充任务调度 + API 层（你 README 里也提到了这一点）

在 `one-modules` 下新增 `one-task` 模块：

```
one-task/
├── controller/
│   └── SpiderTaskController.java   # POST /task/start/{name}
│                                   # POST /task/stop/{name}
│                                   # GET  /task/list
├── service/
│   └── SpiderTaskService.java      # 调用 CrawlerEngine
└── entity/
    └── SpiderTaskLog.java          # 任务执行记录入库
```

这样爬虫就从"手动跑 main 方法"升级为"通过 HTTP 接口触发"。

---

## 建议优先级排序

```
P0（影响整体架构，先做）
├── 1. 把 one-common-webmagic 抽象成 one-common-crawler
└── 2. 把 WebMagic 相关实现迁移到 one-adapter-webmagic

P1（完善可用性）
├── 3. 现有爬虫任务改写为 AbstractSpider 子类
├── 4. 清理硬编码路径 → 接入统一配置
└── 5. 统一 Java 版本到 17

P2（扩展能力）
├── 6. 增加 one-task 任务管理 API
├── 7. Pipeline 接入数据库持久化
└── 8. 按需增加 Crawler4j / Playwright 适配器
```

---

## 最终模块依赖关系

```
one-boot
  └── one-modules/one-task
  └── one-modules/one-spiders

one-modules/one-spiders
  └── one-common-crawler          （AbstractSpider、接口）
  └── one-adapter/one-adapter-webmagic  （通过 @Component 自动注入）

one-adapter/one-adapter-webmagic
  └── one-common-crawler
  └── webmagic-core

one-common-crawler
  └── one-common-core
```

整体改动量不大，主要是**搬移 + 抽象**，现有代码基本都能复用。