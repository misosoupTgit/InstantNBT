package com.github.misosouptgit.instantnbt.ownership;

/**
 * Module-level ownership domain (who created / holds the tag).
 */
public enum ModuleDomain {
	RUNTIME,
	NETWORK,
	SERIALIZER,
	COMPAT,
	EXTERNAL
}
