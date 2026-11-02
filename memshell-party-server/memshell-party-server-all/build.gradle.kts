plugins {
    `java-platform`
    id("maven-publish-convention")
}

group = "io.github.reajason"
version = ModuleVersions.versionFor(project)
description = "BOM for all built-in server providers"

javaPlatform {
    // 保留直接依赖本模块即带入全部 provider 的行为；发布产物只有 POM 与 Gradle metadata。
    allowDependencies()
}

val providers = listOf(
    ":memshell-party-server:memshell-party-server-servlet",
    ":memshell-party-server:memshell-party-server-spring-webmvc",
    ":memshell-party-server:memshell-party-server-spring-webflux",
    ":memshell-party-server:memshell-party-server-xxljob",
    ":memshell-party-server:memshell-party-server-struts2",
    ":memshell-party-server:memshell-party-server-dubbo",
)

dependencies {
    providers.forEach { providerPath ->
        api(project(providerPath))
        constraints { api(project(providerPath)) }
    }
}
