plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

tasks.findByName("bootJar")?.enabled = false

tasks.jar {
    enabled = true
}

dependencies {
    api(project(":libs:config:lock"))
    api(project(":libs:config:lock-redisson"))
}
