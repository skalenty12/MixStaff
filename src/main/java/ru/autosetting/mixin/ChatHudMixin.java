package ru.autosetting.mixin;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.autosetting.config.ModConfig;
import ru.autosetting.util.ChatTime;

/** ChatTime: подставляет время перед текстом сообщения при добавлении в чат. */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 1
    )
    private Text autosetting$withTime(Text message) {
        if (message == null || !ModConfig.get().chatTime) {
            return message;
        }
        return ChatTime.prefix(message);
    }
}
