import com.example.buildlogic.applyServiceCommonModuleDependencies

applyServiceCommonModuleDependencies(includePagination = false, includeRedis = false, includeOpenApi = false, includeApiResponse = false)

dependencies {
    // Core

    // API

    // Data

    // Config

    // Event

    // Security

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // AWS SDK
    implementation(platform("software.amazon.awssdk:bom:2.31.77"))
    implementation("software.amazon.awssdk:s3")
    implementation("com.sksamuel.scrimage:scrimage-webp:4.1.3")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Database runtime
    runtimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("com.h2database:h2")
}
