import org.gradle.api.Project

/** Versions of published modules that do not follow the root release version. */
object ModuleVersions {
    // 未列出的发布模块跟随根版本：api、common、core、gui-sdk、server-all、shelltool-all 与 BOM。
    // 这里只保存 release 基线；根版本为 SNAPSHOT 时自动追加 -SNAPSHOT。
    val independentBaseVersions: Map<String, String> = linkedMapOf(
        ":memshell-party-packer" to "3.0.0",
        ":memshell-party-server:memshell-party-server-servlet" to "3.0.0",
        ":memshell-party-server:memshell-party-server-spring-webmvc" to "3.0.0",
        ":memshell-party-server:memshell-party-server-spring-webflux" to "3.0.0",
        ":memshell-party-server:memshell-party-server-xxljob" to "3.0.0",
        ":memshell-party-server:memshell-party-server-struts2" to "3.0.0",
        ":memshell-party-server:memshell-party-server-dubbo" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-godzilla" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-behinder" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-command" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-suo5" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-antsword" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-neoregeorg" to "3.0.0",
        ":memshell-party-shelltool:memshell-party-shelltool-proxy" to "3.0.0",
        ":memshell-party-stub:memshell-party-stub-dubbo" to "3.0.0",
        ":memshell-party-stub:memshell-party-stub-jetty" to "3.0.0",
        ":memshell-party-stub:memshell-party-stub-tomcat" to "3.0.0",
    )

    fun versionFor(project: Project): String =
        versionFor(project.path, project.rootProject.version.toString())

    fun versionFor(projectPath: String, releaseVersion: String): String {
        val baseVersion = independentBaseVersions[projectPath] ?: return releaseVersion
        return if (releaseVersion.endsWith("-SNAPSHOT")) "$baseVersion-SNAPSHOT" else baseVersion
    }

    fun artifactId(projectPath: String): String = projectPath.substringAfterLast(':')
}
