plugins {
  java
  `java-library`
  `maven-publish`
}

group = "cloud.aster-lang"

// Maven 制品版本 = 共享版本目录的 asterLang（JVM 生态单一版本源，ADR 0012/0023 §9）。
// 不硬编码字面量——字面量是版本漂移的来源（runtime 曾随生态 bump 手同步）。从 catalog
// 派生让版本永远跟随 ecosystemVersion。与 core/truffle/locales/hi 同构。
version = extensions.getByType<VersionCatalogsExtension>()
    .named("asterLibs").findVersion("asterLang").get().requiredVersion

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
  // R21-Major-5 / 审计 #32：与 aster-api 对齐 quarkus-bom，避免运行时库 BOM 漂移。
  // aster-api 已升到 3.37.0——本库当初写死 3.32.2 后 api 单方面升级，导致本库的
  // quarkus-cache/quarkus-core 按 3.32.2 编译测试、却在 aster-api 内解析为 3.37.0，
  // Quarkus 行为变更只会在 aster-api 暴露而本库 CI 全绿。对齐到 3.37.0 消除漂移。
  // （平台 catalog 已新增 asterLibs.quarkus.bom=3.37.0，catalog 发布后应迁移到别名。）
  implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.37.0"))
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
