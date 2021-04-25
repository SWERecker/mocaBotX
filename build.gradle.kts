import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "me.swe"
version = "1.0.1"

repositories {
    mavenCentral()
    jcenter()
    //maven { url = uri("https://dl.bintray.com/karlatemp/misc") }
    //maven("https://gitee.com/Karlatemp/Karlatemp-repo/raw/master/")
}

dependencies {
    //testImplementation(kotlin("test-junit"))
    api("net.mamoe", "mirai-core", "2.6.2")
    //runtimeOnly("net.mamoe:mirai-login-solver-selenium:1.0-dev-16")
    api("org.mongodb", "mongodb-driver-sync", "4.2.2")
    api("redis.clients", "jedis", "3.5.2")
    api("org.slf4j", "slf4j-log4j12", "1.7.30")

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