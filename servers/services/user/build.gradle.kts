dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Common libs
    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))
    implementation(project(":libs:data:entity"))
    implementation(project(":libs:security:core"))

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
}
