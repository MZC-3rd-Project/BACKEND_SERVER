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
    api(project(":libs:data:jpa-base"))
    api(project(":libs:data:rw-routing"))

    // Legacy tests in this module still instantiate routing DataSource with H2
    testRuntimeOnly("com.h2database:h2")
}
