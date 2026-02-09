plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencies {
    // Spring Security
    api("org.springframework.boot:spring-boot-starter-security")

    // Servlet API (for AuthContextCleanupFilter)
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // JWT
    // implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    // runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    // runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}
