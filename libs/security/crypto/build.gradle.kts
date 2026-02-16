plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":libs:core:exception"))

    // Logback PatternLayout for PII masking layout
    compileOnly("ch.qos.logback:logback-classic")

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
