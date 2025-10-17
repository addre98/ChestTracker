@file:Suppress("UnstableApiUsage", "RedundantNullableReturnType")

import com.github.breadmoirai.githubreleaseplugin.GithubReleaseTask
import me.modmuss50.mpp.ReleaseType
import net.fabricmc.loom.task.RemapJarTask
import org.ajoberstar.grgit.Grgit
import red.jackf.GenerateChangelogTask
import red.jackf.UpdateDependenciesTask

plugins {
    id("maven-publish")
    id("fabric-loom") version "1.11-SNAPSHOT"
    id("com.github.breadmoirai.github-release") version "2.5.2"
    id("org.ajoberstar.grgit") version "5.3.0"
    id("me.modmuss50.mod-publish-plugin") version "0.8.3"
}

val grgit: Grgit? = project.grgit

var canPublish = grgit != null && System.getenv("RELEASE") != null

fun getVersionSuffix(): String {
    return grgit?.branch?.current()?.name ?: "nogit+${properties["minecraft_version"]}"
}

group = properties["maven_group"]!!

if (System.getenv().containsKey("NEW_TAG")) {
    version = System.getenv("NEW_TAG").substring(1)
} else {
    val versionStr = "${properties["mod_version"]}+${properties["minecraft_version"]!!}"
    canPublish = false
    version = if (grgit != null) {
        "$versionStr+dev-${grgit.log()[0].abbreviatedId}"
    } else {
        "$versionStr+dev-nogit"
    }
}

val isBundlingSearchables = properties["bundle_searchables"] == "true"
val isBundlingWhereIsIt = properties["bundle_whereisit"] == "true" // ✅ Флаг для Where Is It

base {
    archivesName.set("${properties["archives_base_name"]}")
}

repositories {
    mavenLocal() // ✅ Для Where Is It из mavenLocal

    // Parchment Mappings
    maven {
        name = "ParchmentMC"
        url = uri("https://maven.parchmentmc.org")
        content {
            includeGroup("org.parchmentmc.data")
        }
    }

    // Mod Menu, EMI
    maven {
        name = "TerraformersMC"
        url = uri("https://maven.terraformersmc.com/releases/")
        content {
            includeGroup("com.terraformersmc")
            includeGroup("dev.emi")
        }
    }

    // YACL
    maven {
        name = "Xander Maven"
        url = uri("https://maven.isxander.dev/releases")
        content {
            includeGroupAndSubgroups("dev.isxander")
            includeGroupAndSubgroups("org.quiltmc")
        }
    }

    // YACL Snapshots
    maven {
        name = "Xander Snapshot Maven"
        url = uri("https://maven.isxander.dev/snapshots")
        content {
            includeGroupAndSubgroups("dev.isxander")
            includeGroupAndSubgroups("org.quiltmc")
        }
    }

    // Searchables
    maven {
        name = "BlameJared"
        url = uri("https://maven.blamejared.com")
        content {
            includeGroupAndSubgroups("com.blamejared.searchables")
        }
    }

    // Dev Utils, Jade
    maven {
        name = "Modrinth Maven"
        url = uri("https://api.modrinth.com/maven")
        content {
            includeGroup("maven.modrinth")
        }
    }

    // Shulker Box Tooltip
    maven {
        name = "MisterPeModder"
        url = uri("https://maven.misterpemodder.com/libs-release/")
        content {
            includeGroupAndSubgroups("com.misterpemodder")
        }
    }

    // Cloth Config
    maven {
        name = "Shedaniel"
        url = uri("https://maven.shedaniel.me")
        content {
            includeGroupAndSubgroups("me.shedaniel")
        }
    }

    // WTHIT
    maven {
        url  = uri("https://maven2.bai.lol")
        content {
            includeGroupAndSubgroups("lol.bai")
            includeGroupAndSubgroups("mcp.mobius.waila")
        }
    }
}

java {
    withSourcesJar()
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("chesttracker") {
            sourceSet(sourceSets["client"])
        }
    }

    log4jConfigs.from(file("log4j2.xml"))

    runConfigs.configureEach {
        this.programArgs.addAll("--username JackFred".split(" "))
    }

    accessWidenerPath.set(file("src/client/resources/chesttracker.accesswidener"))
}

