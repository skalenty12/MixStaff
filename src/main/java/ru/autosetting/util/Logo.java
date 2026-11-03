package ru.autosetting.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import ru.autosetting.AutoSettingClient;

import java.io.InputStream;

/** Логотип MixStaff: ленивая регистрация текстуры и отрисовка. */
public final class Logo {
    public static final Identifier TEXTURE = Identifier.of("autosetting", "textures/logo.png");
    private static boolean registered;

    private Logo() {
    }

    public static void ensureRegistered() {
        if (registered) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }
            try (InputStream input = Logo.class.getResourceAsStream("/assets/autosetting/textures/logo.png")) {
            if (input == null) {
                return;
            }
            NativeImage image = NativeImage.read(input);
            client.getTextureManager().registerTexture(TEXTURE, new NativeImageBackedTexture(image));
            registered = true;
        } catch (Exception exception) {
            AutoSettingClient.LOGGER.warn("Не удалось загрузить логотип MixStaff", exception);
        }
    }

    public static void draw(DrawContext context, int x, int y, int size) {
        ensureRegistered();
        if (!registered) {
            UiDraw.roundRect(context, x, y, size, size, size / 4, 0xFF8B5CF6);
            return;
        }
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0.0F, 0.0F,
                size, size, size, size);
    }
}
