package me.evvv.minigameplugin;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.*;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BarFlag;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MinigamePlugin extends JavaPlugin implements Listener {
    private String Tminigame;
    private List<Player> currentPlayers = new ArrayList<>();
    private boolean start = false;
    private Location[] pillarLocations;
    // Constants for the Pillars of Fortune minigame
    private BossBar timerBar;
    private int Pitimer = 8; // timer * 5 seconds | Event Time 1 minute is 8
    private int timer = 5;
    private BukkitRunnable timerTask;  // Store the running task
    private boolean minigameRunning = false;  // Track if the minigame is running
    // Blacklist for items
    private final List<Material> blacklistedItems = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        currentPlayers.clear();
        getLogger().info("MinigamePlugin enabled");
        listener minigameListener = new listener(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(minigameListener, this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "evvv:minigamechannel", minigameListener);

        // Add blacklisted items here
        blacklistedItems.add(Material.BEDROCK); // Example blacklist
        blacklistedItems.add(Material.BARRIER);
        //blacklistedItems.add(Material.TNT); Example blacklist
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.teleport(new Location(Bukkit.getWorld("world"), 12, 66, 9)); //Lobby Spawn Point
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        World world = Bukkit.getWorld("world");
        pillarLocations = new Location[] {
                new Location(world, -48, 135, 102),
                new Location(world, -62, 135, 108),
                new Location(world, -76, 135, 102),
                new Location(world, -82, 135, 88),
                new Location(world, -76, 135, 74),
                new Location(world, -62, 135, 68),
                new Location(world, -48, 135, 74),
                new Location(world, -42, 135, 88)
        };
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        removePlayerFromGame(player);  // Handle removing the player from the game
    }

    private void removePlayerFromGame(Player player) {
        // Remove the player from the BossBar
        if (timerBar != null) {
            timerBar.removePlayer(player);
        }

        // Remove the player from any active lists
        currentPlayers.remove(player);
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            double finalDamage = event.getFinalDamage();

            // If the player's health after damage would be 0 or less, handle it as a "death"
            if (player.getHealth() - finalDamage <= 0) {
                event.setCancelled(true);  // Cancel the actual death event

                // Handle the player's "death"
                handlePlayerDeath(player);
            }
        }
    }

    private void handlePlayerDeath(Player player) {
        Location deathLocation = player.getLocation();  // Save the player's death location

        // Switch the player to spectator mode and teleport them to their death location
        Bukkit.getScheduler().runTaskLater(this, () -> {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(deathLocation);  // Teleport them to their death location
        }, 1L);  // Schedule it a tick later to make sure no bugs happen

        // Handles any extra player removal functions
        removePlayerFromGame(player);
    }

    public void loadA(String minigame) {
        if (minigame.equalsIgnoreCase("Pillar")) {
            loadAndPasteRegion();
        }
        currentPlayers.addAll(Bukkit.getOnlinePlayers());
        new BukkitRunnable() {
            private int counter = 5;
            @Override
            public void run() {
                Bukkit.broadcastMessage("Minigame Starting in " + counter + " seconds");
                counter--;
                if (counter < 0) {
                    startMinigameEvent(minigame);
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);  // 1 Second
    }

    public void startMinigameEvent(String minigame) {
        getLogger().info("Starting minigame: " + minigame);
        Tminigame = minigame;
        // Notify all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.sendMessage("The minigame has started!");
        }

        switch (minigame) {
            case "Pillar":
                startPillarsOfFortune();
                break;
            case "BHunt":
                // Other minigame logic here
                break;
        }
    }

    private void startPillarsOfFortune() {
        start = true;

        // Create and teleport players to the pillars
        if (timerBar == null) {
            timerBar = Bukkit.createBossBar("Time: 5", BarColor.BLUE, BarStyle.SOLID);
        }
        teleportPlayersToPillars(currentPlayers);
        startTimer(currentPlayers);
    }

    private void startTimer(List<Player> players) {
        if (minigameRunning) {
            return;  // Prevent starting a new timer if the minigame is already running
        }

        minigameRunning = true;
        timer = 5;  // Reset the timer to 5 seconds

        timerBar.setProgress(1.0);  // Set the BossBar to full
        timerBar.setVisible(true);  // Show the BossBar
        timerBar.setTitle("Time: " + timer);

        // Total ticks for the 5-second countdown (100 ticks)
        final int totalTicks = 100;
        timerTask = new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed++;

                // Update the BossBar's progress
                double progress = (double) (totalTicks - ticksElapsed) / totalTicks;
                timerBar.setProgress(progress);

                // Update the title every second
                if (ticksElapsed % 20 == 0) {
                    timerBar.setTitle("Time: " + timer);
                    timer--;
                }

                // When the countdown reaches zero
                if (ticksElapsed >= totalTicks) {
                    Pitimer--;
                    if (Pitimer < 0) {
                        stopMinigame();
                        cancel();
                        return;
                    }
                    giveRandomItems(players);  // Give items to players
                    timer = 5;  // Reset the timer for the next round
                    ticksElapsed = 0;  // Reset elapsed ticks for the next round
                }
            }
        };

        timerTask.runTaskTimer(this, 0L, 1L);  // Run every tick (1L)
    }

    public void stopMinigame() {
        if (timerTask != null) {
            timerTask.cancel();  // Stop the ongoing task
        }

        minigameRunning = false;  // Set the flag to false
        timerBar.setVisible(false);  // Hide the BossBar
        timerBar.removeAll();  // Remove all players from the BossBar
        // Need to add win screen for all players still alive(basic) and also any extra minigame handling such as paying out the players etc
        getLogger().info("Minigame stopped.");
    }

    private void teleportPlayersToPillars(List<Player> players) {
        for (int i = 0; i < players.size(); i++) {
            timerBar.addPlayer(players.get(i));
            players.get(i).teleport(pillarLocations[i]);
        }
    }

    private void giveRandomItems(List<Player> players) {
        for (Player player : players) {
            if (player.isOnline() && player.getGameMode() != GameMode.SPECTATOR) {
                Material randomItem = getRandomItem();
                if (randomItem != null) {
                    player.getInventory().addItem(new ItemStack(randomItem));
                    player.sendMessage("You received a " + randomItem.name() + "!");
                }
            }
        }
    }

    private Material getRandomItem() {
        Material[] materials = Material.values();
        Material randomItem;

        // Loop to ensure a non-blacklisted item is chosen
        do {
            randomItem = materials[random.nextInt(materials.length)];
        } while (blacklistedItems.contains(randomItem) || !randomItem.isItem());

        return randomItem;
    }

    private void loadAndPasteRegion() {
        // Construct the file path for the schematic located in the WorldEdit schematics folder
        File schematicFile = new File(Bukkit.getPluginManager().getPlugin("WorldEdit").getDataFolder(), "schematics/pillarmapG.schem");
        getLogger().info("Schematic file path: " + schematicFile.getAbsolutePath());

        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
        if (format == null) {
            getLogger().severe("Unsupported clipboard format for " + schematicFile.getName());
            return;
        }

        World world = Bukkit.getWorld("world"); // Replace "world" with the correct world name
        if (world == null) {
            getLogger().severe("World 'world' is not loaded or does not exist!");
            return;
        }

        try (ClipboardReader reader = format.getReader(Files.newInputStream(schematicFile.toPath()))) {
            Clipboard clipboard = reader.read();

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                ClipboardHolder holder = new ClipboardHolder(clipboard);
                Operation operation = holder
                        .createPaste(editSession)
                        .to(BlockVector3.at(-42, 144, 113)) // Paste location, modify if needed
                        .ignoreAirBlocks(false) // Paste everything, including air
                        .build();
                Operations.completeLegacy(operation);
                getLogger().info("Pillars of Fortune map pasted.");
            } catch (MaxChangedBlocksException e) {
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            e.printStackTrace();
            getLogger().severe("Failed to paste the Pillars of Fortune map.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        event.setCancelled(!start); // Allow block breaking and interaction when the game starts
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player && event.getDamager() instanceof Player) {
            event.setCancelled(!start); // Allow PvP when the game starts
        }
    }
}
