@echo off

:: 创建输出目录
mkdir -p target/classes

:: 编译Java文件
javac -d target/classes -cp "src/main/java" src/main/java/com/rushCT/RushCT.java

:: 复制资源文件
xcopy src\main\resources target\classes /s /e

:: 打包成jar文件
jar cvf target/RushCT.jar -C target/classes .

echo 构建完成！插件jar文件位于 target/RushCT.jar
pause