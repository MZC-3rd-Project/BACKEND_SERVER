import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies(includePagination = false, includeRedis = false)

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Core

    // API

    // Data

    // Event (Outbox 패턴)

    // Config
    implementation(project(":libs:config:webclient"))

    // Security (Gateway HMAC 헤더 검증)

    // OpenAPI

    // ─── Keycloak Admin Client ────────────────────
    implementation("org.keycloak:keycloak-admin-client:23.0.0")

    // ─── Spring Boot ─────────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Flyway
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database (runtime)
    runtimeOnly("org.postgresql:postgresql")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}
