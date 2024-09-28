package me.evvv.minigameplugin;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

public class listener implements PluginMessageListener, Listener {
    private final MinigamePlugin plugin;
    private String selectedMinigame;

    public listener(MinigamePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte[] message) {
        plugin.getLogger().info("Received on channel: " + channel);
        if (!channel.equals("evvv:minigamechannel")) {
            return;
        }

        // Use Guava's ByteArrayDataInput for reading the message
        ByteArrayDataInput in = ByteStreams.newDataInput(message);

        // Step 1: Read the correct subchannel "MinigameChannel"
        String subchannel = in.readUTF();
        plugin.getLogger().info("Subchannel: " + subchannel);

        if (subchannel.equals("MinigameChannel")) {
            // Step 2: Read the message length and content
            short len = in.readShort();
            byte[] msgBytes = new byte[len];
            in.readFully(msgBytes);

            // Step 3: Handle the actual message
            try (DataInputStream msgIn = new DataInputStream(new ByteArrayInputStream(msgBytes))) {
                String receivedMinigame = msgIn.readUTF();
                selectedMinigame = receivedMinigame;

                // Log for debugging
                plugin.getLogger().info("Received minigame selection: " + selectedMinigame);
                plugin.loadA(selectedMinigame);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public String getMinigame() {
        return selectedMinigame;
    }
}