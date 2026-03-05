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
    api(project(":libs:core:exception"))

    // AOP (for @DistributedLock)
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Auto-configuration
    implementation("org.springframework.boot:spring-boot-autoconfigure")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