// Configuration for embedding Where Is It (Temporary workaround)
val embedWhereIsIt by configurations.creating {
    isTransitive = false
    isCanBeResolved = true
    isCanBeConsumed = false
}
// Configuration for embedding JackFredLib (Temporary workaround)
val embedJackFredLib by configurations.creating {
    isTransitive = false
    isCanBeResolved = true
    isCanBeConsumed = false
}
dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${properties["minecraft_version"]}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${properties["parchment_version"]}@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:${properties["loader_version"]}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${properties["fabric-api_version"]}")

    // Where Is It - embed if the flag is enabled
    if (isBundlingWhereIsIt) {
        modCompileOnly(files("libs/whereisit-2.6.4+1.21.9.jar"))
        modLocalRuntime(files("libs/whereisit-2.6.4+1.21.9.jar"))
        embedWhereIsIt(files("libs/whereisit-2.6.4+1.21.9.jar"))

        modCompileOnly(files("libs/jackfredlib-1.10.6+1.21.9.jar"))
        modLocalRuntime(files("libs/jackfredlib-1.10.6+1.21.9.jar"))
        embedJackFredLib(files("libs/jackfredlib-1.10.6+1.21.9.jar"))
    } else {
        // We use from libs
        modCompileOnly(files("libs/whereisit-2.6.4+1.21.9+dev-ecfb5f2.jar"))
        modLocalRuntime(files("libs/whereisit-2.6.4+1.21.9+dev-ecfb5f2.jar"))
    }

    // Config
    modImplementation("dev.isxander:yet-another-config-lib:${properties["yacl_version"]}") {
        exclude(group = "com.terraformersmc", module = "modmenu")
    }

    // dev util
    //modLocalRuntime("dev.emi:emi-fabric:${properties["emi_version"]}")
    //modLocalRuntime("maven.modrinth:jsst:mc1.20-0.3.12")

    ////////////////
    // MOD COMPAT //
    ////////////////

    // Searchables
    modCompileOnly("com.blamejared.searchables:Searchables-fabric-${properties["searchables_version"]}") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api")
    }
    modLocalRuntime("com.blamejared.searchables:Searchables-fabric-${properties["searchables_version"]}") {
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api")
    }
    if (isBundlingSearchables) include("com.blamejared.searchables:Searchables-fabric-${properties["searchables_version"]}")

    // Mod Menu
    modCompileOnly("com.terraformersmc:modmenu:${properties["modmenu_version"]}")
    modLocalRuntime("com.terraformersmc:modmenu:${properties["modmenu_version"]}")

    // Shulker Box Tooltip
    modCompileOnly("com.misterpemodder:shulkerboxtooltip-fabric:${properties["shulkerboxtooltip_version"]}")
    // WTHIT
    modCompileOnly("mcp.mobius.waila:wthit-api:${properties["wthit_version"]}")
    // Jade
    modCompileOnly("maven.modrinth:jade:${properties["jade_version"]}")
    modLocalRuntime("maven.modrinth:jade:${properties["jade_version"]}")
    // Litematica
    modCompileOnly(fileTree("libs"))
}

tasks.withType<ProcessResources>().configureEach {
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to version))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

