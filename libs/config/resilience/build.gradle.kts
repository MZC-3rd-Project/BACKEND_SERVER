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
    // Resilience4j
    api("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    api("io.github.resilience4j:resilience4j-circuitbreaker:2.2.0")
    api("io.github.resilience4j:resilience4j-retry:2.2.0")
    api("io.github.resilience4j:resilience4j-timelimiter:2.2.0")

    // Spring Boot AOP (required for Resilience4j annotations)
    api("org.springframework.boot:spring-boot-starter-aop")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
