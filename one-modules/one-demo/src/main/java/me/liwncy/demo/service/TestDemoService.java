package me.liwncy.demo.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.liwncy.demo.domain.entity.TestDemo;
import me.liwncy.demo.mapper.TestDemoMapper;
import org.springframework.stereotype.Service;

/**
 * 测试单表 Service
 *
 * @author wanchang.li
 * @date 2026-06-02 15:19:16
 */
@Service
public class TestDemoService extends ServiceImpl<TestDemoMapper, TestDemo> {

}
