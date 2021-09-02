import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.21"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "me.swe"
version = "1.1.4.2-PHONE-27"

repositories {
    mavenCentral()
    jcenter()
    maven { url = uri("https://maven.aliyun.com/nexus/content/groups/public/") }
}

dependencies {
    // testImplementation(kotlin("test-junit"))
    runtimeOnly("net.mamoe:mirai-login-solver-selenium:1.0-dev-17")
    val miraiVersion = "2.7.0"
    api("net.mamoe", "mirai-core-api", miraiVersion)     // 编译代码使用
    runtimeOnly("net.mamoe", "mirai-core", miraiVersion) // 运行时使用
    api("org.mongodb", "mongodb-driver-sync", "4.2.2")
    api("redis.clients", "jedis", "3.5.2")
    api("org.slf4j", "slf4j-log4j12", "2.0.0-alpha4")
    api("com.jcabi", "jcabi-log", "0.19.0")
    api("com.squareup.okhttp3", "okhttp", "4.9.0")
    api("com.google.code.gson", "gson", "2.8.8")
    // api("net.mamoe", "mirai-logging-log4j2", "2.6.7")


}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}


tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    manifest {
        attributes["Main-Class"] = "me.swe.main.MainKt"
    }
}