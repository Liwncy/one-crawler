package ${basePackage}.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import me.liwncy.common.mybatis.core.domain.BaseEntity;
import lombok.*;
<#assign hasDateTime = false>
<#assign hasBigDecimal = false>
<#list fields as field>
    <#if field.javaType == "LocalDateTime" || field.javaType == "LocalDate" || field.javaType == "LocalTime">
        <#assign hasDateTime = true>
    </#if>
    <#if field.javaType == "BigDecimal">
        <#assign hasBigDecimal = true>
    </#if>
</#list>

<#if hasDateTime>
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
</#if>
<#if hasBigDecimal>
import java.math.BigDecimal;
</#if>

/**
 * ${functionName}对象 ${tableName}
 *
 * @author ${author}
 * @date ${datetime}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("${tableName}")
public class ${className} extends BaseEntity {

<#list fields as f>
    /**
     * ${f.columnComment}
     */
    private ${f.javaType} ${f.javaName};
</#list>
}
