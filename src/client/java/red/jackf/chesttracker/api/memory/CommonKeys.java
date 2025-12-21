package red.jackf.chesttracker.api.memory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import red.jackf.chesttracker.impl.ChestTracker;

/**
 * List of memory keys used by the core Chest Tracker implementation
 */
public interface CommonKeys {
    //////////////
    // Built-in //
    //////////////

    // Key used for the built-in ender chest compatibility.
    Identifier ENDER_CHEST_KEY = ChestTracker.id("ender_chest");

    // The dimension keys are gained from {@link Level#dimension()}'s location.
    Identifier OVERWORLD = Level.OVERWORLD.identifier();
    Identifier THE_NETHER = Level.NETHER.identifier();
    Identifier THE_END = Level.END.identifier();

    ///////////////////////
    // Mod Compatibility //
    ///////////////////////

    // Share Ender Chest - https://modrinth.com/mod/share-ender-chest
    Identifier SHARE_ENDER_CHEST = Identifier.fromNamespaceAndPath("shareenderchest", "contents");
}
