dependencies {
    // Spring Cloud Gateway
    implementation("org.springframework.cloud:spring-cloud-starter-gateway")
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // Security (JWT claim parsing)
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Shared modules
    implementation(project(":libs:contracts:http"))
    implementation(project(":libs:security:context"))

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testAnnotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.0.0")
    }
}
