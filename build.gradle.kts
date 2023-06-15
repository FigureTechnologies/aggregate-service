plugins {
    kotlin("jvm")
    kotlin("plugin.serialization") version "1.8.21"
    kotlin("kapt")
    id("org.openapi.generator") version "5.4.0"
    application
    idea
    jacoco
    signing
    `maven-publish`
}

group = "tech.figure.aggregate"
version = "0.0.1-SNAPSHOT"
val javaVersion = JavaVersion.VERSION_17
java.sourceCompatibility = javaVersion
java.targetCompatibility = javaVersion

repositories {
    mavenCentral()
    maven( url = "https://jitpack.io")
}

dependencies {
    implementation(projects.common)
    implementation(projects.repository)
    implementation(projects.service)

    implementation(libs.blockapi.client)
    implementation(libs.blockapi.proto)
    implementation(libs.bundles.apache.commons)
    implementation(libs.bundles.logback)
    implementation(libs.bundles.kotlin)
    implementation(libs.bundles.scarlet)
    implementation(libs.datadog)
    implementation(libs.exposed.core)
    implementation(libs.grpc.core)
    implementation(libs.hoplite.core)
    implementation(libs.hoplite.yaml)
    implementation(libs.json)
    implementation(libs.kotlin.serialization)
    implementation(libs.ktor.core)
    implementation(libs.ktor.jackson)
    implementation(libs.ktor.netty)
    implementation(libs.kafka.clients)
    implementation(libs.moshi.kotlin.codegen)
    implementation(libs.protobuf.util)
    implementation(libs.provenance.protos)

    kapt(libs.moshi.kotlin.codegen)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.kotlin.testcoroutines)
    testImplementation(libs.bundles.mockk)
    testImplementation(libs.h2database)
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
    mainClass.set("tech.figure.aggregate.service.MainKt")
}

tasks.compileKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = javaVersion.majorVersion
    }
}

tasks.compileTestKotlin {
    kotlinOptions {
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
        jvmTarget = javaVersion.majorVersion
    }
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "tech.figure.aggregate.service.MainKt"
    }
    isZip64 = true
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

subprojects {
    group="tech.figure.aggregate"
    version = this.findProperty("libraryVersion")?.toString() ?: "1.0-SNAPSHOT"
    apply {
        plugin("signing")
        plugin("maven-publish")
        plugin("kotlin")
        plugin("java-library")
    }

    java {
        withSourcesJar()
        withJavadocJar()
    }

    val artifactName = name
    val projectVersion = version.toString()

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = project.group.toString()
                artifactId = artifactName
                version = projectVersion

                from(components["java"])

                pom {
                    name.set("Aggregate Service")
                    description.set("Block data aggregation service")
                    url.set("https://figure.tech")

                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        }
                    }

                    scm {
                        connection.set("git@github.com:FigureTechnologies/aggregate-service.git")
                        developerConnection.set("git@github.com:FigureTechnologies/aggregate-service.git")
                        url.set("https://github.com/FigureTechnologies/aggregate-service")
                    }
                }
            }
        }
        signing {
            sign(publishing.publications["maven"])
        }
    }
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    dependsOn(tasks.test)
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude("tech/figure/aggregate/service/MainKt*")
            exclude("tech/figure/aggregate/service/stream/models/*")
        }
    )
}

tasks.test {
    useJUnitPlatform()
    finalizedBy("jacocoTestReport")
}
