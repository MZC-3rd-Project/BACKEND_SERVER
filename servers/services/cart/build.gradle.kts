dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation(project(":libs:core:exception"))
    implementation(project(":libs:core:util"))
    implementation(project(":libs:core:id"))
    implementation(project(":libs:clients:product-client"))
    implementation(project(":libs:api:response"))
    implementation(project(":libs:api:exception-handler"))
    implementation(project(":libs:config:webclient"))
    implementation(project(":libs:openapi:config"))
    implementation(project(":libs:security:security-starter"))

    implementation(platform("software.amazon.awssdk:bom:2.31.77"))
    implementation("software.amazon.awssdk:dynamodb")
    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("org.hibernate.orm:hibernate-core")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
