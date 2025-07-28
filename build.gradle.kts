plugins {
    id("java")
    id("xyz.jpenilla.run-velocity") version "2.3.1"
    id("com.gradleup.shadow") version "9.0.0-rc2"
}

dependencies {
    compileOnly(libs.velocity.api)
    annotationProcessor(libs.velocity.api)

    compileOnly(libs.packetevents)
    implementation(libs.rabbitmq.client)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
}

tasks {
    test {
        useJUnitPlatform()
    }
    runVelocity {
        velocityVersion("3.4.0-SNAPSHOT")
    }
}