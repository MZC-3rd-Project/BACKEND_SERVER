import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies()

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Core

    // API

    // Data
    implementation(project(":libs:clients:media-client"))
    implementation(project(":libs:clients:stock-client"))

    // Config
    implementation(project(":libs:config:resilience"))
    implementation(project(":libs:config:webclient"))

    // Event

    // Security

    // OpenAPI

    // ─── Spring Boot ─────────────────────────────────
    // JPA (Kafka 공통 모듈의 멱등성/실패 저장소 연동)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Elasticsearch
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database (runtime)
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
