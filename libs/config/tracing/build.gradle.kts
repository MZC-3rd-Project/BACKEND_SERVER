plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

tasks.bootJar { enabled = false }
tasks.jar { enabled = true }

dependencies {
    // Micrometer Tracing
    api("io.micrometer:micrometer-tracing-bridge-brave")

    // Zipkin Reporter
    api("io.zipkin.reporter2:zipkin-reporter-brave")

    // Spring Boot Actuator (tracing auto-config)
    api("org.springframework.boot:spring-boot-starter-actuator")

    // Spring Boot Auto-Configuration
    api("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Web (optional, for filter)
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
