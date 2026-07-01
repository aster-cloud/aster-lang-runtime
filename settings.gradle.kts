rootProject.name = "aster-lang-runtime"

dependencyResolutionManagement {
    // 共享版本目录（aster-lang-platform，ADR 0012/0023 §9）：本仓 Maven 制品版本从
    // catalog 的 asterLang 派生（消除字面量漂移）。catalog artifact 本身需从仓库解析，
    // 故声明 mavenLocal+mavenCentral；CI/release 先把 platform publishToMavenLocal 再构建
    // （本仓零 aster 依赖，故 catalog 仅为版本派生而引入——是消漂移的必要代价）。
    // RepositoriesMode 默认 PREFER_PROJECT，build.gradle.kts 既有 repositories 仍生效。
    @Suppress("UnstableApiUsage")
    repositories {
        mavenLocal()
        mavenCentral()
    }
    versionCatalogs {
        create("asterLibs") {
            from("cloud.aster-lang:aster-lang-platform:1.0.13")
        }
    }
}
