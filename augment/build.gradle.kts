plugins {
    kotlin("jvm")

    application
    idea

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
    implementation(project(":common"))

    implementation("org.apache.commons", "commons-csv", Version.ApacheCommons.CSV)
    implementation("commons-io", "commons-io", Version.ApacheCommons.IO)
    implementation("org.apache.commons", "commons-lang3", Version.ApacheCommons.Lang3)

    implementation("io.provenance.protobuf:pb-proto-java:${Version.Provenance}")
    implementation("io.grpc:grpc-protobuf:${Version.GRPC}")
    implementation("io.grpc:grpc-stub:${Version.GRPC}")
    implementation("ch.qos.logback:logback-classic:1.0.13")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Version.Kotlinx.Core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:${Version.Kotlinx.Core}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:${Version.Kotlinx.Core}")

    // implementation("com.github.jasync-sql:jasync-postgresql:2.0.2")
    implementation("net.snowflake:snowflake-jdbc:3.13.8")
    implementation("commons-dbutils:commons-dbutils:1.7")

    implementation(platform("software.amazon.awssdk:bom:${Version.AWS}"))
    implementation("software.amazon.awssdk:s3")

    runtimeOnly("io.grpc:grpc-netty-shaded:${Version.GRPC}")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
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
