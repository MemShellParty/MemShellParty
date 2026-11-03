import org.gradle.api.Project

/** Versions of published modules that do not follow the root release version. */
object ModuleVersions {
    // 只有 BOM 跟随根版本，其余发布模块都在这里维护独立 release 基线。
    // 根版本为 SNAPSHOT 时，只有 publishIndependentModules 选中的模块追加 -SNAPSHOT。
    val independentBaseVersions: Map<String, String> = linkedMapOf(
        ":memshell-party-common" to "3.0.0",
        ":memshell-party-api" to "3.0.0",
        ":memshell-party-core" to "3.0.0",
        ":memshell-party-gui:memshell-party-gui-sdk" to "3.0.0",
        ":memshell-party-server:memshell-party-server-all" to "3.1.0",
        ":memshell-party-shelltool:memshell-party-shelltool-all" to "3.1.0",
        ":memshell-party-packer" to "3.1.0",
        ":memshell-party-server:memshell-party-server-servlet" to "3.1.0",
        ":memshell-party-server:memshell-party-server-spring-webmvc" to "3.0.0",
        ":memshell-party-server:memshell-party-server-spring-webflux" to "3.0.0",
        ":memshell-party-server:memshell-party-server-xxljob" to "3.0.0",
        ":memshell-party-server:memshell-party-server-struts2" to "3.0.0",
        ":memshell-party-server:memshell-party-server-dubbo" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-godzilla" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-behinder" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-command" to "3.1.0",
        ":memshell-party-shelltool:memshell-party-shelltool-suo5" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-antsword" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-neoregeorg" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-proxy" to "3.0.0",
        ":memshell-party-stub:memshell-party-stub-dubbo" to "3.0.0",
        ":memshell-party-stub:memshell-party-stub-jetty" to "3.0.0",
        ":memshell-party-stub:memshell-party-stub-tomcat" to "3.0.0",
    )

    fun selectedIndependentModules(project: Project): Set<String> {
        val selected = project.providers.gradleProperty("publishIndependentModules")
            .map { value -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
            .getOrElse(emptySet())
        val independentNames = independentBaseVersions.keys.mapTo(mutableSetOf(), ::artifactId)
        require(selected.all { it in independentNames }) {
            "publishIndependentModules contains unknown or non-independent modules: " +
                (selected - independentNames).joinToString()
        }
        return selected
    }

    fun versionFor(project: Project): String =
        versionFor(
            project.path,
            project.rootProject.version.toString(),
            selectedIndependentModules(project),
        )

    fun versionFor(
        projectPath: String,
        releaseVersion: String,
        selectedIndependentModules: Set<String>,
    ): String {
        val baseVersion = independentBaseVersions[projectPath] ?: return releaseVersion
        val isSelectedSnapshot = releaseVersion.endsWith("-SNAPSHOT") &&
            artifactId(projectPath) in selectedIndependentModules
        return if (isSelectedSnapshot) "$baseVersion-SNAPSHOT" else baseVersion
    }

    fun artifactId(projectPath: String): String = projectPath.substringAfterLast(':')
}
