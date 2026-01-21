package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.joml.Vector3d;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ViperNametags extends Module {
    // TODO: Change these URLs to your actual repository
    private static final String CURRENT_VERSION = "1.0.0";
    private static final String VERSION_URL = "https://raw.githubusercontent.com/YOUR_USERNAME/YOUR_REPO/main/version.txt";
    private static final String JAR_URL = "https://github.com/YOUR_USERNAME/YOUR_REPO/releases/download/latest/ViperAddon.jar";

    private final Set<String> viperList = new HashSet<>();
    private final Vector3d pos = new Vector3d();
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The scale of the nametag.")
        .defaultValue(1.5)
        .build()
    );

    private final Setting<Double> heightOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("height-offset")
        .description("How high above the player to render the tag.")
        .defaultValue(0.75)
        .min(0)
        .build()
    );

    private final Setting<Boolean> showPlayerTag = sgGeneral.add(new BoolSetting.Builder()
        .name("show-player-tag")
        .description("Whether to show the [PLAYER] tag for non-clan members.")
        .defaultValue(true)
        .build()
    );

    public ViperNametags() {
        super(AddonTemplate.CATEGORY, "viper-nametags", "Overrides nametags with VIPER clan tags.");
    }

    @Override
    public void onActivate() {
        updateDatabase();
        checkForUpdates();
    }

    private void updateDatabase() {
        new Thread(() -> {
            try {
                String[] response = Http.get("https://vipershopbot.onrender.com/api/members")
                    .sendJson(String[].class);

                if (response != null) {
                    synchronized (viperList) {
                        viperList.clear();
                        viperList.addAll(Arrays.asList(response));
                    }
                    info("Viper Database Synced: " + viperList.size() + " members.");
                }
            } catch (Exception e) {
                error("Viper API Connection Failed.");
            }
        }).start();
    }

    private void checkForUpdates() {
        new Thread(() -> {
            try {
                String latestVersion = Http.get(VERSION_URL).sendString();
                if (latestVersion != null) {
                    latestVersion = latestVersion.trim();
                    if (!latestVersion.equals(CURRENT_VERSION)) {
                        info("Update available: " + latestVersion + " (Current: " + CURRENT_VERSION + ")");
                        info("Downloading update...");
                        
                        File modsDir = new File(mc.runDirectory, "mods");
                        File newJar = new File(modsDir, "ViperAddon-" + latestVersion + ".jar");
                        
                        if (!newJar.exists()) {
                            try (InputStream in = new URL(JAR_URL).openStream();
                                 FileOutputStream out = new FileOutputStream(newJar)) {
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = in.read(buffer)) != -1) {
                                    out.write(buffer, 0, bytesRead);
                                }
                            }
                            info("Update downloaded: " + newJar.getName() + ". Please delete the old jar and restart.");
                        }
                    }
                }
            } catch (Exception e) {
                // Update check failed
            }
        }).start();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity player)) continue;
            if (player == mc.player) continue;

            // FIX: Interpolate position using event.tickDelta so the tag stays "stuck" to the player's head
            meteordevelopment.meteorclient.utils.Utils.set(pos, player, event.tickDelta);
            
            // FIX: Offset height: EyeHeight + 0.8 ensures it stacks clearly ABOVE the default Meteor nametag
            pos.add(0, player.getEyeHeight(player.getPose()) + heightOffset.get(), 0);

            // Match the scale setting from the main Nametags module
            double tagScale = scale.get();

            if (NametagUtils.to2D(pos, tagScale)) {
                String ign = player.getName().getString();
                boolean isViper;
                synchronized (viperList) {
                    isViper = viperList.contains(ign);
                }

                if (!isViper && !showPlayerTag.get()) continue;

                NametagUtils.begin(pos);
                meteordevelopment.meteorclient.renderer.text.TextRenderer.get().begin(1.0, false, true);

                // Use unicode escapes for color codes to avoid encoding issues (\u00A7 instead of section sign)
                // \u00A7b = Aqua, \u00A7l = Bold, \u00A7f = White, \u00A77 = Gray
                String text = isViper ? "[VIPER]" : "[PLAYER]";

                // FIX: Center the text horizontally (-width / 2)
                double w = meteordevelopment.meteorclient.renderer.text.TextRenderer.get().getWidth(text);
                double h = meteordevelopment.meteorclient.renderer.text.TextRenderer.get().getHeight();
                meteordevelopment.meteorclient.renderer.text.TextRenderer.get().render(text, -w / 2.0, -h / 2.0, Color.WHITE, true);
                
                meteordevelopment.meteorclient.renderer.text.TextRenderer.get().end();
                NametagUtils.end();
            }
        }
    }
}