## 父模块配置
在父模块pom.xml的modules中添加子模块名称：
```
<module>${moduleName}</module>
```
## 模块bom配置
在模块bom的pom.xml中添加依赖及版本控制
```
<!-- ${moduleInfo} -->
<dependency>
    <groupId>${groupId}</groupId>
    <artifactId>${moduleName}</artifactId>
    <version>${r"${revision}"}</version>
</dependency>
```
