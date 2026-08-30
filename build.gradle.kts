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

  // 测试：版本交由 junit-bom 对齐，不写字面量（issue #45）。
  //
  // ★此前是 `junit-jupiter:6.0.0` + `junit-platform-launcher:1.13.4` —— **混代**：
  //   1.13.x 属 JUnit 5.13 的平台线，JUnit 6 起平台构件已统一为 6.x。
  //   而上一行注释还写着「避免 BOM 漂移」，与实际写法自相矛盾。
  //
  // ★更关键的是这两个字面量**根本没生效**：实测依赖树里
  //     junit-jupiter:6.0.0            -> 6.1.0
  //     junit-platform-launcher:1.13.4 -> 6.1.0
  //   均被传递引入的 junit-bom:6.1.0 覆盖。即写在这里的数字既不真实、
  //   又会让读者以为版本是被"钉住"的——两头都误导。
  //
  // 处理：jupiter 走 catalog 别名（生态统一治理），launcher 不写版本
  //（与 aster-lang-validation 同构），由 BOM 解析。
  testImplementation(asterLibs.junit.jupiter)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
  useJUnitPlatform()
}

tasks.withType<Jar> {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
