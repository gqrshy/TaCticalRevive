package com.tacticalrevive.client;

import com.tacticalrevive.config.TacticalReviveConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;

/**
 * Renders the downed state HUD overlay.
 */
@Environment(EnvType.CLIENT)
public final class BleedingHudRenderer {

    private static final int GIVE_UP_SECONDS = 5;

    private BleedingHudRenderer() {
    }

    /**
     * Render the downed HUD if the local player is downed.
     */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || client.level == null) {
            return;
        }

        // Check if local player is downed
        ClientBleedingState.BleedingStateData state =
                ClientBleedingState.getState(client.player.getUUID());

        if (state == null) {
            return;
        }

        renderDownedOverlay(graphics, client, state);
    }

    private static void renderDownedOverlay(GuiGraphics graphics, Minecraft client,
                                             ClientBleedingState.BleedingStateData state) {
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // Red tint overlay
        graphics.fill(RenderType.guiOverlay(), 0, 0, screenWidth, screenHeight, 0x40FF0000);

        // Status text - "DOWNED"
        Component statusText = Component.translatable("tacticalrevive.status.downed");
        int statusWidth = client.font.width(statusText);
        graphics.drawString(client.font, statusText, centerX - statusWidth / 2, centerY - 50, 0xFF5555);

        // Time remaining
        int totalSeconds = state.timeLeft() / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String timeText = String.format("%02d:%02d", minutes, seconds);

        Component timeComponent = Component.translatable("tacticalrevive.hud.time_left", timeText);
        int timeWidth = client.font.width(timeComponent);
        graphics.drawString(client.font, timeComponent, centerX - timeWidth / 2, centerY - 30, 0xFFFFFF);

        // Revive progress label
        Component progressLabel = Component.translatable("tacticalrevive.hud.revive_progress");
        int labelWidth = client.font.width(progressLabel);
        graphics.drawString(client.font, progressLabel, centerX - labelWidth / 2, centerY - 18, 0xAAAAAA);

        // Revive progress bar
        float progress = state.reviveProgress() / TacticalReviveConfig.getRequiredReviveProgress();
        int barWidth = 100;
        int barHeight = 8;
        int barX = centerX - barWidth / 2;
        int barY = centerY - 5;

        // Background
        graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);

        // Progress fill
        int fillWidth = (int) (barWidth * Math.min(progress, 1.0f));
        int fillColor = progress > 0 ? 0xFF00FF00 : 0xFF666666;
        graphics.fill(barX, barY, barX + fillWidth, barY + barHeight, fillColor);

        // Progress text
        String progressText = String.format("%.0f%%", Math.min(progress * 100, 100f));
        int progressWidth = client.font.width(progressText);
        graphics.drawString(client.font, progressText, centerX - progressWidth / 2, barY + barHeight + 5, 0xFFFFFF);

        // Help text with translatable key name and give up time
        Component giveUpKey = Component.translatable("tacticalrevive.hud.give_up_key");
        Component helpText = Component.translatable("tacticalrevive.hud.wait_for_help",
                giveUpKey.getString(), GIVE_UP_SECONDS);
        int helpWidth = client.font.width(helpText);
        graphics.drawString(client.font, helpText, centerX - helpWidth / 2, centerY + 30, 0xAAAAAA);
    }
}
