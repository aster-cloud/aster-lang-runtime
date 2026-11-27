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
            // quarkus-bom 不进发布元数据（见下方 quarkusPlatform），直接依赖的版本改为
            // 按本仓 runtimeClasspath 的实际解析结果写入 POM / Gradle module，
            // 消费方拿到的是普通版本要求，可参与自身的冲突解析。
            versionMapping {
                allVariants {
                    fromResolutionOf("runtimeClasspath")
                }
            }
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

java {
  toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

repositories { mavenCentral() }

// quarkus-bom 只约束本仓自身的解析（编译 / 运行 / 测试 classpath），不进 apiElements /
// runtimeElements，因而不会发布给消费方（aster-lang-runtime#90）。此前它挂在
// implementation 上，发布元数据带 enforcedPlatform(quarkus-bom)，把消费方的
// org.graalvm.sdk:graal-sdk / nativeimage / word 强制降到 23.1.2（truffle 用 25.x）。
// 该配置既不可消费也不可解析，仅作为下列 classpath 的父配置。
val quarkusPlatform: Configuration by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = false
}
sourceSets.all {
  listOf(compileClasspathConfigurationName, runtimeClasspathConfigurationName).forEach {
    configurations.named(it) { extendsFrom(quarkusPlatform) }
  }
}

dependencies {
  // R21-Major-5 / 审计 #32：与 aster-api 对齐 quarkus-bom，避免运行时库 BOM 漂移。
  // aster-api 已升到 3.37.0——本库当初写死 3.32.2 后 api 单方面升级，导致本库的
  // quarkus-cache/quarkus-core 按 3.32.2 编译测试、却在 aster-api 内解析为 3.37.0，
  // Quarkus 行为变更只会在 aster-api 暴露而本库 CI 全绿。对齐到 3.37.0 消除漂移。
  // （平台 catalog 已新增 asterLibs.quarkus.bom=3.37.0，catalog 发布后应迁移到别名。）
  quarkusPlatform(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.37.0"))
  //
  // ★直接依赖必须写明版本（取 quarkus-bom 3.37.0 中的值）：BOM 只在私有的 quarkusPlatform 上，
  //   不进 apiElements / runtimeElements。经 includeBuild 源码引用本仓的消费方（aster-api 的
  //   replay / runner）看到的是项目变体里的声明，versionMapping 只改发布的 POM / module，
  //   不写版本即解析失败（Could not find io.quarkus:quarkus-cache:.）。
  //   checkConsumableVersions 守门：版本缺失或与本仓 BOM 解析结果不一致即失败，升级 BOM 时随之更新。
  implementation("io.quarkus:quarkus-cache:3.37.0")
  implementation("io.quarkus:quarkus-core:3.37.0")
  implementation("io.smallrye.common:smallrye-common-net:2.18.1") // For CidrAddress (GraalVM substitutions)
  implementation("jakarta.enterprise:jakarta.enterprise.cdi-api:4.1.0")
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

// 防回归（#90）：发布的 POM / Gradle module 不得再带 quarkus-bom 或 enforced-platform，
// 否则消费方的 GraalVM 等版本会被再次强制钉死。
val checkPublishedMetadata = tasks.register("checkPublishedMetadata") {
  description = "核对发布元数据未泄漏 quarkus-bom / enforced-platform"
  group = "verification"
  val pom = tasks.named<GenerateMavenPom>("generatePomFileForMavenJavaPublication")
  val module = tasks.named<GenerateModuleMetadata>("generateMetadataFileForMavenJavaPublication")
  dependsOn(pom, module)
  val files = listOf(pom.map { it.destination }, module.flatMap { it.outputFile }.map { it.asFile })
  doLast {
    val leaks = files.map { it.get() }.flatMap { f ->
      val text = f.readText()
      listOf("quarkus-bom", "enforced-platform").filter { text.contains(it) }.map { "${f.name}: $it" }
    }
    if (leaks.isNotEmpty()) {
      throw GradleException("发布元数据泄漏 BOM：\n" + leaks.joinToString("\n"))
    }
  }
}
tasks.named("check") { dependsOn(checkPublishedMetadata) }

// 防回归（#90 后续）：可消费变体（apiElements / runtimeElements）里的每个外部依赖都必须带版本，
// 且等于本仓 runtimeClasspath（受 quarkus-bom 约束）的实际解析版本。includeBuild 的消费方直接
// 读取这些声明，不经过 versionMapping；不一致会让源码引用与发布制品解析出不同版本。
val checkConsumableVersions = tasks.register("checkConsumableVersions") {
  description = "核对可消费变体中的外部依赖均带版本且与本仓解析结果一致"
  group = "verification"
  val declared = provider {
    listOf("apiElements", "runtimeElements").flatMap { name ->
      configurations[name].allDependencies.withType<ExternalModuleDependency>()
        .map { Triple(name, "${it.group}:${it.name}", it.version.orEmpty()) }
    }
  }
  val resolved = configurations.named("runtimeClasspath").flatMap { c ->
    c.incoming.resolutionResult.rootComponent.map { root ->
      root.dependencies.filterIsInstance<org.gradle.api.artifacts.result.ResolvedDependencyResult>()
        .mapNotNull { it.selected.moduleVersion }
        .associate { "${it.group}:${it.name}" to it.version }
    }
  }
  doLast {
    val actual = resolved.get()
    val problems = declared.get().mapNotNull { (variant, module, version) ->
      when {
        version.isBlank() -> "$variant: $module 未写版本"
        actual[module] != null && actual[module] != version ->
          "$variant: $module 声明 $version，本仓解析为 ${actual[module]}"
        else -> null
      }
    }
    if (problems.isNotEmpty()) {
      throw GradleException("可消费变体依赖版本问题：\n" + problems.joinToString("\n"))
    }
  }
}
tasks.named("check") { dependsOn(checkConsumableVersions) }
