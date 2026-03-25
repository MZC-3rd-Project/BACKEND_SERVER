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
    implementation(project(":libs:clients:product-client"))

    // Config
    implementation(project(":libs:config:kafka"))
    implementation(project(":libs:config:redis"))
    implementation(project(":libs:config:resilience"))
    implementation(project(":libs:config:webclient"))

    // Event
    implementation(project(":libs:event:consumer"))
    implementation(project(":libs:event:domain"))
    implementation(project(":libs:event:inbox"))
    implementation(project(":libs:event:outbox"))
    implementation(project(":libs:event:payment"))
    implementation(project(":libs:security:security-starter"))

    // OpenAPI
    implementation(project(":libs:openapi:config"))

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
