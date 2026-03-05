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
    api(project(":libs:event:domain"))
    api(project(":libs:event:outbox-api"))
    api(project(":libs:data:entity"))

    implementation(project(":libs:config:kafka"))
    implementation(project(":libs:core:util"))

    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.kafka:spring-kafka")
    api("org.springframework.boot:spring-boot-starter")

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}
