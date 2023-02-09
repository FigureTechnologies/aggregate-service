plugins {
    kotlin("jvm")

    application
    idea

    kotlin("plugin.serialization") version "1.5.30"
}

group = "tech.figure.augment"
version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"
val javaTarget = JavaVersion.VERSION_11
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

repositories {
    mavenCentral()
    maven { url = uri("https://javadoc.jitpack.io") }
}

dependencies {
    implementation(projects.common)

    implementation(libs.bundles.eventstream)
    implementation(libs.bundles.kotlin)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.kotlin.testcoroutines)
    implementation(libs.bundles.apache.commons)
    implementation(libs.bundles.scarlet)
    implementation(libs.datadog)
    implementation(libs.provenance.protos)
    implementation(libs.bundles.logback)
    implementation(libs.moshi.kotlin.codegen)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.json)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.logback.classic)
    implementation(libs.kotlin.serialization)
    implementation(libs.kotlin.core)
    implementation(libs.kotlin.guava)
    implementation(libs.kotlin.jdk8coroutines)
    implementation(libs.commons.dbutils)
    implementation(libs.exposed.core)

    runtimeOnly(libs.grpc.netty)
    testImplementation(libs.kotlin.test)

}

tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
    }
}

tasks.compileKotlin {
    kotlinOptions {
        jvmTarget = "11"
    }
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClassName = "tech.figure.augment.MainKt"
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "tech.figure.augment.MainKt"
    }
    isZip64 = true
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if(it.isDirectory) it else zipTree(it)}
    })


    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}
