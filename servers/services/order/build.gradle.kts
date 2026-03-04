dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ─── Core ─────────────────────────────────────
    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:core:id"))
    implementation(project(":libs:core:pagination"))

    // ─── API ──────────────────────────────────────
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))

    // ─── Data ─────────────────────────────────────
    implementation(project(":libs:data:entity"))

    // ─── Config ───────────────────────────────────
    implementation(project(":libs:config:kafka"))
    implementation(project(":libs:config:redis"))

    // ─── Event ────────────────────────────────────
    implementation(project(":libs:event:domain"))
    implementation(project(":libs:event:outbox"))

    // ─── Security ─────────────────────────────────
    implementation(project(":libs:security:security-starter"))

    // ─── OpenAPI ──────────────────────────────────
    implementation(project(":libs:openapi:config"))

    // ─── Spring Boot ──────────────────────────────
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
