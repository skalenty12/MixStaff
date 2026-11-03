package ru.autosetting.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.autosetting.util.MenuBackdrop;

/** Заменяет фон выбранных экранов на живой фон MixStaff. */
@Mixin(Screen.class)
public class ScreenMixin {
    private static final MenuBackdrop MIXSTAFF_BACKDROP = new MenuBackdrop();

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void mixstaff$connectBackdrop(DrawContext context, int mouseX, int mouseY,
                                          float delta, CallbackInfo info) {
        Object currentScreen = this;
        if (currentScreen instanceof ConnectScreen screen) {
            MIXSTAFF_BACKDROP.render(context, screen.width, screen.height);
            info.cancel();
            return;
        }
        if (currentScreen instanceof OptionsScreen screen) {
            MIXSTAFF_BACKDROP.render(context, screen.width, screen.height);
            info.cancel();
            return;
        }
        String screenClassName = currentScreen.getClass().getName();
        if (screenClassName.equals("ru.vidtu.ias.screen.AccountScreen")
                || screenClassName.endsWith(".ResourcePackScreen")
                || screenClassName.endsWith(".PackScreen")) {
            Screen screen = (Screen) currentScreen;
            MIXSTAFF_BACKDROP.render(context, screen.width, screen.height);
            info.cancel();
        }
    }

}
