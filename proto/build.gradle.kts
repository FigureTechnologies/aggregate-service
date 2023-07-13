import com.google.protobuf.gradle.id

plugins {
    java
    application
    kotlin("jvm")
    id("com.google.protobuf") version libs.versions.pluginProtobuf.get()
    idea
    `maven-publish`
}

dependencies {
    implementation(libs.bundles.grpc)
    implementation(libs.bundles.protobuf)
    implementation(libs.bundles.kotlin.coroutines)
    implementation(libs.grpc.kotlin.stub)
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)
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
