plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.openapi.generator") version "5.2.1"
    application
    idea
    jacoco
}

group = "io.provenance.tech.aggregate"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

val TENDERMINT_OPENAPI_YAML = "$rootDir/src/main/resources/tendermint-v0.34.12-rpc-openapi-FIXED.yaml"

repositories {
    mavenCentral()
    maven( url = "https://s01.oss.sonatype.org/content/groups/staging/")
    maven( url = "https://jitpack.io")
}

dependencies {
    implementation(projects.common)
    implementation(projects.repository)
    implementation(projects.service)

    implementation(libs.ktor.core)
    implementation(libs.ktor.netty)

    implementation(libs.bundles.eventstream)
    implementation(libs.bundles.kotlin)
    testImplementation(libs.bundles.junit)
    testImplementation(libs.kotlin.testcoroutines)
    testImplementation(libs.bundles.mockk)
    implementation(libs.bundles.apache.commons)
    implementation(libs.bundles.scarlet)
    implementation(libs.datadog)
    implementation(libs.provenance.protos)
    implementation(libs.bundles.logback)
    implementation(libs.moshi.kotlin.codegen)
    kapt(libs.moshi.kotlin.codegen)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.json)
    implementation(platform(libs.aws.bom))
    implementation(libs.bundles.aws)
    implementation(libs.localstack)
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


tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "io.provenance.aggregate.service.MainKt"
    }
    isZip64 = true
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("io/provenance/aggregate/service/MainKt*")
            exclude("io/provenance/aggregate/service/stream/models/*")
        }
    )
}

tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
}
