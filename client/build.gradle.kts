plugins{
    kotlin("jvm") version "1.9.21"
    `maven-publish`
    java
    idea
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
