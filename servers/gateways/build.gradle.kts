tasks.findByName("bootJar")?.enabled = false
tasks.findByName("bootRun")?.enabled = false

subprojects {
    dependencies {
        add("implementation", project(":libs:config:metrics"))
        add("implementation", project(":libs:config:logging"))
        add("implementation", project(":libs:config:tracing"))
    }
}
