plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.openapi.generator") version "5.2.1"
    application
    idea
    jacoco
    signing
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

group = "tech.figure.aggregate"
version = "0.0.1-SNAPSHOT"
val javaTarget = JavaVersion.VERSION_11
java.sourceCompatibility = javaTarget
java.targetCompatibility = javaTarget

repositories {
    mavenCentral()
    maven( url = "https://jitpack.io")
}

dependencies {
    implementation(projects.common)
    implementation(projects.repository)
    implementation(projects.service)

    implementation(libs.ktor.core)
    implementation(libs.ktor.netty)

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

    implementation(libs.blockapi.client)
    implementation(libs.blockapi.proto)

    implementation(libs.grpc.core)
    implementation(libs.protobuf.util)

    implementation(libs.exposed.core)

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

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(findProject("ossrhUsername")?.toString() ?: System.getenv("OSSRH_USERNAME"))
            password.set(findProject("ossrhPassword")?.toString() ?: System.getenv("OSSRH_PASSWORD"))
            stagingProfileId.set("858b6e4de4734a") // tech.figure staging id
        }
    }
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
