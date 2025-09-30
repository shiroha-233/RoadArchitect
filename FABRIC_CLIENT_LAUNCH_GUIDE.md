# Fabric客户端启动指南

## 🚨 当前问题
由于网络证书问题，Gradle无法下载依赖，导致无法直接使用 `gradlew runClient` 命令。

## 🔧 解决方案

### 方案1: 修复网络问题
```bash
# 设置Gradle使用HTTP而不是HTTPS (临时解决方案)
cd modules/fabric
echo "systemProp.https.protocols=TLSv1.2,TLSv1.1,TLSv1" >> gradle.properties
echo "org.gradle.jvmargs=-Djavax.net.ssl.trustStore=cacerts" >> gradle.properties

# 然后重试
../../gradlew runClient --no-daemon
```

### 方案2: 使用IDE启动
1. 用IntelliJ IDEA或Eclipse打开项目
2. 导入Gradle项目
3. 找到Fabric模块的运行配置
4. 直接在IDE中运行客户端

### 方案3: 手动启动Minecraft开发环境
```bash
cd modules/fabric/run

# 使用已有的Minecraft启动器，添加以下JVM参数：
-Dfabric.development=true
-Dfabric.remapClasspathFile=.fabric/remapClasspathFile
-javaagent:fabric-loader.jar
```

### 方案4: 检查现有配置
从日志可以看出之前成功运行过，检查是否有现有的启动脚本：