<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>${groupId}</groupId>
        <artifactId>${parentModule}</artifactId>
        <version>${r"${revision}"}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>${moduleName}</artifactId>

    <description>
        ${moduleName}  ${moduleInfo}
    </description>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <source>16</source>
                    <target>16</target>
                </configuration>
            </plugin>
        </plugins>
    </build>

    <dependencies>
        <!-- 核心模块 -->
        <dependency>
            <groupId>me.liwncy</groupId>
            <artifactId>one-common-core</artifactId>
        </dependency>

    </dependencies>

</project>
