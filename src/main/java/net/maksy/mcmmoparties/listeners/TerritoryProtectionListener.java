package net.maksy.mcmmoparties.listeners;

import net.maksy.mcmmoparties.McMMOParties;
import net.maksy.mcmmoparties.configuration.enums.TerritoryPermission;
import net.maksy.mcmmoparties.territory.TerritoryClaim;
import net.maksy.mcmmoparties.territory.TerritoryService;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TerritoryProtectionListener implements Listener {
    private final Map<UUID, Long> lastDenialMessage = new HashMap<>();

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        check(event.getPlayer(), event.getBlock(), TerritoryPermission.BREAK, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        check(event.getPlayer(), event.getBlockPlaced(), TerritoryPermission.BUILD, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        TerritoryPermission permission;
        if (block.getState() instanceof Container) {
            permission = TerritoryPermission.CONTAINER;
        } else if (isRedstoneInteraction(block.getType())) {
            permission = TerritoryPermission.REDSTONE;
        } else {
            permission = TerritoryPermission.INTERACT;
        }
        check(event.getPlayer(), block, permission, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        check(event.getPlayer(), event.getRightClicked().getLocation(), TerritoryPermission.ENTITY, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        check(event.getPlayer(), event.getRightClicked().getLocation(), TerritoryPermission.ENTITY, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player) {
            return;
        }
        Player player = resolvePlayer(event.getDamager());
        if (player != null) {
            check(player, event.getEntity().getLocation(), TerritoryPermission.ENTITY, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        check(event.getPlayer(), event.getBlockClicked().getRelative(event.getBlockFace()), TerritoryPermission.BUILD, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        check(event.getPlayer(), event.getBlockClicked(), TerritoryPermission.BREAK, event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (event.getPlayer() != null) {
            check(event.getPlayer(), event.getEntity().getLocation(), TerritoryPermission.BUILD, event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player player = resolvePlayer(event.getRemover());
        if (player != null) {
            check(player, event.getEntity().getLocation(), TerritoryPermission.BREAK, event);
        } else if (claimAt(event.getEntity().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Player player = resolvePlayer(event.getAttacker());
        if (player != null) {
            check(player, event.getVehicle().getLocation(), TerritoryPermission.ENTITY, event);
        } else if (claimAt(event.getVehicle().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            check(event.getPlayer(), event.getBlock(), TerritoryPermission.BUILD, event);
        } else if (claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (claimAt(event.getBlock().getLocation()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!enabled() || !McMMOParties.getConfigManager().shouldProtectTerritoryExplosions()) {
            return;
        }
        event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!enabled() || !McMMOParties.getConfigManager().shouldProtectTerritoryExplosions()) {
            return;
        }
        event.blockList().removeIf(block -> claimAt(block.getLocation()) != null);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!enabled() || !McMMOParties.getConfigManager().shouldProtectTerritoryPistons()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (!sameTerritory(block, block.getRelative(event.getDirection()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!enabled() || !McMMOParties.getConfigManager().shouldProtectTerritoryPistons()) {
            return;
        }
        for (Block block : event.getBlocks()) {
            if (!sameTerritory(block, block.getRelative(event.getDirection()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent event) {
        if (!enabled()) {
            return;
        }
        if (!sameTerritory(event.getBlock(), event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!enabled() || event.getSource().getLocation() == null || event.getDestination().getLocation() == null) {
            return;
        }
        TerritoryClaim source = claimAt(event.getSource().getLocation());
        TerritoryClaim destination = claimAt(event.getDestination().getLocation());
        String sourceParty = source == null ? null : source.partyId();
        String destinationParty = destination == null ? null : destination.partyId();
        if (!Objects.equals(sourceParty, destinationParty)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameChunk(event.getFrom(), event.getTo())) {
            return;
        }
        if (McMMOParties.getTerritoryService().cancelClaimPreview(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(net.maksy.mcmmoparties.configuration.configs.LanguageConfig.get().getMessage(
                    "territory_preview_moved", "&7Your territory preview was cancelled because you changed chunks."
            ));
        }
    }

    private boolean sameChunk(org.bukkit.Location first, org.bukkit.Location second) {
        return first.getWorld() != null && second.getWorld() != null
                && first.getWorld().getUID().equals(second.getWorld().getUID())
                && first.getChunk().getX() == second.getChunk().getX()
                && first.getChunk().getZ() == second.getChunk().getZ();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        McMMOParties.getTerritoryService().cancelPreview(event.getPlayer().getUniqueId());
    }

    private void check(Player player, Block block, TerritoryPermission permission, Event event) {
        check(player, block.getLocation(), permission, event);
    }

    private void check(Player player, org.bukkit.Location location, TerritoryPermission permission, Event event) {
        if (!enabled()) {
            return;
        }
        TerritoryService.PermissionDecision decision = McMMOParties.getTerritoryService()
                .checkPermission(player, location, permission, event);
        if (decision.allowed()) {
            return;
        }
        if (event instanceof Cancellable cancellable) {
            cancellable.setCancelled(true);
        }
        sendDenial(player, decision.denialMessage());
    }

    private void sendDenial(Player player, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastDenialMessage.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < McMMOParties.getConfigManager().getTerritoryMessageCooldownMillis()) {
            return;
        }
        lastDenialMessage.put(player.getUniqueId(), now);
        player.sendMessage(message);
    }

    private boolean sameTerritory(Block first, Block second) {
        TerritoryClaim firstClaim = claimAt(first.getLocation());
        TerritoryClaim secondClaim = claimAt(second.getLocation());
        String firstParty = firstClaim == null ? null : firstClaim.partyId();
        String secondParty = secondClaim == null ? null : secondClaim.partyId();
        return Objects.equals(firstParty, secondParty);
    }

    private TerritoryClaim claimAt(org.bukkit.Location location) {
        if (!enabled()) {
            return null;
        }
        return McMMOParties.getTerritoryService().getClaim(location);
    }

    private boolean enabled() {
        return McMMOParties.getTerritoryService() != null
                && McMMOParties.getConfigManager().isTerritoryProtectionEnabled();
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    private boolean isRedstoneInteraction(Material material) {
        return Tag.BUTTONS.isTagged(material)
                || Tag.DOORS.isTagged(material)
                || Tag.TRAPDOORS.isTagged(material)
                || material.name().endsWith("_PRESSURE_PLATE")
                || material == Material.LEVER
                || material == Material.REPEATER
                || material == Material.COMPARATOR;
    }
}
