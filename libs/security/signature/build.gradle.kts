plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":libs:contracts:http"))
    api(project(":libs:core:exception"))
    api(project(":libs:security:context"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.findByName("bootJar")?.enabled = false

tasks.jar {
    enabled = true
}
