package com.cosmic.guard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatGuardPlugin extends JavaPlugin implements Listener {
    private static final long COMBAT_MILLIS = 20_000L;
    private static final MiniMessage MINI = MiniMessage.miniMessage();

    private final Map<UUID, Long> combatUntil = new ConcurrentHashMap<>();
    private final Map<UUID, String> gradients = new ConcurrentHashMap<>();
    private final Map<UUID, String> selectedGradient = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();
    private final LinkedHashMap<String, Gradient> palette = new LinkedHashMap<>();

    @Override
    public void onEnable() {
        palette.put("Water", new Gradient("#FFFFFF", "#00BFFF"));
        palette.put("Galaxy", new Gradient("#7B2CFF", "#FF4FD8"));
        palette.put("Cherry Blossom", new Gradient("#FF9ECF", "#FFF0F5"));
        palette.put("Vampire", new Gradient("#2B0000", "#FF0000"));
        palette.put("Christmas", new Gradient("#00A651", "#FF3030"));
        palette.put("Volcano", new Gradient("#FF4500", "#FFD700"));
        palette.put("Lava", new Gradient("#FF5A00", "#FFF000"));
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) lastLocations.put(player.getUniqueId(), player.getLocation().clone());
        getLogger().info("CombatGuardChatColors enabled.");
    }

    @Override
    public void onDisable() {
        combatUntil.clear();
        lastLocations.clear();
    }

    private boolean inCombat(Player player) {
        Long until = combatUntil.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void tag(Player player) {
        combatUntil.put(player.getUniqueId(), System.currentTimeMillis() + COMBAT_MILLIS);
        player.sendActionBar(Component.text("⚔ COMBAT TAGGED — 20 seconds", NamedTextColor.RED));
    }

    private long remainingSeconds(Player player) {
        Long until = combatUntil.get(player.getUniqueId());
        return until == null ? 0 : Math.max(0, (until - System.currentTimeMillis() + 999) / 1000);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player victim = event.getEntity() instanceof Player p ? p : null;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player p) attacker = p;
        if (victim != null && attacker != null && !victim.equals(attacker)) {
            tag(victim);
            tag(attacker);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (inCombat(player)) {
            Location dropAt = player.getLocation();
            for (ItemStack stack : player.getInventory().getStorageContents()) drop(dropAt, stack);
            for (ItemStack stack : player.getInventory().getArmorContents()) drop(dropAt, stack);
            drop(dropAt, player.getInventory().getItemInOffHand());
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            player.setHealth(0.0);
            combatUntil.remove(player.getUniqueId());
        }
        lastLocations.remove(player.getUniqueId());
    }

    private void drop(Location at, ItemStack stack) {
        if (stack != null && !stack.getType().isAir()) at.getWorld().dropItemNaturally(at, stack.clone());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!inCombat(player)) return;
        event.setCancelled(true);
        player.sendMessage(Component.text("You cannot use commands while in combat! (" +
                remainingSeconds(player) + "s)", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !inCombat(player)) return;
        if (isRestrictedInventory(event.getInventory().getType())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("You cannot access storage while in combat!", NamedTextColor.RED));
        }
    }

    private boolean isRestrictedInventory(InventoryType type) {
        return switch (type) {
            case CHEST, ENDER_CHEST, BARREL, SHULKER_BOX, HOPPER, DISPENSER, DROPPER,
                    FURNACE, BLAST_FURNACE, SMOKER, BREWING, CRAFTER -> true;
            default -> false;
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        event.setCancelled(true);
        event.getPlayer().sendMessage(Component.text("Teleportation is disabled on this server.", NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl && pearl.getShooter() instanceof Player player) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Ender pearls are disabled.", NamedTextColor.RED));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.ENDER_PEARL) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Ender pearls are disabled.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getWorld() == event.getTo().getWorld()
                && (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ())) {
            lastLocations.put(event.getPlayer().getUniqueId(), event.getTo().clone());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String gradientName = gradients.get(event.getPlayer().getUniqueId());
        if (gradientName == null) return;
        Gradient gradient = palette.get(gradientName);
        if (gradient == null) return;
        String safeName = PlainTextComponentSerializer.plainText().serialize(event.getPlayer().displayName());
        Component coloredName = MINI.deserialize("<gradient:" + gradient.start + ":" + gradient.end + ">"
                + escapeMini(safeName) + "</gradient>");
        Component message = coloredName.append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(event.getMessage()));
        event.getRecipients().forEach(viewer -> viewer.sendMessage(message));
        event.setCancelled(true);
    }

    private String escapeMini(String input) {
        return input.replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used in-game.");
            return true;
        }
        if (!player.isOp() && !player.hasPermission("combatguard.chatcolors")) {
            player.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return true;
        }
        if (command.getName().equalsIgnoreCase("chatcolors")) {
            openGradientGui(player);
            return true;
        }
        if (!command.getName().equalsIgnoreCase("chatcolor")) return false;
        if (args.length == 0) {
            player.sendMessage(Component.text("Usage: /chatcolor <set|remove> <player> [gradient]", NamedTextColor.YELLOW));
            return true;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /chatcolor set <player> [gradient]", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
                return true;
            }
            String choice = args.length >= 3 ? normalizeGradient(String.join(" ", Arrays.copyOfRange(args, 2, args.length)))
                    : selectedGradient.get(player.getUniqueId());
            if (choice == null || !palette.containsKey(choice)) {
                player.sendMessage(Component.text("Choose a gradient in /chatcolors first, or specify its name.", NamedTextColor.RED));
                return true;
            }
            gradients.put(target.getUniqueId(), choice);
            player.sendMessage(Component.text("Applied " + choice + " gradient to " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text("Your chat name gradient is now " + choice + ".", NamedTextColor.GREEN));
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /chatcolor remove <player>", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
                return true;
            }
            gradients.remove(target.getUniqueId());
            player.sendMessage(Component.text("Removed the chat gradient from " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }
        player.sendMessage(Component.text("Usage: /chatcolor <set|remove> <player> [gradient]", NamedTextColor.YELLOW));
        return true;
    }

    private String normalizeGradient(String input) {
        for (String name : palette.keySet()) if (name.equalsIgnoreCase(input.replace("_", " "))) return name;
        return null;
    }

    private void openGradientGui(Player player) {
        Inventory inventory = Bukkit.createInventory(new GradientHolder(), 27, Component.text("Chat Colors", NamedTextColor.DARK_PURPLE));
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int i = 0;
        for (Map.Entry<String, Gradient> entry : palette.entrySet()) {
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            Gradient g = entry.getValue();
            meta.displayName(MINI.deserialize("<gradient:" + g.start + ":" + g.end + ">" + entry.getKey() + " Gradient</gradient>"));
            meta.lore(List.of(Component.text("Click to select this gradient", NamedTextColor.GRAY),
                    Component.text(g.start + " → " + g.end, NamedTextColor.DARK_GRAY)));
            item.setItemMeta(meta);
            inventory.setItem(slots[i++], item);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GradientHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getItemMeta().displayName() == null) return;
        String display = PlainTextComponentSerializer.plainText().serialize(clicked.getItemMeta().displayName());
        String selected = display.replace(" Gradient", "");
        if (!palette.containsKey(selected)) return;
        selectedGradient.put(player.getUniqueId(), selected);
        player.sendMessage(Component.text("Selected " + selected + " gradient. Use /chatcolor set <player> to apply it.", NamedTextColor.GREEN));
        player.closeInventory();
    }

    private static final class GradientHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record Gradient(String start, String end) {}
}
