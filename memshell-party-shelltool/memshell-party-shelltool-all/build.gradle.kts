plugins {
    `java-platform`
    id("maven-publish-convention")
}

group = "io.github.reajason"
version = ModuleVersions.versionFor(project)
description = "BOM for all built-in shell tool providers"

javaPlatform {
    // 保留直接依赖本模块即带入全部 provider 的行为；发布产物只有 POM 与 Gradle metadata。
    allowDependencies()
}

val providers = listOf(
    ":memshell-party-shelltool:memshell-party-shelltool-godzilla",
    ":memshell-party-shelltool:memshell-party-shelltool-behinder",
    ":memshell-party-shelltool:memshell-party-shelltool-command",
    ":memshell-party-shelltool:memshell-party-shelltool-suo5",
    ":memshell-party-shelltool:memshell-party-shelltool-antsword",
    ":memshell-party-shelltool:memshell-party-shelltool-neoregeorg",
    ":memshell-party-shelltool:memshell-party-shelltool-proxy",
)

dependencies {
    providers.forEach { providerPath ->
        api(project(providerPath))
        constraints { api(project(providerPath)) }
    }
}
