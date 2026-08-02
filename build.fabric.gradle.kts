plugins {
	id("mod-platform")
	id("dev.kikugie.loom-back-compat")
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)

	replacements.string(current.parsed <= "1.16.5") {
		replace("dev.architectury", "me.shedaniel.architectury")
	}
	replacements.string(current.parsed >= "1.21.11") {
		replace("ResourceLocation", "Identifier")
		replace("location()", "identifier()")
	}
}

platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			fabricLikeVersionRange = prop("deps.minecraft")
		}
		required("fabricloader") {
			fabricLikeVersionRange = ">=${prop("deps.fabric-loader")}"
		}
		required("architectury") {
			slug("architectury-api")
			fabricLikeVersionRange = ">=${prop("deps.architectury")}"
		}
		// Architectury Fabric depends on Fabric API; declare it so loaders resolve it cleanly.
		required("fabric-api") {
			slug("fabric-api")
			fabricLikeVersionRange = ">=${prop("deps.fabric-api")}"
		}
		optional("modmenu") {}
	}
}

loom {
	val awFile = rootProject.file("src/main/resources/aw/${sc.current.version}.accesswidener")
	if (awFile.exists() && awFile.readText().lines().any { it.isNotBlank() && !it.startsWith("accessWidener") && !it.startsWith("#") }) {
		accessWidenerPath = awFile
	}
	runs.named("client") {
		client()
		ideConfigGenerated(true)
		runDir = "run/"
		environment = "client"
		programArgs("--username=Dev")
		configName = "Fabric Client"
	}
	runs.named("server") {
		server()
		ideConfigGenerated(true)
		runDir = "run/"
		environment = "server"
		configName = "Fabric Server"
	}
}

repositories {
	mavenCentral()
	strictMaven("https://maven.architectury.dev/", "dev.architectury", "me.shedaniel") { name = "Architectury" }
	strictMaven("https://maven.terraformersmc.com/", "com.terraformersmc") { name = "TerraformersMC" }
	strictMaven("https://maven.nucleoid.xyz/", "eu.pb4") { name = "Nucleoid" }
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
}

configurations.all {
	resolutionStrategy {
		force("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	if (sc.current.parsed < "26") {
		mappings(loom.layered {
			officialMojangMappings()
			if (hasProperty("deps.parchment"))
				parchment("org.parchmentmc.data:parchment-${prop("deps.parchment")}@zip")
		})
	}
	modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	val architecturyGroup = if (sc.current.parsed <= "1.16.5") "me.shedaniel" else "dev.architectury"
	modImplementation("$architecturyGroup:architectury-fabric:${prop("deps.architectury")}")
	modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}
