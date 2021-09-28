plugins {
    application
    idea

    id("tech.figure.augment.kotlin-application-conventions")

    kotlin("plugin.serialization") version "1.5.30"
}

group = "tech.figure.augment"
version = project.property("version")?.takeIf { it != "unspecified" } ?: "1.0-SNAPSHOT"

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

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.5.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")

    implementation("com.github.jasync-sql:jasync-postgresql:2.0.2")

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

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if(it.isDirectory) it else zipTree(it)}
    })


    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}
