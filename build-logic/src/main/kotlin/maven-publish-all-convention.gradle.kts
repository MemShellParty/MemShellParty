val independentModuleNames = ModuleVersions.independentBaseVersions.keys
    .mapTo(mutableSetOf()) { ModuleVersions.artifactId(it) }
val selectedIndependentModules = providers.gradleProperty("publishIndependentModules")
    .map { value -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet() }
    .getOrElse(emptySet())
require(selectedIndependentModules.all { it in independentModuleNames }) {
    "publishIndependentModules contains unknown or non-independent modules: " +
        (selectedIndependentModules - independentModuleNames).joinToString()
}

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description = "Publishes unified modules and independent modules selected by publishIndependentModules"
    // Release 只发布统一版本模块、聚合模块和显式选择的独立模块；快照可覆盖写，始终全量发布。
    // The loop runs when this task is configured (task graph time) because the vanniktech
    // plugin registers publishToMavenCentral only during the subproject's afterEvaluate.
    subprojects.forEach { sub ->
        sub.plugins.withId("com.vanniktech.maven.publish") {
            val isIndependent = sub.path in ModuleVersions.independentBaseVersions
            val isSnapshot = rootProject.version.toString().endsWith("-SNAPSHOT")
            if (!isIndependent || isSnapshot || sub.name in selectedIndependentModules) {
                dependsOn(sub.tasks.named("publishToMavenCentral"))
            }
        }
    }
}

tasks.register("publishAllToMavenLocal") {
    group = "publishing"
    description = "Publishes every module that applies the maven-publish-convention to mavenLocal"
    subprojects.forEach { sub ->
        sub.plugins.withId("com.vanniktech.maven.publish") {
            dependsOn(sub.tasks.named("publishToMavenLocal"))
        }
    }
}
