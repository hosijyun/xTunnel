plugins {
    kotlin("jvm")
}

group = "com.weefic.xtun"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("io.netty:netty-all:4.2.7.Final")
    // https://mvnrepository.com/artifact/io.netty/netty-codec-native-quic
    api("io.netty:netty-codec-native-quic:4.2.7.Final")
    // https://mvnrepository.com/artifact/io.netty/netty-codec-classes-quic
    api("io.netty:netty-codec-classes-quic:4.2.7.Final")

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    api("ch.qos.logback:logback-classic:1.4.11")
    api("org.bouncycastle:bcprov-jdk18on:1.81")
    api("com.fasterxml.jackson.core:jackson-annotations:2.16.0")
    api("com.maxmind.geoip2:geoip2:4.2.0")
    api("org.conscrypt:conscrypt-openjdk-uber:2.5.2")
}

kotlin {
    jvmToolchain(17)
}