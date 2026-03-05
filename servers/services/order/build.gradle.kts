import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies()

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ─── Core ─────────────────────────────────────

    // ─── API ──────────────────────────────────────

    // ─── Data ─────────────────────────────────────

    // ─── Config ───────────────────────────────────

    // ─── Event ────────────────────────────────────

    // ─── Security ─────────────────────────────────

    // ─── OpenAPI ──────────────────────────────────

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
