plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencies {
    // Backward-compatible aggregator:
    // keep legacy module path while delegating to split modules.
    api(project(":libs:security:security-starter-servlet"))
    api(project(":libs:security:security-starter-webflux"))
}

tasks.findByName("bootJar")?.enabled = false

tasks.jar {
    enabled = true
}
