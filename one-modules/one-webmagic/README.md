## 父模块配置
在父模块pom.xml的modules中添加子模块
```
<module>one-webmagic</module>
```
## 模块全局配置
在最外层的pom.xml中添加依赖及版本控制
```
<!-- WebMagic模块 -->
<dependency>
    <groupId>me.liwncy</groupId>
    <artifactId>one-webmagic</artifactId>
    <version>${revision}</version>
</dependency>
```
## 启动模块配置
在启动模块的pom.xml中添加依赖
```
<!-- WebMagic模块 -->
<dependency>
    <groupId>me.liwncy</groupId>
    <artifactId>one-webmagic</artifactId>
</dependency>
```
