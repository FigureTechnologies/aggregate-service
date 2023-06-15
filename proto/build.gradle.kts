import com.google.protobuf.gradle.id

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    id("com.google.protobuf") version libs.versions.pluginProtobuf.get()
    idea
}

repositories {
    mavenCentral()
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protocVersion.get()}"
    }

    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpcKotlin.get()}:jdk8@jar"
        }
    }

    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
                create("grpckt")
            }
            it.builtins {
                create("kotlin")
            }
        }
    }
}

dependencies {
    implementation(libs.bundles.grpc)
    implementation(libs.bundles.protobuf)
    implementation(libs.bundles.kotlin.coroutines)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)

}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.name
            groupId = project.group.toString()
            version = project.version.toString()
            from(components["java"])
        }
    }
}
