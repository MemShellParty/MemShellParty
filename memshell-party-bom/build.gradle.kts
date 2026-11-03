plugins {
    `java-platform`
    id("maven-publish-convention")
}

description = "Bill of Materials for MemShellParty modules"
version = ModuleVersions.versionFor(project)

val releaseVersion = rootProject.version.toString()
val selectedIndependentModules = ModuleVersions.selectedIndependentModules(project)

dependencies {
    constraints {
        ModuleVersions.independentBaseVersions.forEach { (projectPath, _) ->
            val artifactId = ModuleVersions.artifactId(projectPath)
            val moduleVersion = ModuleVersions.versionFor(projectPath, releaseVersion, selectedIndependentModules)
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

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom.withXml {
            // Maven 的 dependencyManagement 按 type 匹配；聚合平台必须声明为 pom，
            // 否则消费方以 type=pom 引入时无法从本 BOM 继承版本。
            val aggregateModuleIds = setOf(
                "memshell-party-server-all",
                "memshell-party-shelltool-all",
            )
            val document = asElement().ownerDocument
            val dependencies = asElement().getElementsByTagName("dependency")
            for (index in 0 until dependencies.length) {
                val dependency = dependencies.item(index) as org.w3c.dom.Element
                val artifactId = dependency.getElementsByTagName("artifactId").item(0).textContent
                if (artifactId in aggregateModuleIds) {
                    dependency.appendChild(document.createElement("type").apply {
                        textContent = "pom"
                    })
                }
            }
        }
    }
}
