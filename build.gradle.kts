plugins {
    kotlin("jvm") version "1.5.21"
    kotlin("plugin.spring") version "1.5.21"
    id("org.jetbrains.kotlin.kapt") version "1.5.21"
    id("com.google.protobuf") version "0.8.17"
    id("org.openapi.generator") version "5.2.1"
//    id("org.springframework.boot") version "2.5.3"
//    id("io.spring.dependency-management") version "1.0.11.RELEASE"
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
    maven {
        url = uri("https://nexus.figure.com/repository/mirror")
        credentials {
            username = (project.properties["nexusUser"] ?: System.getenv("NEXUS_USER")) as String?
            password = (project.properties["nexusPass"] ?: System.getenv("NEXUS_PASS")) as String?
        }
    }
    maven {
        url = uri("https://nexus.figure.com/repository/figure")
        credentials {
            username = (project.properties["nexusUser"] ?: System.getenv("NEXUS_USER")) as String?
            password = (project.properties["nexusPass"] ?: System.getenv("NEXUS_PASS")) as String?
        }
    }
}

object Version {
    val arrow = "0.12.1"
    val apacheCommons = "1.9"
    val aws = "2.15.0"
    val grpc = "1.39.0"
    val hoplite = "1.4.7"
    val junit = "5.1.0"
    val kotlinx = "1.5.0"
    val logback = "0.1.5"
    val moshi = "1.12.0"
    val provenance = "1.5.0"
    val scarlet = "0.1.12"
    val awsSdk = "2.17.32"
    val localstack = "0.2.15"
}

dependencies {
    implementation("org.jetbrains.kotlin", "kotlin-stdlib")
    implementation("org.jetbrains.kotlin", "kotlin-reflect", Version.kotlinx)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Version.kotlinx)
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8", Version.kotlinx)

    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", Version.kotlinx)
    testImplementation("org.jetbrains.kotlinx", "kotlinx-coroutines-test", Version.kotlinx)
    testImplementation("org.junit.jupiter", "junit-jupiter-engine", Version.junit)
    testImplementation("org.apache.commons", "commons-text", Version.apacheCommons)

    implementation("io.arrow-kt", "arrow-core", Version.arrow)

    implementation("com.tinder.scarlet", "scarlet", Version.scarlet)
    implementation("com.tinder.scarlet", "stream-adapter-coroutines", Version.scarlet)
    implementation("com.tinder.scarlet", "websocket-okhttp", Version.scarlet)
    implementation("com.tinder.scarlet", "message-adapter-moshi", Version.scarlet)

    implementation("io.grpc", "grpc-alts", Version.grpc)
    implementation("io.grpc", "grpc-netty", Version.grpc)
    implementation("io.grpc", "grpc-protobuf", Version.grpc)
    implementation("io.grpc", "grpc-stub", Version.grpc)

    implementation("io.provenance.protobuf", "pb-proto-java", Version.provenance)

    implementation("ch.qos.logback.contrib", "logback-json-core", Version.logback)
    implementation("ch.qos.logback.contrib", "logback-json-classic", Version.logback)

    implementation("com.squareup.moshi", "moshi-kotlin-codegen", Version.moshi)
    kapt("com.squareup.moshi:moshi-kotlin-codegen:${Version.moshi}")

    implementation("com.sksamuel.hoplite", "hoplite-core", Version.hoplite)

    implementation("org.json", "json", "20210307")

    implementation(platform("software.amazon.awssdk:bom:${Version.aws}"))
    implementation("software.amazon.awssdk:netty-nio-client")
    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:dynamodb")

    implementation("cloud.localstack", "localstack-utils", Version.localstack)

//    implementation("org.springframework.boot:spring-boot-starter-web")
//    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
//    testImplementation("org.springframework.boot:spring-boot-starter-test")
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
