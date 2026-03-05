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
    // Backward-compatible aggregator:
    // keep legacy module path while delegating to split modules.
    api(project(":libs:config:kafka-core"))
    api(project(":libs:config:kafka-idempotency-jpa"))
}
