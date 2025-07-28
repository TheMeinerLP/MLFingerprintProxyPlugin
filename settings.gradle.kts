rootProject.name = "MLFingerprintProxyPlugin"

dependencyResolutionManagement { 
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
    }
    
    versionCatalogs {
        create("libs") {
            version("velocity", "3.4.0-SNAPSHOT")
            version("junit", "5.10.0")
            version("packetevents", "2.9.3")
            version("rabbitmq", "5.26.0")

            library("velocity-api", "com.velocitypowered", "velocity-api").versionRef("velocity")
            library("packetevents", "com.github.retrooper", "packetevents-velocity").versionRef("packetevents")
            library("rabbitmq-client", "com.rabbitmq", "amqp-client").versionRef("rabbitmq")

            library("junit-bom", "org.junit", "junit-bom").versionRef("junit")
            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
        }
    }
}