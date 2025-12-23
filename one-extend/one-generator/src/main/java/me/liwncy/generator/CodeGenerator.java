package me.liwncy.generator;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 代码生成器
 *
 */
public class CodeGenerator {

    // 数据库信息
    private static final String JDBC_URL =
            "jdbc:mysql://localhost:3306/one-crawler?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8" ;
    private static final String JDBC_USER = "root" ;
    private static final String JDBC_PWD = "root123" ;

    private static final String AUTHOR = "wanchang.li" ;

    // 基础包名 todo 根据项目结构调整
    private static final String TABLE_NAME = "sys_user" ;
    private static final String BASE_PACKAGE = "me.liwncy.demo" ;

    // ================= 修改开始：适配你的项目路径结构 =================
    // 项目输出目录
    private static final String PROJECT_PATH = getProjectPath();

    private static final String JAVA_OUT = PROJECT_PATH + "/src/main/java/" ;
    private static final String RES_OUT = PROJECT_PATH + "/src/main/resources/" ;

    /**
     * 自动适配路径：
     * 如果 user.dir 是在父工程 (backend)，则自动追加子模块路径。
     * 如果已经是子模块路径，则保持不变。
     */
    private static String getProjectPath() {
        String dir = System.getProperty("user.dir");
        // 统一把 Windows 的反斜杠 \ 替换为 / 以便判断
        String normalizedDir = dir.replace("\\", "/");

        // 如果路径里不包含 "enterprise-info-service"，说明是在父目录运行的，需要拼接
        // if (!normalizedDir.contains("enterprise-info-service")) {
        //     return dir + "/backend-services/enterprise-info-service";
        // }
        // todo 适配你的项目路径结构
        return dir + "/one-modules/one-demo" ;
    }
    // ================= 修改结束 =================

    public static void main(String[] args) throws Exception {
        System.out.println("当前输出根目录: " + PROJECT_PATH);
        // 传入表名
        generate();
    }

    public static void generate() throws Exception {

        // 1. 读取表结构
        // 1.1 查找表注释作为functionName
        String functionName = loadTable(TABLE_NAME).get("tableComment").toString();
        List<Map<String, Object>> columns = loadTableColumns(TABLE_NAME);

        // 2. 表名转类名
        String className = tableToClassName(TABLE_NAME);

        String moduleName = BASE_PACKAGE.substring(BASE_PACKAGE.lastIndexOf(".") + 1);

        // 3. 构建模板参数
        Map<String, Object> model = new HashMap<>();
        model.put("basePackage", BASE_PACKAGE);
        model.put("moduleName", moduleName);
        model.put("tableName", TABLE_NAME);
        model.put("functionName", functionName);
        model.put("className", className);
        model.put("lowerClassName", lowerFirst(className));
        model.put("fields", columns);
        model.put("author", AUTHOR);
        model.put("datetime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 4. 自动提取 company_id 列
        // for (Map<String, Object> col : columns) {
        //     if ("company_id".equals(col.get("column"))) {
        //         model.put("companyId", col.get("javaName"));
        //         break;
        //     }
        // }

        // 5. Freemarker 配置
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        // 注意：这里确保 templates 目录是在 resources 下
        cfg.setClassLoaderForTemplateLoading(CodeGenerator.class.getClassLoader(), "templates/gen");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);

        // 6. 生成文件
        write(cfg, model, "entity.ftl",
                JAVA_OUT + toPath(BASE_PACKAGE) + "/domain/entity/" + className + ".java");

        write(cfg, model, "bo.ftl",
                JAVA_OUT + toPath(BASE_PACKAGE) + "/domain/bo/" + className + "Bo.java");

        write(cfg, model, "vo.ftl",
                JAVA_OUT + toPath(BASE_PACKAGE) + "/domain/vo/" + className + "Vo.java");

        write(cfg, model, "controller.ftl",
                JAVA_OUT + toPath(BASE_PACKAGE) + "/controller/" + className + "Controller.java");

        write(cfg, model, "service.ftl",
                JAVA_OUT + toPath(BASE_PACKAGE) + "/service/" + className + "Service.java");

        write(cfg, model, "mapper.ftl",
                JAVA_OUT + toPath(BASE_PACKAGE) + "/mapper/" + className + "Mapper.java");

        write(cfg, model, "mapperXml.ftl",
                RES_OUT + "mapper/" + className + "Mapper.xml");

        System.out.println("生成完成：" + TABLE_NAME);
        System.out.println("Java文件位于：" + JAVA_OUT + toPath(BASE_PACKAGE));
    }

