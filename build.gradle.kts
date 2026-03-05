import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

plugins {
    java
    id("org.springframework.boot") version "3.5.10" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}
allprojects {
    group = "com.example"
    version = "0.0.1-SNAPSHOT"
    description = "project03-backend"

    repositories {
        mavenCentral()
    }
}

subprojects {
    val isLeafServerModule = path.startsWith(":servers:") && childProjects.isEmpty()

    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")
    if (isLeafServerModule) {
        apply(plugin = "org.springframework.boot")
    }

    extensions.configure<DependencyManagementExtension> {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.10")
        }
    }


    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        if (isLeafServerModule) {
            implementation("org.springframework.boot:spring-boot-starter")
        }
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

tasks.jar {
    enabled = false
}
