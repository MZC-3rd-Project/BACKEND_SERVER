import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies()

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Core

    // API

    // Data
    implementation(project(":libs:clients:product-client"))

    // Config
    implementation(project(":libs:config:resilience"))
    implementation(project(":libs:config:webclient"))

    // Event

    // OpenAPI

    // ─── Spring Boot ─────────────────────────────────
    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Mail
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation(platform("software.amazon.awssdk:bom:2.31.77"))
    implementation("software.amazon.awssdk:ses")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database (runtime)
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
