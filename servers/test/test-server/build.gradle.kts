import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies(includePagination = false, includeSecurityStarter = false)

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Core

    // API

    // Data

    // Config
    implementation(project(":libs:config:resilience"))

    // Event

    // OpenAPI

    // WebClient (for self-calling circuit breaker test)
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // ─── Spring Boot ─────────────────────────────────
    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database (runtime)
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
