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
    api(project(":libs:api:response"))

    // Spring Web (for @RestControllerAdvice, ResponseEntity)
    api("org.springframework.boot:spring-boot-starter-web")

    // Validation (for MethodArgumentNotValidException)
    api("org.springframework.boot:spring-boot-starter-validation")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
