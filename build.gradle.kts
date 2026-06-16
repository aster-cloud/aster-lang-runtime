plugins {
  java
  `java-library`
  `maven-publish`
}

group = "cloud.aster-lang"
version = "1.0.1"

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "aster-lang-runtime"
            // 抑制 enforced platform 警告，因为这是内部发布
            suppressAllPomMetadataWarnings()
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/aster-cloud/${rootProject.name}")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
    }
}

tasks.withType<GenerateModuleMetadata> {
    suppressedValidationErrors.add("enforced-platform")
}

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories { mavenCentral() }

dependencies {
  // R21-Major-5：与 aster-api 对齐到 3.32.2，避免运行时库 BOM 漂移
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.32.2"))
  implementation("io.quarkus:quarkus-cache")
  implementation("io.quarkus:quarkus-core")
  implementation("io.smallrye.common:smallrye-common-net") // For CidrAddress (GraalVM substitutions)
  implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1")
  implementation("jakarta.inject:jakarta.inject-api:2.0.1")

  // 测试：保持与 core/truffle/validation 同版本（6.0.0），避免 BOM 漂移
  testImplementation("org.junit.jupiter:junit-jupiter:6.0.0")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
  useJUnitPlatform()
}

tasks.withType<Jar> {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
