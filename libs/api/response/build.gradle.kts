plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

dependencies {
    // Internal dependencies
    api(project(":libs:core:exception"))

    // Jackson for JSON serialization
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Spring Web (for ResponseEntity)
    api("org.springframework:spring-web")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
