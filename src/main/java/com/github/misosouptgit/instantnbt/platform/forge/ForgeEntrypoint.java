package com.github.misosouptgit.instantnbt.platform.forge;

//? forge {
import com.github.misosouptgit.instantnbt.InstantNBT;
import net.minecraftforge.fml.common.Mod;

@Mod(InstantNBT.MOD_ID)
public class ForgeEntrypoint {
	public ForgeEntrypoint() {
		InstantNBT.init();
	}
}
//?}
