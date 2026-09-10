pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android (offline OCR) is published on JitPack only.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("cz.adaptech.tesseract4android") }
        }
    }
}
rootProject.name = "Leaf"
include(":app")
// On-demand language packs for text recognition (see packs/).
include(":lang_por")
project(":lang_por").projectDir = file("packs/lang_por")
include(":lang_pol")
project(":lang_pol").projectDir = file("packs/lang_pol")
include(":lang_tur")
project(":lang_tur").projectDir = file("packs/lang_tur")
include(":lang_ind")
project(":lang_ind").projectDir = file("packs/lang_ind")
include(":lang_vie")
project(":lang_vie").projectDir = file("packs/lang_vie")
include(":lang_jpn")
project(":lang_jpn").projectDir = file("packs/lang_jpn")
include(":lang_kor")
project(":lang_kor").projectDir = file("packs/lang_kor")
include(":lang_chi_sim")
project(":lang_chi_sim").projectDir = file("packs/lang_chi_sim")
include(":lang_hin")
project(":lang_hin").projectDir = file("packs/lang_hin")
include(":lang_ara")
project(":lang_ara").projectDir = file("packs/lang_ara")
include(":lang_hrv")
project(":lang_hrv").projectDir = file("packs/lang_hrv")
include(":lang_slv")
project(":lang_slv").projectDir = file("packs/lang_slv")
include(":lang_bul")
project(":lang_bul").projectDir = file("packs/lang_bul")
include(":lang_mkd")
project(":lang_mkd").projectDir = file("packs/lang_mkd")
include(":lang_hun")
project(":lang_hun").projectDir = file("packs/lang_hun")
include(":lang_ron")
project(":lang_ron").projectDir = file("packs/lang_ron")
include(":lang_ell")
project(":lang_ell").projectDir = file("packs/lang_ell")
include(":lang_ukr")
project(":lang_ukr").projectDir = file("packs/lang_ukr")
include(":lang_nld")
project(":lang_nld").projectDir = file("packs/lang_nld")
include(":lang_ces")
project(":lang_ces").projectDir = file("packs/lang_ces")