    /**
     * 根据模板和数据模型生成文件
     *
     * @param cfg 配置对象，用于获取模板
     * @param model 数据模型，用于填充模板
     * @param tpl 模板文件名
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


    /**
     * 加载指定表信息
     *
     * @param table 表名
     * @return 包含表信息的Map列表
     * @throws Exception 数据库连接或查询异常
     */
    private static Map<String, Object> loadTable(String table) throws Exception {
        Map<String, Object> tabInfo = new HashMap<>();
        // 建立数据库连接并查询表的列信息
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PWD)) {
            PreparedStatement ps = conn.prepareStatement(
                    "select table_name,table_comment from information_schema.tables " +
                            "where table_schema = database() and table_name = ? "
            );
            ps.setString(1, table);
            ResultSet rs = ps.executeQuery();
            // 遍历结果集
            while (rs.next()) {
                tabInfo.put("tableName", rs.getString("table_name"));
                tabInfo.put("tableComment", toCamel(rs.getString("table_comment")));
            }
        }
        return tabInfo;
    }

    /**
     * 加载指定表的列信息
     *
     * @param table 表名
     * @return 包含列信息的Map列表，每个Map包含column(列名)、javaName(Java命名)、javaType(Java类型)三个键值对
     * @throws Exception 数据库连接或查询异常
     */
    private static List<Map<String, Object>> loadTableColumns(String table) throws Exception {
        List<Map<String, Object>> list = new ArrayList<>();
        // 建立数据库连接并查询表的列信息
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PWD)) {
            PreparedStatement ps = conn.prepareStatement(
                    "select column_name,column_comment,data_type from information_schema.columns " +
                            "where table_schema = database() and table_name = ? order by ordinal_position"
            );
            ps.setString(1, table);
            ResultSet rs = ps.executeQuery();
            // 遍历查询结果，将列信息转换为Map并添加到列表中
            while (rs.next()) {
                Map<String, Object> col = new HashMap<>();
                col.put("column", rs.getString("column_name"));
                col.put("javaName", toCamel(rs.getString("column_name")));
                col.put("columnComment", rs.getString("column_comment"));
                col.put("javaType", sqlTypeToJava(rs.getString("data_type")));
                list.add(col);
            }
        }
        return list;
    }


    /**
     * 将数据库表名转换为Java类名
     * 采用下划线分隔命名法转驼峰命名法的转换规则
     *
     * @param table 数据库表名，通常采用下划线分隔的命名方式
     * @return 转换后的Java类名，采用驼峰命名法
     */
    private static String tableToClassName(String table) {
        // 按下划线分割表名
        String[] parts = table.split("_");
        StringBuilder sb = new StringBuilder();
        // 将每个部分的首字母大写后拼接
        for (String p : parts) sb.append(upperFirst(p));
        return sb.toString();
    }


    /**
     * 将下划线命名转换为驼峰命名
     *
     * @param name 下划线格式的字符串，如 "user_name"
     * @return 驼峰格式的字符串，如 "userName"
     */
    private static String toCamel(String name) {
        // 按下划线分割字符串
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder(parts[0]);

        // 将除第一部分外的其他部分首字母大写后拼接
        for (int i = 1; i < parts.length; i++) {
            sb.append(upperFirst(parts[i]));
        }

        return sb.toString();
    }


    /**
     * 将SQL数据类型转换为对应的Java数据类型
     *
     * @param t SQL数据类型字符串
     * @return 对应的Java数据类型字符串
     */
    private static String sqlTypeToJava(String t) {
        // 根据不同的SQL数据类型返回相应的Java类型
        switch (t) {
            case "varchar":
            case "text":
            case "char":
                return "String" ;
            case "decimal":
            case "numeric":
                return "BigDecimal" ;
            case "datetime":
            case "timestamp":
                return "LocalDateTime" ;
            case "date":
                return "LocalDate" ;
            case "bigint":
                return "Long" ;
            case "int":
                return "Integer" ;
            default:
                return "String" ;
        }
    }


    /**
     * 将字符串的第一个字符转换为大写
     *
     * @param s 输入的字符串
     * @return 首字符大写的字符串
     */
    private static String upperFirst(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }


    /**
     * 将字符串的第一个字符转换为小写
     *
     * @param s 输入的字符串
     * @return 首字符为小写的字符串，如果输入为空则返回原字符串
     */
    private static String lowerFirst(String s) {
        return s.substring(0, 1).toLowerCase() + s.substring(1);
    }


    /**
     * 将包名转换为路径格式
     *
     * @param pkg 包名字符串，使用点号分隔
     * @return 转换后的路径字符串，使用斜杠分隔
     */
    private static String toPath(String pkg) {
        return pkg.replace(".", "/");
    }

}
