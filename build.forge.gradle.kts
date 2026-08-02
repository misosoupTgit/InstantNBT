plugins {
	id("mod-platform")
	id("net.neoforged.moddev.legacyforge")
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
	loader = "forge"
	dependencies {
		required("minecraft") {
			forgeLikeVersionRange = prop("deps.minecraft")
		}
		required("forge") {
			forgeLikeVersionRange.set("[1,)")
		}
		required("architectury") {
			slug("architectury-api")
			forgeLikeVersionRange = "[${prop("deps.architectury")},)"
		}
	}
}

legacyForge {
	version = "${prop("deps.minecraft")}-${prop("deps.forge")}"

	validateAccessTransformers = true

	accessTransformers.from(
		rootProject.file("src/main/resources/aw/${sc.current.version}.cfg")
	)

	mixin {
		add(sourceSets.main.get(), "${prop("mod.id")}.refmap.json")
		config("${prop("mod.id")}.mixins.json")
	}

	runs {
		register("client") {
			client()
			gameDirectory = file("run/")
			ideName = "Forge Client (${sc.current.version})"
			programArgument("--username=Dev")
		}
		register("server") {
			server()
			gameDirectory = file("run/")
			ideName = "Forge Server (${sc.current.version})"
		}
	}

	mods {
		register(prop("mod.id")) {
			sourceSet(sourceSets["main"])
		}
	}
}

repositories {
	mavenCentral()
	strictMaven("https://maven.architectury.dev/", "dev.architectury", "me.shedaniel") { name = "Architectury" }
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
	maven("https://cursemaven.com/") { name = "CurseMaven" }
}

dependencies {
	val architecturyGroup = if (sc.current.parsed <= "1.16.5") "me.shedaniel" else "dev.architectury"
	// Published Forge mods are SRG-mapped; remapping config is required for official-mapped userdev.
	modImplementation("$architecturyGroup:architectury-forge:${prop("deps.architectury")}")
	annotationProcessor("org.spongepowered:mixin:0.8.5:processor")

	// Optional Tier1 compileOnly (Plan 3.6). Enable via stonecutter props:
	// deps.ae2_curse = "<projectId>:<fileId>"  e.g. "223794:4582991"
	// deps.create_curse = "<projectId>:<fileId>"
	val ae2Curse = runCatching { prop("deps.ae2_curse") }.getOrNull()
	val createCurse = runCatching { prop("deps.create_curse") }.getOrNull()
	if (!ae2Curse.isNullOrBlank() && sc.current.version.startsWith("1.20.1")) {
		compileOnly("curse.maven:ae2-${ae2Curse}")
	}
	if (!createCurse.isNullOrBlank() && sc.current.version.startsWith("1.20.1")) {
		compileOnly("curse.maven:create-${createCurse}")
	}
}

tasks.named("createMinecraftArtifacts") {
	dependsOn(tasks.named("stonecutterGenerate"))
}
