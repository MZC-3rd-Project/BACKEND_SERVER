dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:core:id"))
    implementation(project(":libs:core:pagination"))
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))
    implementation(project(":libs:data:entity"))
    implementation(project(":libs:config:kafka"))
    implementation(project(":libs:config:redis"))
    implementation(project(":libs:config:resilience"))
    implementation(project(":libs:config:webclient"))
    implementation(project(":libs:event:consumer"))
    implementation(project(":libs:event:domain"))
    implementation(project(":libs:event:inbox"))
    implementation(project(":libs:openapi:config"))
    implementation(project(":libs:security:security-starter"))
    implementation(project(":libs:clients:auth-client"))
    implementation(project(":libs:clients:media-client"))
    implementation(project(":libs:clients:product-client"))

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    testRuntimeOnly("com.h2database:h2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("com.h2database:h2")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}
