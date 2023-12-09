import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("kotlin")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "net.mscript.windows"
version = "0.0.1-DEV"

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(project(":project:module-compiler"))
}

application {
    mainClass.set("${group}.MScript")
}

tasks{
    test {
        useJUnitPlatform()
    }
    build { dependsOn(shadowJar) }
    withType<ShadowJar> {
        relocate("kotlin.", "kotlin1921.")
    }
}