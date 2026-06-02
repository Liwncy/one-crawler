package me.liwncy.demo.controller;

import me.liwncy.common.web.core.BaseController;
import me.liwncy.demo.service.TestDemoService;
import me.liwncy.demo.domain.bo.TestDemoBo;
import me.liwncy.demo.domain.vo.TestDemoVo;
import me.liwncy.demo.domain.entity.TestDemo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试单表
 *
 * @author wanchang.li
 * @date 2026-06-02 15:19:16
 */
@RestController
@RequestMapping("/demo/testDemo")
@RequiredArgsConstructor
public class TestDemoController extends BaseController {


}
