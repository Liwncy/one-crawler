package me.liwncy.webmagic.task.pipeline;


import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpUtil;
import org.springframework.stereotype.Component;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.util.Arrays;
import java.util.List;

/**
 * @description 自定义的认可的检验能力范围pipeline通道
 */
@Component
public class WeiXinBQBPipeline implements Pipeline {

    static String BASE_DOWN_URL = "D:\\weixin\\";

    @Override
    public void process(ResultItems resultItems, Task task) {
        String title = StrUtil.cleanBlank(resultItems.get("title"));
        String[] urlArr = StrUtil.cleanBlank(resultItems.get("url")).split("/");
        String uid = urlArr[urlArr.length - 1];
        if (title.contains("|")) {
            title = title.split("\\|")[1];
        }
        List<String> picList = resultItems.get("picList");
        for (int i = 0; i < picList.size(); i++) {
            String picUrl = picList.get(i);
            String downUrl = BASE_DOWN_URL + StrUtil.cleanBlank(title) + "\\";
            String picName = title + "_" + StrUtil.padPre((i + 1) + "",3, picList.size() + "") + "." + ReUtil.getGroup1("wx_fmt=([a-zA-Z0-9]+)", picUrl);
            System.out.println(downUrl + picName);
            HttpUtil.downloadFile(picList.get(i), downUrl + picName);
        }
    }
}
