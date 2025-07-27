rootProject.name = "MLFingerprintProxyPlugin"

dependencyResolutionManagement { 
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
    
    versionCatalogs {
        create("libs") {
            version("velocity", "3.4.0-SNAPSHOT")
            version("junit", "5.10.0")

            library("velocity-api", "com.velocitypowered", "velocity-api").versionRef("velocity")
            library("junit-bom", "org.junit", "junit-bom").versionRef("junit")
            library("junit-jupiter-api", "org.junit.jupiter", "junit-jupiter-api").withoutVersion()
        }
    }
}