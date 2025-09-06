package com.danish.inactivekiller;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class InactiveKillerPlugin extends JavaPlugin implements Listener {

    private static final String LOG_PREFIX = "§8[§bInactive§3Killer§8] §7"; // Colored prefix for nice visuals
    private final AtomicLong lastActiveAtEpochMs = new AtomicLong(System.currentTimeMillis());
    private int taskId = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        this.lastActiveAtEpochMs.set(System.currentTimeMillis());
        startMonitorTask();
        logBanner("enabled");
    }

    @Override
    public void onDisable() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        logBanner("disabled");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.lastActiveAtEpochMs.set(System.currentTimeMillis());
        log("Player joined: §a" + event.getPlayer().getName() + "§7. Inactivity timer reset.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.lastActiveAtEpochMs.set(System.currentTimeMillis());
        log("Player left: §c" + event.getPlayer().getName() + "§7. Inactivity timer reset.");
    }

    private void startMonitorTask() {
        final FileConfiguration cfg = getConfig();
        long inactivitySeconds = cfg.getLong("inactivity-seconds", 60L);
        long checkIntervalTicks = Math.max(20L, cfg.getLong("check-interval-ticks", 20L)); // default 1s

        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            try {
                Server server = getServer();
                int online = server.getOnlinePlayers().size();
                if (online > 0) {
                    // If anyone is online, keep resetting timer to now
                    lastActiveAtEpochMs.set(System.currentTimeMillis());
                    return;
                }

                long now = System.currentTimeMillis();
                long idleMs = now - lastActiveAtEpochMs.get();
                long thresholdMs = Duration.ofSeconds(inactivitySeconds).toMillis();

                long remainingMs = Math.max(0, thresholdMs - idleMs);
                if (remainingMs <= 0) {
                    announceAndStop();
                } else if (remainingMs <= 15_000) {
                    // Visual countdown for last 15 seconds
                    long secs = Math.max(1, (long) Math.ceil(remainingMs / 1000.0));
                    actionBar("§cNo players online. Stopping in §e" + secs + "s§c...");
                }
            } catch (Throwable t) {
                getLogger().warning("Monitor task error: " + t.getMessage());
            }
        }, 20L, checkIntervalTicks);

        log("Monitoring started. Inactivity threshold: §e" + inactivitySeconds + "s§7, interval: §e" + checkIntervalTicks + " ticks§7.");
    }

    private void announceAndStop() {
        broadcast("§cNo players online for the configured time. §7Stopping server...");
        // Use a short delay to let the message flush to console
        Bukkit.getScheduler().runTaskLater(this, () -> Bukkit.shutdown(), 20L);
        // Prevent repeated triggers
        lastActiveAtEpochMs.set(System.currentTimeMillis());
    }

    private void broadcast(String message) {
        Bukkit.getConsoleSender().sendMessage(LOG_PREFIX + message);
    }

    private void log(String message) {
        Bukkit.getConsoleSender().sendMessage(LOG_PREFIX + message);
    }

    private void logBanner(String state) {
        String line = "§8==============================================";
        Bukkit.getConsoleSender().sendMessage(line);
        Bukkit.getConsoleSender().sendMessage(LOG_PREFIX + "§bInactive §3Killer §7by §aDanish §7" + state + ".");
        Bukkit.getConsoleSender().sendMessage(LOG_PREFIX + "§7Source: §fcom.danish.inactivekiller");
        Bukkit.getConsoleSender().sendMessage(line);
    }

    private void actionBar(String message) {
        // Fallback: send to console. Action bar to players isn't applicable since there are no players.
        // We keep this method for potential future use when broadcasting to any idle console viewers.
        Bukkit.getConsoleSender().sendMessage(LOG_PREFIX + message);
    }
}


