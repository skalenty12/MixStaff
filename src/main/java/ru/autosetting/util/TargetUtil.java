package ru.autosetting.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/** Поиск игрока под прицелом (с запасом по дальности и углу). */
public final class TargetUtil {
    private static final double RANGE = 8.0D;
    private static final double MIN_DOT = 0.94D;

    private TargetUtil() {
    }

    public static PlayerEntity findTarget(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return null;
        }
        if (client.crosshairTarget instanceof EntityHitResult hit
                && hit.getEntity() instanceof PlayerEntity player) {
            return player;
        }

        Vec3d eye = client.player.getEyePos();
        Vec3d look = client.player.getRotationVec(1.0F);
        PlayerEntity best = null;
        double bestDot = MIN_DOT;
        for (PlayerEntity player : client.world.getPlayers()) {
            if (player == client.player || !player.isAlive()) {
                continue;
            }
            Vec3d to = player.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance < 0.1D || distance > RANGE) {
                continue;
            }
            double dot = to.normalize().dotProduct(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = player;
            }
        }
        return best;
    }

    public static String findTargetNick(MinecraftClient client) {
        PlayerEntity target = findTarget(client);
        return target == null ? null : target.getGameProfile().getName();
    }
}
