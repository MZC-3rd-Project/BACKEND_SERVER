import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies()

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Core

    // API

    // Data
    implementation(project(":libs:clients:stock-client"))

    // Config
    implementation(project(":libs:config:resilience"))
    implementation(project(":libs:config:webclient"))

    // Event

    // OpenAPI

    // Security

    // ─── Spring Boot ─────────────────────────────────
    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

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
