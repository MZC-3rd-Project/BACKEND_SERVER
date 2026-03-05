import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies(includePagination = false, includeSecurityStarter = false)

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Common libs
    implementation(project(":libs:security:crypto"))

    // OpenAPI

    // Config
    implementation(project(":libs:config:resilience"))

    // Event
    implementation(project(":libs:event:inbox"))

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
