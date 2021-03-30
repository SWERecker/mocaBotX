import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.10"
}

group = "me.swe"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    //maven { url = uri("https://dl.bintray.com/karlatemp/misc") }
    //maven("https://gitee.com/Karlatemp/Karlatemp-repo/raw/master/")
}

dependencies {
    //testImplementation(kotlin("test-junit"))
    api("net.mamoe", "mirai-core", "2.4.2")
    //runtimeOnly("net.mamoe:mirai-login-solver-selenium:1.0-dev-16")
    api("org.mongodb", "mongodb-driver-sync", "4.2.2")
    api("redis.clients", "jedis", "3.5.2")
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}
