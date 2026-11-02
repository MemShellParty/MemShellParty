plugins {
    `java-platform`
    id("maven-publish-convention")
}

description = "Bill of Materials for MemShellParty modules"
version = ModuleVersions.versionFor(project)

val releaseVersion = rootProject.version.toString()
val releaseModules = listOf(
    "memshell-party-common",
    "memshell-party-api",
    "memshell-party-core",
    "memshell-party-gui-sdk",
    "memshell-party-server-all",
    "memshell-party-shelltool-all",
)

dependencies {
    constraints {
        releaseModules.forEach { artifactId ->
            api("io.github.reajason:$artifactId:$releaseVersion")
        }
        ModuleVersions.independentBaseVersions.forEach { (projectPath, _) ->
            val artifactId = ModuleVersions.artifactId(projectPath)
            val moduleVersion = ModuleVersions.versionFor(projectPath, releaseVersion)
            if (artifactId.startsWith("memshell-party-stub-")) {
                // 防止 server 的传递依赖把未变化的桩升级到本次发行版版本。
                api("io.github.reajason:$artifactId") {
                    version { strictly(moduleVersion) }
                }
            } else {
                api("io.github.reajason:$artifactId:$moduleVersion")
            }
        }
    }
}
