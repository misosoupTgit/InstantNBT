@file:OptIn(dev.kikugie.stonecutter.StonecutterExperimentalAPI::class)

import net.darkhax.curseforgegradle.TaskPublishCurseForge
import net.darkhax.curseforgegradle.Constants as CFConstants
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named

plugins {
	alias(libs.plugins.stonecutter)
	alias(libs.plugins.loom.back.compat).apply(false)
	alias(libs.plugins.neoforged.moddev).apply(false)
	alias(libs.plugins.jsonlang.postprocess).apply(false)
	alias(libs.plugins.mod.publish.plugin).apply(false)
	alias(libs.plugins.kotlin.jvm).apply(false)
	alias(libs.plugins.devtools.ksp).apply(false)
	alias(libs.plugins.fletching.table).apply(false)
	alias(libs.plugins.legacyforge.moddev).apply(false)
	alias(libs.plugins.curseforgegradle)
}

stonecutter active file(".sc_active_version")

tasks.register("runActiveClient") {
	group = "stonecutter"
	description = "Run client of the active Stonecutter version"
	dependsOn(stonecutter.current!!.project + ":runClient")
}

tasks.register("runActiveServer") {
	group = "stonecutter"
	description = "Run server of the active Stonecutter version"
	dependsOn(stonecutter.current!!.project + ":runServer")
}

stonecutter parameters {
	constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge", "forge")
	swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
	swaps["mod_id"] = "\"${properties.get<String>("mod.id")}\";"
	swaps["mod_name"] = "\"${properties.get<String>("mod.name")}\";"
	swaps["mod_group"] = "\"${properties.get<String>("mod.group")}\";"
	swaps["minecraft"] = "\"${current.version}\";"
	constants["release"] = properties.get<String>("mod.id") != "modtemplate"
}

for (version in stonecutter.versions.map { it.version }.distinct()) tasks.register("publish$version") {
	group = "publishing"
	dependsOn(stonecutter.tasks.named("publishMods") { metadata.version == version })
}

tasks.register("buildAndCollectAll") {
	group = "build"
	description = "Build all Stonecutter versions and collect jars into build/dist"
	dependsOn(stonecutter.tasks.named("buildAndCollect") { true })
}

tasks.register<JavaExec>("runTierAMicroBenchmark") {
	group = "verification"
	description = "Run InstantNBT headless micro-benchmarks on Tier A (1.20.1-forge)"
	val forge = project(":1.20.1-forge")
	dependsOn(forge.tasks.named("classes"))
	classpath = forge.the<org.gradle.api.tasks.SourceSetContainer>()["main"].runtimeClasspath
	mainClass.set("com.github.misosouptgit.instantnbt.benchmark.MicroBenchmarkMain")
	workingDir = rootDir
}

// --- CurseForge bulk upload (CurseForgeGradle; requires JVM 25+) ---

data class CurseUploadTarget(
	val loader: String,
	val minecraft: String,
	val additionalVersions: List<String> = emptyList(),
)

// Set CURSEFORGE_PROJECT_ID (env/.env) or replace this placeholder before publishing.
val curseProjectId = System.getenv("CURSEFORGE_PROJECT_ID")?.takeIf { it.isNotBlank() } ?: ""
val curseModId = "instantnbt"
val curseModVersion = "0.1.0"

