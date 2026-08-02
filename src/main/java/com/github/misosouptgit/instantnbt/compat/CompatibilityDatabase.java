package com.github.misosouptgit.instantnbt.compat;

import com.github.misosouptgit.instantnbt.InstantNBT;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compatibility Database loader (Project Plan 13.4).
 */
public final class CompatibilityDatabase {
	private static final Gson GSON = new Gson();
	private final List<CompatRule> rules = new ArrayList<>();

	public void loadDefaults() {
		rules.clear();
		rules.addAll(CompatRule.builtinDefaults());
		List<CompatRule> fromResource = loadResource("/data/instantnbt/compat/default.json");
		if (!fromResource.isEmpty()) {
			rules.clear();
			rules.addAll(fromResource);
		}
	}

	public List<CompatRule> rules() {
		return Collections.unmodifiableList(rules);
	}

	public List<CompatRule> matching(String modId, String version) {
		List<CompatRule> out = new ArrayList<>();
		for (CompatRule rule : rules) {
			if (rule.modId().equalsIgnoreCase(modId) && rule.matchesVersion(version)) {
				out.add(rule);
			}
		}
		return out;
	}

	private static List<CompatRule> loadResource(String path) {
		try (InputStream in = CompatibilityDatabase.class.getResourceAsStream(path)) {
			if (in == null) {
				return Collections.emptyList();
			}
			JsonObject root = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			JsonArray arr = root.getAsJsonArray("rules");
			if (arr == null) {
				return Collections.emptyList();
			}
			List<CompatRule> parsed = new ArrayList<>();
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				CompatRule.Action action = CompatRule.Action.WARN;
				if (o.has("action")) {
					try {
						action = CompatRule.Action.valueOf(o.get("action").getAsString());
					} catch (IllegalArgumentException ignored) {
						action = CompatRule.Action.WARN;
					}
				}
				parsed.add(new CompatRule(
					o.has("id") ? o.get("id").getAsString() : "anon",
					o.has("modId") ? o.get("modId").getAsString() : "",
					o.has("versionRange") ? o.get("versionRange").getAsString() : "*",
					action,
					o.has("feature") && !o.get("feature").isJsonNull() ? o.get("feature").getAsString() : null,
					o.has("reason") ? o.get("reason").getAsString() : "",
					o.has("tier") ? o.get("tier").getAsInt() : 3
				));
			}
			return parsed;
		} catch (Exception ex) {
			InstantNBT.LOGGER.warn("Failed to parse compat DB {}: {}", path, ex.toString());
			return Collections.emptyList();
		}
	}
}
