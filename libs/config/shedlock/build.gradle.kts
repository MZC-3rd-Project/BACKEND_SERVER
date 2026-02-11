plugins {
    id("java-library")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

dependencies {
    // ShedLock
    api("net.javacrumbs.shedlock:shedlock-spring:6.3.0")
    api("net.javacrumbs.shedlock:shedlock-provider-redis-spring:6.3.0")

    // Redis (for RedisConnectionFactory)
    compileOnly("org.springframework.boot:spring-boot-starter-data-redis")

    // Spring Boot
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
