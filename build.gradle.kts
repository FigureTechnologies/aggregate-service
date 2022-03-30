plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.protobuf") version "0.8.17"
    id("org.openapi.generator") version "5.2.1"
    application
    idea
}

group = "io.provenance.tech.aggregate"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

val TENDERMINT_OPENAPI_YAML = "$rootDir/src/main/resources/tendermint-v0.34.12-rpc-openapi-FIXED.yaml"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":common"))
    implementation(project(":l2-cache"))

    // All dependencies in the `org.jetbrains.kotlin` package will use the version of kotlin defined in
    // `gradle.properties`: used to pin the org.jetbrains.kotlin.{jvm,kapt} plugin versions in `settings.gradle.kts`.
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-reflect")

    implementation("org.jetbrains.kotlinx", "kotlinx-cli-jvm", Version.Kotlinx.CLI)
    implementation("org.jetbrains.kotlinx", "kotlinx-datetime", Version.Kotlinx.DateTime)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Version.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Version.Kotlinx.Core)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-reactive", Version.Kotlinx.Core)

    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", Version.Kotlinx.Core)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", Version.JUnit)
    testImplementation("org.apache.commons", "commons-text", Version.ApacheCommons.Text)
    testImplementation("org.junit-pioneer", "junit-pioneer", Version.JUnitPioneer)

    implementation("io.arrow-kt", "arrow-core", Version.Arrow)

    implementation("org.apache.commons", "commons-csv", Version.ApacheCommons.CSV)
    implementation("commons-io", "commons-io", Version.ApacheCommons.IO)
    implementation("org.apache.commons", "commons-lang3", Version.ApacheCommons.Lang3)

    implementation("com.tinder.scarlet", "scarlet", Version.Scarlet)
    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Version.Scarlet)
    implementation("com.tinder.scarlet", "websocket-okhttp", Version.Scarlet)
    implementation("com.tinder.scarlet", "message-adapter-moshi", Version.Scarlet)

    implementation("com.datadoghq", "java-dogstatsd-client", Version.DatadogStats)

    implementation("io.provenance.protobuf", "pb-proto-java", Version.Provenance)

    implementation("ch.qos.logback.contrib", "logback-json-core", Version.Logback)
    implementation("ch.qos.logback.contrib", "logback-json-classic", Version.Logback)

    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Version.Moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Version.Moshi}")

    implementation("com.sksamuel.hoplite", "hoplite-core", Version.Hoplite)
    implementation("com.sksamuel.hoplite", "hoplite-yaml", Version.Hoplite)

    implementation("org.json", "json", Version.JSON)

    implementation(platform("software.amazon.awssdk:bom:${Version.AWS}"))
    implementation("software.amazon.awssdk:netty-nio-client")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:dynamodb-enhanced")

    implementation("cloud.localstack", "localstack-utils", Version.LocalStack)
}

sourceSets {
    main {
        java {
            srcDirs(
                "$projectDir/src/main/kotlin",
                "$buildDir/generated/src/main/kotlin"
            )
        }
    }
    test {
        java {
            srcDir("$projectDir/src/test/kotlin")
        }
    }
}

application {
    mainClass.set("io.provenance.aggregate.service.MainKt")
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "11"
    }
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

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "io.provenance.aggregate.service.MainKt"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })


    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}
