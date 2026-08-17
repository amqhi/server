plugins {
    application
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.2.0"
}

group = "com.amqhi"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("io.vertx:vertx-core:5.0.12")
    implementation("io.vertx:vertx-web:5.0.12")
    implementation("io.vertx:vertx-web-client:5.0.12")
    implementation("io.vertx:vertx-pg-client:5.0.12")
    implementation("io.vertx:vertx-redis-client:5.0.12")
    implementation("io.vertx:vertx-auth-oauth2:5.0.12")
    implementation("io.vertx:vertx-auth-jwt:5.0.12")
    implementation("io.netty:netty-resolver-dns-native-macos:4.2.12.Final:osx-aarch_64")
    implementation("net.mamoe.yamlkt:yamlkt:0.13.0")
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.5.26")
    implementation("software.amazon.awssdk:s3:2.44.4")
    implementation("software.amazon.awssdk:aws-crt-client:2.44.4")
    implementation("com.password4j:password4j:1.8.2")
    implementation("com.google.api-client:google-api-client:2.9.0")
}

application {
    mainClass.set("com.amqhi.MainKt")
}

kotlin {
    jvmToolchain(25)
}
tasks.test {
    useJUnitPlatform()
}
