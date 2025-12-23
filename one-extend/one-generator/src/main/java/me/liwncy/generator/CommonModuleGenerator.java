package me.liwncy.generator;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * common子模块生成器
 *
 * @author wanchang.li
 */
public class CommonModuleGenerator {

    private static final String GROUP_ID = "me.liwncy";
    private static final String PARENT_MODULE = "one-common";
    // todo 子模块相关配置
    private static final String MODULE_NAME = "one-common-webmagic";
    private static final String MODULE_INFO = "爬虫服务";
    private static final String BASE_PACKAGE = GROUP_ID + ".common.webmagic";

    // 项目输出目录
    private static final String MODULE_PATH = getGeneratePath();

    private static final String JAVA_OUT = MODULE_PATH + "/src/main/java/";
    private static final String RES_OUT = MODULE_PATH + "/src/main/resources/";


    private static String getGeneratePath() {
        String dir = System.getProperty("user.dir");
        // 统一把 Windows 的反斜杠 \ 替换为 / 以便判断
        String normalizedDir = dir.replace("\\", "/");
        return normalizedDir + "/" + PARENT_MODULE + "/" + MODULE_NAME + "/";
    }

    public static void main(String[] args) throws Exception {
        System.out.println("当前输出根目录: " + MODULE_PATH);
        // 传入表名
        generate();
    }

    private static void generate() throws Exception {

        // Freemarker 配置
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        // 注意：这里确保 templates 目录是在 resources 下
        cfg.setClassLoaderForTemplateLoading(CommonModuleGenerator.class.getClassLoader(), "templates/cmgen");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        // 构建模板参数
        Map<String, Object> model = new HashMap<>();
        model.put("groupId", GROUP_ID);
        model.put("parentModule", PARENT_MODULE);
        model.put("moduleName",MODULE_NAME);
        model.put("moduleInfo", MODULE_INFO);
        model.put("basePackage",BASE_PACKAGE);
        // 生成readme文件
        write(cfg, model, "readme.ftl", MODULE_PATH + "README.md");
        // 生成pom文件
        write(cfg, model, "pomXml.ftl", MODULE_PATH + "pom.xml");
        // 生成模块文件夹
        write(cfg, model, "null.ftl", JAVA_OUT + BASE_PACKAGE.replace(".", "/")+"/null");
        // 生成自动配置文件
        write(cfg, model, "AutoConfigurationImports.ftl",
            RES_OUT + "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports");


        System.out.println("生成完成：" + MODULE_NAME);
        // System.out.println("Java文件位于：" + JAVA_OUT + toPath(BASE_PACKAGE));
    }

    /**
     * 根据模板和数据模型生成文件
     *
     * @param cfg      配置对象，用于获取模板
     * @param model    数据模型，用于填充模板
     * @param tpl      模板文件名
     * @param fullPath 输出文件的完整路径
     * @throws Exception 处理过程中可能抛出的异常
     */
    private static void write(Configuration cfg, Map<String, Object> model,
                              String tpl, String fullPath) throws Exception {
        // 获取模板并创建输出文件
        Template template = cfg.getTemplate(tpl);
        File outFile = new File(fullPath);
        outFile.getParentFile().mkdirs();

        // 处理模板并写入文件
        try (FileWriter w = new FileWriter(outFile)) {
            template.process(model, w);
        }
    }

}
