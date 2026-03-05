package com.example.buildlogic

import org.gradle.api.Project

fun Project.applyServiceCommonModuleDependencies(
    includePagination: Boolean = true,
    includeRedis: Boolean = true,
    includeOpenApi: Boolean = true,
    includeSecurityStarter: Boolean = true,
    includeApiResponse: Boolean = true
) {
    dependencies.add("implementation", project(":libs:core:exception"))
    dependencies.add("implementation", project(":libs:core:util"))
    dependencies.add("implementation", project(":libs:core:id"))
    dependencies.add("implementation", project(":libs:data:entity"))
    dependencies.add("implementation", project(":libs:config:kafka"))
    dependencies.add("implementation", project(":libs:event:domain"))
    dependencies.add("implementation", project(":libs:event:outbox"))

    if (includePagination) {
        dependencies.add("implementation", project(":libs:core:pagination"))
    }
    if (includeRedis) {
        dependencies.add("implementation", project(":libs:config:redis"))
    }
    if (includeOpenApi) {
        dependencies.add("implementation", project(":libs:openapi:config"))
    }
    if (includeSecurityStarter) {
        dependencies.add("implementation", project(":libs:security:security-starter"))
    }
    if (includeApiResponse) {
        dependencies.add("implementation", project(":libs:api:rest-starter"))
    } else {
        dependencies.add("implementation", project(":libs:api:exception-handler"))
    }
}
