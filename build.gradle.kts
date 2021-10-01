import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

object Version {
    const val Arrow = "0.12.1"

    object ApacheCommons {
        const val CSV = "1.9.0"
        const val Text = "1.9"
        const val IO = "2.11.0"
    }

    const val AWS = "2.17.40"
    const val DatadogStats = "2.13.0"
    const val GRPC = "1.39.0"
    const val Hoplite = "1.4.7"
    const val JUnit = "5.1.0"
    const val JUnitPioneer = "1.4.2"
    const val JSON = "20210307"

    object Kotlinx {
        const val Core = "1.5.2"
        const val CLI = "0.3.3"
    }

    const val Logback = "0.1.5"
    const val Moshi = "1.12.0"
    const val Provenance = "1.5.0"
    const val Scarlet = "0.1.12"
    const val AWSSDK = "2.17.32"
    const val LocalStack = "0.2.15"
}

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
    // All dependencies in the `org.jetbrains.kotlin` package will use the version of kotlin defined in
    // `gradle.properties`: used to pin the org.jetbrains.kotlin.{jvm,kapt} plugin versions in `settings.gradle.kts`.
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-reflect")

    implementation("org.jetbrains.kotlinx", "kotlinx-cli-jvm", Version.Kotlinx.CLI)
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

    implementation("com.tinder.scarlet", "scarlet", Version.Scarlet)
    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Version.Scarlet)
    implementation("com.tinder.scarlet", "websocket-okhttp", Version.Scarlet)
    implementation("com.tinder.scarlet", "message-adapter-moshi", Version.Scarlet)

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.datadoghq", "java-dogstatsd-client", Version.DatadogStats)

    implementation("io.grpc", "grpc-alts", Version.GRPC)
    implementation("io.grpc", "grpc-netty", Version.GRPC)
    implementation("io.grpc", "grpc-protobuf", Version.GRPC)
    implementation("io.grpc", "grpc-stub", Version.GRPC)

    implementation("io.provenance.protobuf", "pb-proto-java", Version.Provenance)

    implementation("ch.qos.logback.contrib", "logback-json-core", Version.Logback)
    implementation("ch.qos.logback.contrib", "logback-json-classic", Version.Logback)

    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Version.Moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Version.Moshi}")

    implementation("com.sksamuel.hoplite", "hoplite-core", Version.Hoplite)

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

kapt {
    correctErrorTypes = true
}

project.afterEvaluate {
    // Force generation of the API and models based on the
    tasks.get("kaptGenerateStubsKotlin").dependsOn("generateTendermintAPI")
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

/**
 * See the following links for information about generating models from an OpenAPI spec:
 * - https://github.com/OpenAPITools/openapi-generator/tree/master/modules/openapi-generator-gradle-plugin
 * - https://github.com/OpenAPITools/openapi-generator/blob/master/docs/global-properties.md
 * - https://github.com/OpenAPITools/openapi-generator/blob/master/docs/generators/kotlin.md
 */
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateTendermintAPI") {
    generatorName.set("kotlin")
    verbose.set(false)
    validateSpec.set(true)
    inputSpec.set(TENDERMINT_OPENAPI_YAML)
    outputDir.set("$buildDir/generated")
    packageName.set("io.provenance.aggregate.service.stream")
    modelPackage.set("io.provenance.aggregate.service.stream.models")
    library.set("jvm-okhttp4")
//library.set("jvm-retrofit2")
    configOptions.set(
        mapOf(
            "artifactId" to "tendermint-api",
            "dateLibrary" to "java8",
            "moshiCodeGen" to true.toString(),
            "modelMutable" to false.toString(),
            "serializableModel" to true.toString(),
            "serializationLibrary" to "moshi",
            "useCoroutines" to true.toString()
        )
    )
//    globalProperties.set(
//        mapOf(
//            "apis" to "false",
//            "models" to "",
//            "modelDocs" to ""
//        )
//    )
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
