plugins {
    id("java")
}

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)

    compileOnly(libs.packetevents)
    implementation(libs.rabbitmq.client)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
}

tasks.test {
    useJUnitPlatform()
}