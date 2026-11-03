package ru.autosetting.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.autosetting.ui.MixStaffMultiplayerScreen;
import ru.autosetting.ui.MixStaffTitleScreen;

/** Подменяет ванильные титульный экран и список серверов на меню MixStaff. */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void mixstaff$replaceScreens(Screen screen, CallbackInfo info) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (screen != null && screen.getClass() == TitleScreen.class) {
            client.setScreen(new MixStaffTitleScreen());
            info.cancel();
            return;
        }
        // Выход с сервера ведёт в ванильный список серверов — заменяем его
        // на меню MixStaff, чтобы игрок оставался в оформлении мода.
        if (screen != null && screen.getClass() == MultiplayerScreen.class) {
            client.setScreen(new MixStaffMultiplayerScreen());
            info.cancel();
            return;
        }
        // Screen#close() вызывает setScreen(null), а ваниль при world == null
        // сама создаёт TitleScreen() прямо внутри метода, мимо этой инъекции.
        if (screen == null && client.world == null) {
            client.setScreen(new MixStaffTitleScreen());
            info.cancel();
        }
    }
}
