val selectedIndependentModules = ModuleVersions.selectedIndependentModules(project)

tasks.register("publishAllToMavenCentral") {
    group = "publishing"
    description = "Publishes root-versioned modules and independent modules selected by publishIndependentModules"
    // Release 与 SNAPSHOT 使用相同发布集合：跟随根版本的模块和显式选择的独立模块。
    // The loop runs when this task is configured (task graph time) because the vanniktech
    // plugin registers publishToMavenCentral only during the subproject's afterEvaluate.
    subprojects.forEach { sub ->
        sub.plugins.withId("com.vanniktech.maven.publish") {
            val isIndependent = sub.path in ModuleVersions.independentBaseVersions
            if (!isIndependent || sub.name in selectedIndependentModules) {
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
