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
    // SpringDoc OpenAPI
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")

    // Core exception module for error code references
    api(project(":libs:core:exception"))
    api(project(":libs:api:response"))

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
