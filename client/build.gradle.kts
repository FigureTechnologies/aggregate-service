plugins{
    kotlin("jvm") version "1.8.21"
    `maven-publish`
    java
    idea
}

group = "tech.figure.aggregate.client"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(projects.proto)
    api(libs.bundles.grpc)
    api(libs.bundles.protobuf)
    api(libs.bundles.kotlin.coroutines)
}

val javaTarget = JavaVersion.VERSION_17
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
        jvmTarget = javaTarget.majorVersion
    }
}
