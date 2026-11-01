plugins {
    alias(libs.plugins.lombok)
    id("java8-library-conventions")
}

description = "Java deserialize payload for MemShellParty"

sourceSets {
    create("fixtures")
}

dependencies {
    api(project(":memshell-party-api"))
    "fixturesImplementation"(sourceSets.main.get().output)
    testImplementation(sourceSets["fixtures"].output)
    compileOnly(libs.metainf.services)
    annotationProcessor(libs.metainf.services)
    implementation(libs.bcel)
    implementation(libs.bundles.jna)
    implementation("com.caucho:hessian:4.0.66")
    implementation("commons-beanutils:commons-beanutils:1.9.2")
    implementation("commons-collections:commons-collections:3.2.1")
    implementation("org.apache.commons:commons-collections4:4.0")
    testImplementation(libs.junit.jupiter)
    testImplementation(project(":memshell-party-core"))
    testImplementation(libs.groovy)
    // 仅测试用 JSON 解析校验手写载荷；运行期不再依赖 jackson-databind
    testImplementation(libs.jackson.databind)
    testImplementation("com.h2database:h2:1.4.200")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<JavaCompile>("compileFixturesJava") {
    sourceCompatibility = "6"
    targetCompatibility = "6"
}

tasks.test {
    useJUnitPlatform {
        excludeTags("no-jna")
    }
}

/**
 * 模拟下游 exclude 掉 JNA 后的运行时：同一份测试类，只把 jna / jna-platform jar 从
 * 类路径里摘掉。注册表必须照常初始化，只跳过依赖 JNA 的 AgentJar 附加器。
 */
val noJnaTest by tasks.registering(Test::class) {
    description = "Run packer registry tests without JNA on the classpath"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath.filter {
        !it.name.startsWith("jna-") && !it.name.startsWith("jna-platform-")
    }
    useJUnitPlatform {
        includeTags("no-jna")
    }
}

tasks.check {
    dependsOn(noJnaTest)
}

/**
 * fileless Agent 运行时按 Java 8 编译，装配 payload 时由 FilelessPayloadAssembler 把副本降为
 * class-file 50；验证 fixture 在目标 JRE 6 上直接加载类文件，这里按同一规则降版本。
 */
val java6FilelessRuntime = tasks.register<Sync>("java6FilelessRuntime") {
    group = "verification"
    description = "Copies the fileless Agent runtime with class-file version 50 for JRE 6 fixtures."
    from(sourceSets.main.get().output) {
        include("com/reajason/javaweb/packer/jar/agent/fileless/**")
    }
    into(layout.buildDirectory.dir("generated/fileless-java6"))
    doLast {
        destinationDir.walkTopDown()
            .filter { it.extension == "class" }
            .forEach { file ->
                val bytes = file.readBytes()
                bytes[6] = 0
                bytes[7] = 50
                file.writeBytes(bytes)
            }
    }
}

val nativeFixtureJar = tasks.register<Jar>("nativeFixtureJar") {
    group = "verification"
    description = "Builds the no-javaagent Instrumentation and retransformation fixture."
    archiveBaseName.set("native-fileless-fixture")
    archiveVersion.set("")
    destinationDirectory.set(layout.buildDirectory.dir("verification"))
    manifest {
        attributes["Main-Class"] = "com.reajason.javaweb.packer.jar.agent.fileless.NativeAgentFixture"
    }
    from(java6FilelessRuntime)
    from(sourceSets["fixtures"].output) {
        include(
            "com/reajason/javaweb/packer/jar/agent/fileless/NativeAgentFixture*.class",
            "com/reajason/javaweb/packer/jar/agent/fileless/WindowsNativeAgentFixture.class",
            "com/reajason/javaweb/packer/jar/agent/fileless/WindowsPackedAgentFixture*.class",
            "com/reajason/javaweb/packer/jar/agent/fileless/RetransformFixture.class"
        )
    }
}

val windowsPackedFixtureJar = tasks.register<JavaExec>("windowsPackedFixtureJar") {
    group = "verification"
    description = "Builds the real packed-bootstrap Windows retransformation fixture."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.reajason.javaweb.packer.jar.agent.WindowsPackedFixtureGenerator")
    val filelessFixtureJar = nativeFixtureJar.flatMap { it.archiveFile }
    val outputJar = layout.buildDirectory.file("verification/windows-packed-fileless-fixture.jar")
    inputs.files(filelessFixtureJar)
    outputs.file(outputJar)
    doFirst {
        args(filelessFixtureJar.get().asFile, outputJar.get().asFile)
    }
}
