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
    api(project(":libs:security:signature"))

    // StringUtils + core utilities
    compileOnly("org.springframework:spring-core")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
