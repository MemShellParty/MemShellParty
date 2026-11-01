plugins {
    alias(libs.plugins.lombok)
    id("java8-library-conventions")
}

description = "MemShellParty servlet container server support"

dependencies {
    api(project(":memshell-party-api"))
    annotationProcessor(libs.metainf.services)
    compileOnly(libs.metainf.services)
    implementation(libs.javax.servlet.api)
    // 模板父类（org.apache.catalina.Valve、connector.Request/Response、coyote.UpgradeProtocol…）的生成期桩：
    // 生成引擎按字节解析模板父链时要能在运行时类路径上找到这些类（不会被加载进 JVM，桩接口够用）。
    // 真身 tomcat-embed-core 3.2 MiB，而这里只用到签名 —— 下游（GUI 等）因此不用背一整个内嵌 Tomcat
    implementation(project(":memshell-party-stub:memshell-party-stub-tomcat"))
    // Jetty 模板父类（org.eclipse.jetty.server.Handler 等）的编译期桩，理由同上
    implementation(project(":memshell-party-stub:memshell-party-stub-jetty"))
    // WebSocket 模板实现 JSR-356 javax.websocket.*，生成引擎按字节解析模板接口时需要这些
    // 签名在运行时类路径上（不会被加载；真身由容器自带）
    implementation(libs.javax.websocket.api)
    // Tests：本模块各 server/injector 与生成引擎（core）配合的验证
    testImplementation(project(":memshell-party-core"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.tomcat.embed.core)
    // 必须排在 javax.servlet-api 前：内嵌容器自带的 servlet 实现类要先于纯 API jar 命中。
    testImplementation(libs.javax.servlet.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}
