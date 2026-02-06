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
    // Spring Data JPA
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}
