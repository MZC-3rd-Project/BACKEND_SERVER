dependencies {
    // Core
    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:core:id"))

    // API
    implementation(project(":libs:api:exception-handler"))

    // Data
    implementation(project(":libs:data:entity"))

    // Config
    implementation(project(":libs:config:kafka"))

    // Event
    implementation(project(":libs:event:consumer"))
    implementation(project(":libs:event:domain"))
    implementation(project(":libs:event:inbox"))
    implementation(project(":libs:event:outbox"))

    // Security
    implementation(project(":libs:security:security-starter"))

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
