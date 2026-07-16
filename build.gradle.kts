plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.jialiangh"
version = "1.0.4"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// 目标：兼容所有基于 IntelliJ 平台的 IDE（IDEA / Android Studio / PyCharm / WebStorm ...）
// 只依赖 com.intellij.modules.platform，构建时用 IDEA Community 作为编译基线即可。
dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        pluginVerifier()
        zipSigner()
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            // 覆盖较宽的版本区间，让插件能装进各代全家桶
            sinceBuild = "233"
            untilBuild = "252.*"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    wrapper {
        gradleVersion = "8.10.2"
    }
}
