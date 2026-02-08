plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    // Jackson (for JSON serialization of cursor responses)
    api("com.fasterxml.jackson.core:jackson-databind")

    // Spring Data (optional, for Pageable integration)
    compileOnly("org.springframework.data:spring-data-commons")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
