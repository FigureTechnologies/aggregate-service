plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
}

group = "io.provenance.tech.aggregate"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

val TENDERMINT_OPENAPI_YAML = "$rootDir/src/main/resources/tendermint-v0.34.12-rpc-openapi-FIXED.yaml"

repositories {
    mavenCentral()
    maven { url = uri("https://s01.oss.sonatype.org/content/groups/staging/") }
}

dependencies {
    implementation(libs.bundles.kotlin)
    testImplementation(libs.kotlin.testcoroutines)
    testImplementation(libs.bundles.junit)

    implementation(libs.bundles.apache.commons)
    implementation(libs.bundles.scarlet)
    implementation(libs.datadog)
    implementation(libs.grpc.protobuf)
    implementation(libs.provenance.protos)
    implementation(libs.bundles.logback)
    implementation(libs.moshi.kotlin.codegen)
    kapt(libs.moshi.kotlin.codegen)
    implementation(libs.bundles.hoplite)
    implementation(libs.json)
    implementation(platform(libs.aws.bom))
    implementation(libs.bundles.aws)
    implementation(libs.localstack)
    implementation(libs.bundles.eventstream)
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
    packageName.set("io.provenance.aggregate.common")
    modelPackage.set("io.provenance.aggregate.common.models")
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
