plugins {
    id("idea")
    id("maven-publish-all-convention")
    // apply false so all subprojects share one classloader for this plugin,
    // otherwise MavenCentralBuildService class identity differs across sibling projects
    id("com.vanniktech.maven.publish") version "0.35.0" apply false
}

idea {
    module {
        excludeDirs.add(file("src"))
    }
}

version = "3.1.0-SNAPSHOT"

allprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
}
