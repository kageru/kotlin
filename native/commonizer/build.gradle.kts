plugins {
    kotlin("jvm")
    id("jps-compatible")
}

description = "Kotlin KLIB Library Commonizer"

publish()

dependencies {
    compileOnly(project(":compiler:cli-common"))
    compileOnly(project(":compiler:ir.serialization.common"))
    compileOnly(project(":compiler:frontend"))
    compileOnly(project(":native:frontend.native"))
    compileOnly(project(":kotlin-util-klib-metadata"))
    compileOnly(intellijCoreDep()) { includeJars("intellij-core") }

    // This dependency is necessary to keep the right dependency record inside of POM file:
    publishedCompile(project(":kotlin-compiler"))

    compile(kotlinStdlib())

    testCompile(commonDep("junit:junit"))
    testCompile(projectTests(":compiler:tests-common"))
    testCompile(project(":native:frontend.native"))

    testCompile(intellijCoreDep()) { includeJars("intellij-core") }
}

val runCommonizer by tasks.registering(NoDebugJavaExec::class) {
    classpath(sourceSets.main.get().runtimeClasspath)
    main = "org.jetbrains.kotlin.descriptors.commonizer.cli.CommonizerCLI"
}

sourceSets {
    "main" {
        projectDefault()
        runtimeClasspath += configurations.compileOnly
    }
    "test" { projectDefault() }
}

projectTest(parallel = true) {
    dependsOn(":dist")
    workingDir = rootDir
}

standardPublicJars()
