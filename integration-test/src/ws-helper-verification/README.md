# WsHelper 一次性真实容器校验

这个源码集**不属于 CI**：`wsHelperVerification` 没有挂到 `test` / `check`，只有显式执行时才会编译和运行。

```bash
# 全部 profile
./gradlew :integration-test:wsHelperVerification

# 只跑某几个 profile
./gradlew :integration-test:wsHelperVerification -PwsProfiles=tomcat-8.5-nio,jetty-9.4
```

用途：在真实中间件里注入 `Proxy` + `RAW_WEBSOCKET`，用真实 CONNECT 隧道验证 WsHelper 的
接管、帧循环、目标 socket 回收、泵线程退出以及业务请求可用性。每个 profile 独立起一套
network + echo sidecar + 中间件容器，互不影响。

## 当前覆盖

| profile | 镜像 | 覆盖的 helper |
|---|---|---|
| tomcat-8.5-nio | `tomcat:8-jre8` | TomcatRawWsHelper（Wrapper/NIO） |
| tomcat-10.1-jakarta | `tomcat:10.1-jre11` | TomcatRawWsHelper（Jakarta） |
| tomcat-5-bio | `reajason/tomcat:5-jdk6` | TomcatRawWsHelper（BIO） |
| jetty-9.4 | `reajason/jetty:9.4-jre8` | JettyRawWsHelper |
| undertow-springboot2 | 本地构建的 `springboot2-undertow:verify` | UndertowRawWsHelper |
| resin-4.0.67 | `reajason/resin:4.0.67-jdk11` | ResinRawWsHelper |
| glassfish-5.1.0 | `reajason/glassfish:5.1.0` | GlassFishRawWsHelper |
| weblogic-15.1.1.0 | `reajason/weblogic:15.1.1.0-jdk21` | WebLogicRawWsHelper |
| websphere-9.0.5.17 | `reajason/websphere:9.0.5.17` | WebSphereRawWsHelper |

BES 9.5.5、TongWeb 8.0.6.2、Apusic 9.0.1、InforSuite 10 的镜像都来自内网，**不放进本源码集**，
避免把内网镜像地址提交到仓库。BES/TongWeb 是重打包的 Tomcat，已由 Tomcat 8.5/10.1/5 覆盖；
GlassFish helper 由公开的 GlassFish 5.1 覆盖；只有 ApusicRawWsHelper 因此没有运行覆盖，保留静态分析结论。

`resin` / `glassfish` / `weblogic` / `websphere` 四个 profile 只查本机镜像，不触发拉取；
镜像不存在时记为 **skipped**（原因写在报告里）。本机已导入这四个镜像，本轮全部真实执行并通过。

`undertow-springboot2` 使用 `vul/vul-springboot2-undertow` 的 bootJar，配一个本地
`eclipse-temurin:17` 镜像运行；该模块的 `/b64` 控制器改成了用应用自身类加载器 define，
否则在 Spring Boot 嵌套 jar 下注入类看不到 `javax.servlet`。

## 单 profile 检查项

1. 注入前后业务页面 200；
2. **同一 shell 端点上反复建连/使用/关闭**：每次都是新的 WebSocket 连接，走完整 CONNECT→echo→关闭，
   默认 10 轮，每轮后目标连接和 `rawws-pump` 线程必须归零；可用 `-PwsCycles=N` 加压；
3. 客户端直接关底层 TCP，目标连接和 `rawws-pump` 线程归零；
4. CONNECT 不可达目标返回 `503` 并关闭 WebSocket；
5. 2 路并发隧道各自独立、不串流；
6. 全部会话结束后业务页面仍 200。

注入默认走 `DefaultBase64`，失败自动回退 `JSP`；`undertow-springboot2` 只走 `DefaultBase64`
（Spring Boot 应用没有 `/upload`）。

## 本轮结果

```bash
./gradlew :integration-test:wsHelperVerification -PwsCycles=20
```

```
tests=9 skipped=0 failures=0 errors=0  time=145.617  BUILD SUCCESSFUL in 2m 27s
```

9 个 profile 全部真实容器通过，每个 profile 在同一 shell 端点上重复 20 轮
建连/使用/关闭，共 180 轮，覆盖 7 个 WsHelper 实现。

## 镜像准备

四个较重的 profile 需要本机已有镜像：

```bash
docker pull reajason/resin:4.0.67-jdk11
docker pull reajason/glassfish:5.1.0
docker pull reajason/weblogic:15.1.1.0-jdk21
docker pull reajason/websphere:9.0.5.17
```

镜像约 1–4GB；Docker Hub 不稳时大层可能 `unexpected EOF` / TLS 超时，重试会复用已完成层。

