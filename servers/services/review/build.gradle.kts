dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:id"))
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))
    implementation(project(":libs:data:entity"))
    implementation(project(":libs:event:outbox"))
    implementation(project(":libs:config:webclient"))
    implementation(project(":libs:clients:media-client"))
    implementation(project(":libs:openapi:config"))
    implementation(project(":libs:security:security-starter"))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    testRuntimeOnly("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
