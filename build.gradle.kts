plugins {
    kotlin("jvm")
}

group = "com.weefic.xtun"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("io.netty:netty-all:4.2.1.Final")
    api("ch.qos.logback:logback-classic:1.2.10")
    api("org.bouncycastle:bcprov-jdk15on:1.70")
    api("com.fasterxml.jackson.core:jackson-annotations:2.16.0")
    api("com.maxmind.geoip2:geoip2:4.2.0")
}

kotlin {
    jvmToolchain(17)
}