//Where Is It integration
val extractWhereIsIt = tasks.register<Copy>("extractWhereIsIt") {
    onlyIf { isBundlingWhereIsIt }

    from({
        embedWhereIsIt.resolve().map { zipTree(it) }
    })

    into(layout.buildDirectory.dir("whereisit-extracted"))

    exclude(
        "META-INF/*.SF",
        "META-INF/*.DSA",
        "META-INF/*.RSA",
        "META-INF/MANIFEST.MF",
        "fabric.mod.json"
    )

    duplicatesStrategy = DuplicatesStrategy.WARN

    doLast {
        val refmaps = fileTree(destinationDir).matching {
            include("**/*.refmap.json")
        }
        println("Found refmaps:")
        refmaps.forEach { println("  ${it.relativeTo(destinationDir)}") }
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from("LICENSE") {
        rename { "${it}_${properties["archivesBaseName"]}"}
    }

    // ✅ Встраивай Where Is It И JackFredLib
    if (isBundlingWhereIsIt) {
        into("META-INF/jars") {
            from(embedWhereIsIt) {
                rename { "whereisit-2.6.4+1.21.9.jar" }
            }
            // ✅ Также добавь JackFredLib!
            from(embedJackFredLib) {
                rename { "jackfredlib-1.10.6+1.21.9.jar" }
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"]!!)
        }
    }

    repositories {
        if (!System.getenv().containsKey("CI")) repositories.mavenLocal()

        if (canPublish) {
            maven {
                name = "JackFredMaven"
                url = uri("https://maven.jackf.red/releases/")
                content {
                    includeGroupByRegex("red.jackf.*")
                }
                credentials {
                    username = properties["jfmaven.user"]?.toString() ?: System.getenv("JACKFRED_MAVEN_USER")
                    password = properties["jfmaven.key"]?.toString() ?: System.getenv("JACKFRED_MAVEN_PASS")
                }
            }
        }
    }
}

if (canPublish) {
    val lastTag = if (System.getenv("PREVIOUS_TAG") == "NONE") null else System.getenv("PREVIOUS_TAG")
    val newTag = "v$version"

    var generateChangelogTask: TaskProvider<GenerateChangelogTask>? = null

    if (lastTag != null) {
        val changelogHeader = if (properties.containsKey("changelogHeaderAddon")) {
            val addonProp: String = properties["changelogHeaderAddon"]!!.toString()
            if (addonProp.isNotBlank()) addonProp else null
        } else null

        val changelogFileText = rootProject.file("changelogs/${properties["mod_version"]}.md")
            .takeIf { it.exists() }
            ?.readText()

        generateChangelogTask = tasks.register<GenerateChangelogTask>("generateChangelog") {
            this.lastTag.set(lastTag)
            this.newTag.set(newTag)
            githubUrl.set(properties["github_url"]!!.toString())
            prefixFilters.set(properties["changelog_filter"]!!.toString().split(","))

            val bundledParts = mutableListOf<String>()
            if (isBundlingWhereIsIt) {
                bundledParts.add("  - Where Is It: ${properties["where-is-it_version"]}")
            }
            if (isBundlingSearchables) {
                bundledParts.add("  - Searchables: ${properties["searchables_version"]}")
            }

            val bundledText = if (bundledParts.isNotEmpty()) {
                "Bundled:\n${bundledParts.joinToString("\n")}"
            } else null

            prologue.set(listOfNotNull(changelogHeader, changelogFileText, bundledText).joinToString(separator = "\n\n"))
        }
    }

    val changelogTextProvider = if (generateChangelogTask != null) {
        provider { generateChangelogTask!!.get().changelogFile.get().asFile.readText() }
    } else {
        provider { "No Changelog Generated" }
    }

    tasks.named<GithubReleaseTask>("githubRelease") {
        generateChangelogTask?.let { dependsOn(it) }

        authorization = System.getenv("GITHUB_TOKEN")?.let { "Bearer $it" }
        owner = properties["github_owner"]!!.toString()
        repo = properties["github_repo"]!!.toString()
        tagName = newTag
        releaseName = "${properties["mod_name"]} $newTag"
        targetCommitish = grgit!!.branch.current().name
        releaseAssets.from(
            tasks["remapJar"].outputs.files,
            tasks["remapSourcesJar"].outputs.files,
        )

        body = changelogTextProvider
    }

    if (listOf("CURSEFORGE_TOKEN", "MODRINTH_TOKEN").any { System.getenv().containsKey(it) }) {
        publishMods {
            changelog.set(changelogTextProvider)
            type.set(when(properties["release_type"]) {
                "release" -> ReleaseType.STABLE
                "beta" -> ReleaseType.BETA
                else -> ReleaseType.ALPHA
            })
            modLoaders.add("fabric")
            modLoaders.add("quilt")
            file.set(tasks.named<RemapJarTask>("remapJar").get().archiveFile)

            if (System.getenv().containsKey("CURSEFORGE_TOKEN") || dryRun.get()) {
                curseforge {
                    projectId.set("397217")
                    accessToken.set(System.getenv("CURSEFORGE_TOKEN"))
                    properties["game_versions_curse"]!!.toString().split(",").forEach {
                        minecraftVersions.add(it)
                    }
                    displayName.set("${properties["prefix"]!!} ${properties["mod_name"]!!} ${version.get()}")
                    listOf("fabric-api", "yacl").forEach {
                        requires { slug.set(it) }
                    }

                    // Where Is It as built-in or optional
                    if (isBundlingWhereIsIt) {
                        embeds { slug.set("where-is-it") }
                    } else {
                        optional { slug.set("where-is-it") }
                    }

                    listOf("emi", "jei", "roughly-enough-items", "modmenu", "shulkerboxtooltip", "wthit", "jade").forEach {
                        optional { slug.set(it) }
                    }

                    if (isBundlingSearchables) {
                        embeds { slug.set("searchables") }
                    } else {
                        optional { slug.set("searchables") }
                    }
                }
            }

            if (System.getenv().containsKey("MODRINTH_TOKEN") || dryRun.get()) {
                modrinth {
                    accessToken.set(System.getenv("MODRINTH_TOKEN"))
                    projectId.set("ni4SrKmq")
                    properties["game_versions_mr"]!!.toString().split(",").forEach {
                        minecraftVersions.add(it)
                    }
                    displayName.set("${properties["mod_name"]!!} ${version.get()}")
                    listOf("fabric-api", "yacl").forEach {
                        requires { slug.set(it) }
                    }

                    // Where Is It as built-in or optional
                    if (isBundlingWhereIsIt) {
                        embeds { slug.set("where-is-it") }
                    } else {
                        optional { slug.set("where-is-it") }
                    }

                    listOf("emi", "jei", "rei", "modmenu", "shulkerboxtooltip", "wthit", "jade").forEach {
                        optional { slug.set(it) }
                    }

                    if (isBundlingSearchables) {
                        embeds { slug.set("searchables") }
                    } else {
                        optional { slug.set("searchables") }
                    }
                }
            }
        }
    }
}

tasks.register<UpdateDependenciesTask>("updateModDependencies") {
    mcVersion.set(properties["minecraft_version"]!!.toString())
    loader.set("fabric")
}