package me.liwncy.common.webmagic.pipeline;

import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;
import us.codecraft.webmagic.utils.FilePersistentBase;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 默认的json文件输出
 * @author code4crafer@gmail.com
 */
public class OneJsonFilePipeline extends FilePersistentBase implements Pipeline {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private final PrintWriter printWriter;

    public OneJsonFilePipeline() throws FileNotFoundException, UnsupportedEncodingException {
        this("D:\\webmagic\\");
    }

    public OneJsonFilePipeline(String path) throws FileNotFoundException, UnsupportedEncodingException {
        setPath(path);
        printWriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(getFile(path)), StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void process(ResultItems resultItems, Task task) {
        // 使用 Hutool 的 JSONUtil 将 Map 转换为 JSON 字符串
        String json = JSONUtil.toJsonStr(resultItems.getAll());
        printWriter.println(json); // 写入 JSON 数据
        printWriter.println(","); // 写入","分隔
        printWriter.flush();
    }
}
