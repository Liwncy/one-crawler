## 父模块配置
在父模块pom.xml的modules中添加子模块
```
<module>${moduleName}</module>
```
## 模块全局配置
在最外层的pom.xml中添加依赖及版本控制
```
<!-- ${moduleInfo} -->
<dependency>
    <groupId>${groupId}</groupId>
    <artifactId>${moduleName}</artifactId>
    <version>${r"${revision}"}</version>
</dependency>
```
## 启动模块配置
在启动模块的pom.xml中添加依赖
```
<!-- ${moduleInfo} -->
<dependency>
    <groupId>${groupId}</groupId>
    <artifactId>${moduleName}</artifactId>
</dependency>
```
