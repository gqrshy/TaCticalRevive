package com.tacticalrevive.client;

import com.tacticalrevive.config.TacticalReviveConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * Renders the bleeding state HUD overlay.
 */
@Environment(EnvType.CLIENT)
public final class BleedingHudRenderer {

    private BleedingHudRenderer() {
    }

    /**
     * Render the bleeding HUD if the local player is bleeding.
     */
    public static void render(GuiGraphics graphics, float tickDelta) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            return;
        }

        // Check if local player is bleeding
        ClientBleedingState.BleedingStateData state =
                ClientBleedingState.getState(client.player.getUUID());

        if (state == null) {
            return;
        }

        renderBleedingOverlay(graphics, client, state);
    }

    private static void renderBleedingOverlay(GuiGraphics graphics, Minecraft client,
                                               ClientBleedingState.BleedingStateData state) {
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Red tint overlay
        graphics.fill(RenderType.guiOverlay(), 0, 0, screenWidth, screenHeight, 0x40FF0000);

        // Time remaining
        int seconds = state.timeLeft() / 20;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        String timeText = String.format("%02d:%02d", minutes, seconds);

        Component timeComponent = Component.translatable("tacticalrevive.hud.time_left", timeText);
        int timeWidth = client.font.width(timeComponent);
        graphics.drawString(client.font, timeComponent, centerX - timeWidth / 2, centerY - 30, 0xFFFFFF);

        // Revive progress bar
        float progress = state.reviveProgress() / TacticalReviveConfig.getRequiredReviveProgress();
        int barWidth = 100;
        int barHeight = 8;
        int barX = centerX - barWidth / 2;
        int barY = centerY - 10;

        // Background
        graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // Progress fill
        int fillWidth = (int) (barWidth * progress);
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00FF00);

        // Progress text
        String progressText = String.format("%.0f%%", progress * 100);
        int progressWidth = client.font.width(progressText);
        graphics.drawString(client.font, progressText, centerX - progressWidth / 2, barY + barHeight + 5, 0xFFFFFF);

        // Help text
        Component helpText = Component.translatable("tacticalrevive.hud.wait_for_help");
        int helpWidth = client.font.width(helpText);
        graphics.drawString(client.font, helpText, centerX - helpWidth / 2, centerY + 30, 0xAAAAAA);
    }
}