// Oldest → newest within each loader. additionalVersions match stonecutter.properties.toml.
// Loader order per Project Plan 3.7: Forge → NeoForge → Fabric.
val curseUploadTargets: List<CurseUploadTarget> = listOf(
	CurseUploadTarget("forge", "1.17.1", listOf("1.17")),
	CurseUploadTarget("forge", "1.18.2", listOf("1.18", "1.18.1")),
	CurseUploadTarget("forge", "1.19.2", listOf("1.19", "1.19.1")),
	CurseUploadTarget("forge", "1.19.4", listOf("1.19.3")),
	CurseUploadTarget("forge", "1.20.1", listOf("1.20")),
	CurseUploadTarget("neoforge", "1.20.4", listOf("1.20.2", "1.20.3")),
	CurseUploadTarget("neoforge", "1.20.6", listOf("1.20.5")),
	CurseUploadTarget("neoforge", "1.21.1", listOf("1.21")),
	CurseUploadTarget("neoforge", "1.21.3", listOf("1.21.2")),
	CurseUploadTarget("neoforge", "1.21.4"),
	CurseUploadTarget("neoforge", "1.21.5"),
	CurseUploadTarget("neoforge", "1.21.8", listOf("1.21.6", "1.21.7")),
	CurseUploadTarget("neoforge", "1.21.11", listOf("1.21.9", "1.21.10")),
	CurseUploadTarget("neoforge", "26.1.2", listOf("26.1.1", "26.1")),
	CurseUploadTarget("fabric", "1.16.5"),
	CurseUploadTarget("fabric", "1.17.1", listOf("1.17")),
	CurseUploadTarget("fabric", "1.18.2", listOf("1.18", "1.18.1")),
	CurseUploadTarget("fabric", "1.19.2", listOf("1.19", "1.19.1")),
	CurseUploadTarget("fabric", "1.19.4", listOf("1.19.3")),
	CurseUploadTarget("fabric", "1.20.1", listOf("1.20")),
	CurseUploadTarget("fabric", "1.20.4", listOf("1.20.2", "1.20.3")),
	CurseUploadTarget("fabric", "1.20.6", listOf("1.20.5")),
	CurseUploadTarget("fabric", "1.21.1", listOf("1.21")),
	CurseUploadTarget("fabric", "1.21.3", listOf("1.21.2")),
	CurseUploadTarget("fabric", "1.21.4"),
	CurseUploadTarget("fabric", "1.21.5"),
	CurseUploadTarget("fabric", "1.21.8", listOf("1.21.6", "1.21.7")),
	CurseUploadTarget("fabric", "1.21.11", listOf("1.21.9", "1.21.10")),
	CurseUploadTarget("fabric", "26.1.2", listOf("26.1.1", "26.1")),
)

tasks.register<TaskPublishCurseForge>("publishCurseForgeAll") {
	group = "publishing"
	description = "Upload jars from build/dist to CurseForge (Forge → NeoForge → Fabric, oldest MC first)"
	// Jars are expected in build/dist (e.g. downloaded from the GitHub Release). Does not rebuild.

	if (curseProjectId.isBlank()) {
		error("CURSEFORGE_PROJECT_ID is not set (environment or replace placeholder in stonecutter.gradle.kts)")
	}

	fun readCurseToken(): String {
		System.getenv("CURSEFORGE_TOKEN")?.takeIf { it.isNotBlank() }?.let { return it }
		val envFile = rootProject.file(".env")
		if (envFile.exists()) {
			envFile.readLines().forEach { raw ->
				val line = raw.trim()
				if (line.isEmpty() || line.startsWith("#")) return@forEach
				val idx = line.indexOf('=')
				if (idx <= 0) return@forEach
				val key = line.substring(0, idx).trim()
				if (key != "CURSEFORGE_TOKEN") return@forEach
				var value = line.substring(idx + 1).trim()
				if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
					value = value.substring(1, value.length - 1)
				}
				if (value.isNotBlank()) return value
			}
		}
		error("CURSEFORGE_TOKEN is not set (environment or .env)")
	}

	apiToken = readCurseToken()
	apiEndpoint = "https://minecraft.curseforge.com"
	disableVersionDetection()

	// Minimal changelog (displayName omitted — CurseForge uses the jar file name).
	val changelogFile = rootProject.file("CHANGELOG.md")
	val changelogText = when {
		!changelogFile.exists() -> " "
		changelogFile.readText().isBlank() -> " "
		else -> changelogFile.readText()
	}

	val distDir = layout.buildDirectory.dir("dist").get().asFile
	val missing = mutableListOf<String>()

	curseUploadTargets.forEach { target ->
		val jarFile = distDir.resolve("$curseModId-$curseModVersion-${target.loader}+${target.minecraft}.jar")
		if (!jarFile.isFile) {
			missing += jarFile.name
			return@forEach
		}
		val mainFile = upload(curseProjectId, jarFile)
		mainFile.changelog = changelogText
		mainFile.changelogType = "markdown"
		mainFile.releaseType = CFConstants.RELEASE_TYPE_RELEASE
		mainFile.addEnvironment("Client", "Server")

		when (target.loader) {
			"fabric" -> {
				mainFile.addModLoader("Fabric")
				mainFile.addRequirement("fabric-api", "architectury-api")
			}
			"forge" -> {
				mainFile.addModLoader("Forge")
				mainFile.addRequirement("architectury-api")
			}
			"neoforge" -> {
				mainFile.addModLoader("NeoForge")
				mainFile.addRequirement("architectury-api")
			}
			else -> error("Unknown loader: ${target.loader}")
		}

		val gameVersions = listOf(target.minecraft) + target.additionalVersions
		mainFile.addGameVersion(*gameVersions.toTypedArray())
	}

	if (missing.isNotEmpty()) {
		error("Missing jars in build/dist:\n" + missing.joinToString("\n"))
	}
	logger.lifecycle("CurseForge upload queued: ${getUploadArtifacts().size} files -> project $curseProjectId")
}
