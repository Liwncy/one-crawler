package me.liwncy.spiders.controller;

import lombok.RequiredArgsConstructor;
import me.liwncy.common.core.domain.R;
import me.liwncy.spiders.domain.vo.SpiderTaskInfo;
import me.liwncy.spiders.service.SpiderTaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 最小可用的爬虫任务管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/spiders")
public class SpiderTaskController {

    private final SpiderTaskService spiderTaskService;

    @GetMapping
    public R<List<SpiderTaskInfo>> list() {
        return R.ok(spiderTaskService.listSpiders());
    }

    @GetMapping("/{name}")
    public R<SpiderTaskInfo> detail(@PathVariable String name) {
        return R.ok(spiderTaskService.getSpider(name));
    }

    @PostMapping("/run/{name}")
    public R<SpiderTaskInfo> run(@PathVariable String name) {
        spiderTaskService.run(name);
        return R.ok("spider run finished: " + name, spiderTaskService.getSpider(name));
    }
}

