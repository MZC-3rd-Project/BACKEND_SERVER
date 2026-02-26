dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ─── 공통 모듈 ─────────────────────────────────
    // Core
    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:core:id"))

    // API
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))

    // Data
    implementation(project(":libs:data:entity"))

    // Event (Outbox 패턴)
    implementation(project(":libs:event:domain"))
    implementation(project(":libs:event:outbox"))

    // Config
    implementation(project(":libs:config:webclient"))
    implementation(project(":libs:config:kafka"))

    // Security (Gateway HMAC 헤더 검증)
    implementation(project(":libs:security:security-starter"))

    // OpenAPI
    implementation(project(":libs:openapi:config"))

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
