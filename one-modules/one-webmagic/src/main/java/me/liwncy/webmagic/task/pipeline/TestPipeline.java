package me.liwncy.webmagic.task.pipeline;


import org.springframework.stereotype.Component;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

/**
 * @description 自定义的认可的检验能力范围pipeline通道
 */
@Component
public class TestPipeline implements Pipeline {


    @Override
    public void process(ResultItems resultItems, Task task) {
        //
        System.out.println(resultItems);
    }
}
