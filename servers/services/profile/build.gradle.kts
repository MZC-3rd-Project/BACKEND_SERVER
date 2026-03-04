dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Common libs
    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))
    implementation(project(":libs:data:entity"))
    implementation(project(":libs:core:id"))

    // OpenAPI
    implementation(project(":libs:openapi:config"))

    // Config
    implementation(project(":libs:config:kafka"))
    implementation(project(":libs:config:redis"))
    implementation(project(":libs:config:resilience"))

    // Event
    implementation(project(":libs:event:domain"))
    implementation(project(":libs:event:outbox"))

    // JPA
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Validation
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database (runtime)
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")

    // flyway
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    implementation (project(":libs:clients:auth-client"))
    implementation(project(":libs:clients:media-client"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.springframework:spring-webflux")

}
