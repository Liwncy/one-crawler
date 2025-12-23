package ${basePackage}.controller;

import me.liwncy.common.web.core.BaseController;
import ${basePackage}.service.${className}Service;
import ${basePackage}.domain.bo.${className}Bo;
import ${basePackage}.domain.vo.${className}Vo;
import ${basePackage}.domain.entity.${className};
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ${functionName}
 *
 * @author ${author}
 * @date ${datetime}
 */
@RestController
@RequestMapping("/${moduleName}/${lowerClassName}")
@RequiredArgsConstructor
public class ${className}Controller extends BaseController {


}
