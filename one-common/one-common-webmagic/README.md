## 父模块配置
在父模块pom.xml的modules中添加子模块名称：
```
<module>one-common-webmagic</module>
```
## 模块bom配置
在模块bom的pom.xml中添加依赖及版本控制
```
<!-- 爬虫服务 -->
<dependency>
    <groupId>me.liwncy</groupId>
    <artifactId>one-common-webmagic</artifactId>
    <version>${revision}</version>
</dependency>
```
