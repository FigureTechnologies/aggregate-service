import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    idea

    id("tech.figure.augment.kotlin-application-conventions")
}

group = "tech.figure.augment"
version = "1.0-SNAPSHOT"

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://javadoc.jitpack.io") }
}

dependencies {
    implementation("io.provenance.protobuf:pb-proto-java:1.7.0")
    implementation("io.grpc:grpc-protobuf:1.39.0")
    implementation("io.grpc:grpc-stub:1.39.0")
    implementation("ch.qos.logback:logback-classic:1.0.13")

    runtimeOnly("io.grpc:grpc-netty-shaded:1.39.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
    }
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClassName = "tech.figure.augment.MainKt"
}
