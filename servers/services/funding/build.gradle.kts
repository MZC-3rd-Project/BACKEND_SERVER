dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // ─── 공통 모듈 ─────────────────────────────────
    // Core
    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:core:id"))
    implementation(project(":libs:core:pagination"))

    // API
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))

    // Data
    implementation(project(":libs:data:entity"))

    // Config
    implementation(project(":libs:config:kafka"))
    implementation(project(":libs:config:redis"))
    implementation(project(":libs:config:resilience"))
    implementation(project(":libs:config:webclient"))

    // Event
    implementation(project(":libs:event:domain"))
    implementation(project(":libs:event:outbox"))

    // OpenAPI
    implementation(project(":libs:openapi:config"))

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
