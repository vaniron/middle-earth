package net.jukoz.me.utils;

import net.minecraft.nbt.NbtCompound;

public class PlayerMovementData {
    public static final String KEY = "player_afk_data";
    public static final int MAX_AFK_TIME = 100;

    public static int addAFKTime(IEntityDataSaver player, int amount) {
        NbtCompound nbt = player.getPersistentData();
        int current = nbt.getInt(KEY);
        int newValue = Math.max(0, Math.min(MAX_AFK_TIME, current + amount));

        if (newValue != current) {  // ← **KEY FIX: Only write if changed**
            nbt.putInt(KEY, newValue);
        }

        return newValue;
    }

    public static int readAFK(IEntityDataSaver player) {
        return player.getPersistentData().getInt(KEY);
    }

    public static void resetAFK(IEntityDataSaver player) {
        player.getPersistentData().putInt(KEY, 0);  // Always changes from non-zero
    }
}