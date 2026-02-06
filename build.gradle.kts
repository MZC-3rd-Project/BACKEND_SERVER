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

    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")


    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter")
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



