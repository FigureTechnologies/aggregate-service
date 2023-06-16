plugins {
    kotlin("jvm")
    kotlin("kapt")
    idea
}

group = "tech.figure.aggregate"
version = "0.0.1-SNAPSHOT"
val javaTarget = JavaVersion.VERSION_17
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":proto"))
    implementation(libs.bundles.kotlin)
    implementation(libs.kotlin.serialization)

    implementation(libs.bundles.apache.commons)
    implementation(libs.bundles.scarlet)
    implementation(libs.datadog)
    implementation(libs.grpc.protobuf)
    implementation(libs.provenance.protos)
    implementation(libs.bundles.logback)
    implementation(libs.moshi.kotlin.codegen)
    implementation(libs.bundles.hoplite)
    implementation(libs.json)
    implementation(libs.bundles.hdwallet)

    implementation(libs.commons.dbutils)
    implementation(libs.apache.commons)

    implementation(libs.postgres)
    
    implementation(libs.blockapi.proto)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)

    testImplementation(libs.kotlin.testcoroutines)
    testImplementation(libs.bundles.junit)
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

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "17"
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
}
