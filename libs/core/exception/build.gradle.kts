plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencies {
    // Spring Web (for HttpStatus)
    api("org.springframework:spring-web")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}
