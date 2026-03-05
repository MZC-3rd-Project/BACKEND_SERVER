import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies()

dependencies {
    // Web
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Core

    // API

    // Data

    // Config
    implementation(project(":libs:config:resilience"))

    // Event

    // OpenAPI

    // Security

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.31.77"))
    implementation("software.amazon.awssdk:s3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database runtime
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
