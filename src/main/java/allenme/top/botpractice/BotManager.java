package allenme.top.botpractice;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.Goal;
import net.citizensnpcs.api.ai.GoalSelector;
import net.citizensnpcs.nms.v1_8_R3.util.NMSImpl;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BotManager implements Listener {

    private final NPCRegistry registry;
    private final Map<Player, NPC> playerBots;
    private final Map<Player, BukkitTask> botTasks;
    private final NMSImpl nmsImpl;

    // Bot攻擊玩家的冷卻時間記錄
    private final Map<NPC, Long> lastBotAttackTimes = new HashMap<>();

    // 玩家攻擊Bot的冷卻時間記錄 (使用UUID避免Player物件引用問題)
    private final Map<UUID, Long> lastPlayerAttackTimes = new HashMap<>();

    // 攻擊冷卻時間常數 (600毫秒)
    private static final long ATTACK_COOLDOWN_MS = 600L;

    public BotManager() {
        this.registry = CitizensAPI.getNPCRegistry();
        this.playerBots = new HashMap<>();
        this.botTasks = new HashMap<>();
        this.nmsImpl = new NMSImpl();
    }

    /**
     * 玩家離開時清理 Bot
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeBot(event.getPlayer());
        // 清理玩家攻擊時間記錄
        lastPlayerAttackTimes.remove(event.getPlayer().getUniqueId());
    }

    /**
     * 處理玩家攻擊Bot的事件 - 實施攻擊冷卻保護
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerAttackBot(EntityDamageByEntityEvent event) {
        // 檢查是否為玩家攻擊
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();

        // 檢查被攻擊的實體是否為該玩家的Bot
        if (!isPlayerBot(player, event.getEntity())) {
            return;
        }

        // 檢查玩家攻擊冷卻
        long currentTime = System.currentTimeMillis();
        UUID playerUUID = player.getUniqueId();
        Long lastAttack = lastPlayerAttackTimes.get(playerUUID);

        if (lastAttack != null && currentTime - lastAttack < ATTACK_COOLDOWN_MS) {
            // 攻擊在冷卻期內，取消攻擊事件
            event.setCancelled(true);

            // 播放冷卻音效提示
            player.playSound(player.getLocation(), Sound.CLICK, 0.5f, 2.0f);

            // 可選：發送冷卻訊息 (註解掉避免訊息spam)
            // long remainingCooldown = ATTACK_COOLDOWN_MS - (currentTime - lastAttack);
            // player.sendMessage("§c攻擊冷卻中... " + remainingCooldown + "ms");

            return;
        }

        // 記錄攻擊時間
        lastPlayerAttackTimes.put(playerUUID, currentTime);

        // 播放攻擊成功音效
        player.playSound(player.getLocation(), Sound.SUCCESSFUL_HIT, 1.0f, 1.0f);
    }

    /**
     * 檢查被攻擊的實體是否為指定玩家的Bot
     */
    private boolean isPlayerBot(Player player, org.bukkit.entity.Entity entity) {
        NPC playerBot = playerBots.get(player);
        return playerBot != null && playerBot.getEntity() != null && playerBot.getEntity().equals(entity);
    }

    public void spawnBot(Player player, String arenaName) {
        // 如果玩家已經有機器人，先移除
        if (playerBots.containsKey(player)) {
            removeBot(player);
        }

        File configFile = new File(BotPractice.getInstance().getDataFolder(), arenaName + ".yml");
        FileConfiguration arenaConfig;

        // 配置文件處理邏輯（與原代碼相同）
        if (!configFile.exists()) {
            arenaConfig = YamlConfiguration.loadConfiguration(configFile);
            Location playerLoc = player.getLocation();

            arenaConfig.set("Pos2.X", playerLoc.getX());
            arenaConfig.set("Pos2.Y", playerLoc.getY());
            arenaConfig.set("Pos2.Z", playerLoc.getZ());
            arenaConfig.set("Pos2.Yaw", playerLoc.getYaw());
            arenaConfig.set("Pos2.Pitch", playerLoc.getPitch());
            arenaConfig.set("Pos2.World", playerLoc.getWorld().getName());

            try {
                arenaConfig.save(configFile);
                player.sendMessage("§a已創建競技場配置文件: " + arenaName + ".yml");
            } catch (IOException e) {
                player.sendMessage("§c保存配置文件時發生錯誤: " + e.getMessage());
                return;
            }
        } else {
            arenaConfig = YamlConfiguration.loadConfiguration(configFile);

            if (!arenaConfig.contains("Pos2")) {
                Location playerLoc = player.getLocation();
                arenaConfig.set("Pos2.X", playerLoc.getX());
                arenaConfig.set("Pos2.Y", playerLoc.getY());
                arenaConfig.set("Pos2.Z", playerLoc.getZ());
                arenaConfig.set("Pos2.Yaw", playerLoc.getYaw());
                arenaConfig.set("Pos2.Pitch", playerLoc.getPitch());
                arenaConfig.set("Pos2.World", playerLoc.getWorld().getName());

                try {
                    arenaConfig.save(configFile);
                    player.sendMessage("§a已更新配置文件");
                } catch (IOException e) {
                    player.sendMessage("§c保存配置文件時發生錯誤: " + e.getMessage());
                    return;
                }
            }
        }

        if (arenaConfig != null) {
            String worldName = arenaConfig.getString("Pos2.World", arenaName);
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                world = player.getWorld();
                player.sendMessage("§e警告: 找不到世界 '" + worldName + "'，使用玩家當前世界");
            }

            double x = arenaConfig.getDouble("Pos2.X");
            double y = arenaConfig.getDouble("Pos2.Y");
            double z = arenaConfig.getDouble("Pos2.Z");
            float yaw = (float) arenaConfig.getDouble("Pos2.Yaw");
            float pitch = (float) arenaConfig.getDouble("Pos2.Pitch");
            Location botSpawnLocation = new Location(world, x, y, z, yaw, pitch);

            // 延遲生成 NPC
            Bukkit.getScheduler().runTask(BotPractice.getInstance(), () -> {
                try {
                    // 創建 EntityType.PLAYER 類型的 NPC
                    NPC npc = registry.createNPC(EntityType.PLAYER, BotName.getBotName());

                    if (npc == null) {
                        player.sendMessage("§c無法創建機器人");
                        return;
                    }

                    boolean spawned = npc.spawn(botSpawnLocation);
                    if (!spawned) {
                        player.sendMessage("§c機器人生成失敗");
                        npc.destroy();
                        return;
                    }

                    // 核心設置 - 使用Citizens API
                    npc.setProtected(false);        // 允許受到傷害
                    npc.setUseMinecraftAI(true);    // 啟用Citizens AI

                    // 延遲設置屬性
                    Bukkit.getScheduler().runTaskLater(BotPractice.getInstance(), () -> {
                        try {
                            if (npc.getEntity() != null) {
                                // 設置生命值
                                if (npc.getEntity() instanceof LivingEntity) {
                                    LivingEntity botEntity = (LivingEntity) npc.getEntity();
                                    botEntity.setMaxHealth(20.0);
                                    botEntity.setHealth(20.0);
                                }

                                // 裝備武器
                                ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
                                sword.addEnchantment(Enchantment.DAMAGE_ALL, 5);
                                npc.getTrait(Equipment.class).set(Equipment.EquipmentSlot.HAND, sword);

                                // 為玩家裝備
                                equipPlayerForBattle(player);

                                // 保存引用
                                playerBots.put(player, npc);

                                // 開始Citizens AI戰鬥系統
                                startCitizensAI(player, npc);

                                player.sendMessage("§a機器人已生成 - 使用Citizens戰鬥AI");
                                player.sendMessage("§7位置: " + String.format("%.1f, %.1f, %.1f", x, y, z));
                                player.sendMessage("§e完整AI戰鬥系統 - 包含追蹤和攻擊");
                                player.sendMessage("§7雙向攻擊冷卻保護: " + ATTACK_COOLDOWN_MS + "ms");
                            } else {
                                player.sendMessage("§c機器人實體生成失敗");
                                npc.destroy();
                            }
                        } catch (Exception e) {
                            player.sendMessage("§c設置機器人屬性時發生錯誤: " + e.getMessage());
                            if (npc.isSpawned()) {
                                npc.destroy();
                            }
                        }
                    }, 10L); // 延遲 10 tick 確保完全初始化

                } catch (Exception e) {
                    player.sendMessage("§c生成機器人時發生錯誤: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }
    }

    /**
     * Citizens AI戰鬥系統 - 包含追蹤和攻擊
     */
    private void startCitizensAI(Player player, NPC npc) {
        GameManager.GameState state = BotPractice.getInstance().getGameManager().getGameState(player);

        if (state == GameManager.GameState.IN_GAME) {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(BotPractice.getInstance(), () -> {
                try {
                    if (npc.isSpawned() && player.isOnline() && npc.getEntity() != null) {
                        Navigator navigator = npc.getNavigator();

                        double distance = npc.getEntity().getLocation().distance(player.getLocation());

                        if (distance > 15.0) {
                            // 距離太遠，取消所有行為
                            navigator.cancelNavigation();
                        } else if (distance > 3.0) {
                            // 中距離：追蹤玩家
                            if (!navigator.isNavigating()) {
                                navigator.setTarget(player, false);
                            }
                        } else {
                            // 近距離：停止移動並攻擊
                            navigator.cancelNavigation();
                            performCitizensAttack(npc, player);
                        }
                    }
                } catch (Exception e) {
                    // 忽略錯誤，保持穩定運行
                }
            }, 20L, 10L); // 每0.5秒更新一次

            botTasks.put(player, task);
        }
    }

    /**
     * 使用Citizens NMS API執行攻擊 - 包含Bot攻擊冷卻保護
     */
    private void performCitizensAttack(NPC npc, Player target) {
        if (npc.getEntity() instanceof LivingEntity) {
            try {
                LivingEntity botEntity = (LivingEntity) npc.getEntity();

                // 檢查Bot攻擊冷卻
                long currentTime = System.currentTimeMillis();
                Long lastAttack = lastBotAttackTimes.get(npc);

                if (lastAttack == null || currentTime - lastAttack >= ATTACK_COOLDOWN_MS) {
                    // 使用Citizens NMS API執行攻擊
                    nmsImpl.attack(botEntity, target);

                    lastBotAttackTimes.put(npc, currentTime);

                    // 播放攻擊音效
                    target.getWorld().playSound(target.getLocation(),
                            Sound.SUCCESSFUL_HIT, 1.0f, 1.0f);

                    // 面向目標
                    Location targetLoc = target.getLocation();
                    Location botLoc = botEntity.getLocation();

                    double dx = targetLoc.getX() - botLoc.getX();
                    double dz = targetLoc.getZ() - botLoc.getZ();
                    float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

                    botLoc.setYaw(yaw);
                    botEntity.teleport(botLoc);
                }
            } catch (Exception e) {
                // 如果NMS攻擊失敗，降級到基礎傷害
                target.damage(8.0, npc.getEntity());
            }
        }
    }

    /**
     * 為玩家裝備戰鬥裝備
     */
    private void equipPlayerForBattle(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();

        player.setMaxHealth(20.0);
        player.setHealth(20.0);

        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.DAMAGE_ALL, 5);
        inventory.setItemInHand(sword);

        ItemStack food = new ItemStack(Material.COOKED_BEEF, 64);
        inventory.addItem(food);

        player.updateInventory();
        player.sendMessage("§7已裝備: 鋒利 V 鑽石劍");
    }

    public void setArenaPosition(Player player, String arenaName) {
        File configFile = new File(BotPractice.getInstance().getDataFolder(), arenaName + ".yml");
        FileConfiguration arenaConfig = YamlConfiguration.loadConfiguration(configFile);

        Location playerLoc = player.getLocation();

        arenaConfig.set("Pos2.X", playerLoc.getX());
        arenaConfig.set("Pos2.Y", playerLoc.getY());
        arenaConfig.set("Pos2.Z", playerLoc.getZ());
        arenaConfig.set("Pos2.Yaw", playerLoc.getYaw());
        arenaConfig.set("Pos2.Pitch", playerLoc.getPitch());
        arenaConfig.set("Pos2.World", playerLoc.getWorld().getName());

        try {
            arenaConfig.save(configFile);
            player.sendMessage("§a已設置競技場 '" + arenaName + "' 的位置");
        } catch (IOException e) {
            player.sendMessage("§c保存配置文件時發生錯誤: " + e.getMessage());
        }
    }

    public void removeBot(Player player) {
        // 取消任務
        if (botTasks.containsKey(player)) {
            botTasks.get(player).cancel();
            botTasks.remove(player);
        }

        // 清理Bot攻擊時間記錄
        if (playerBots.containsKey(player)) {
            NPC npc = playerBots.get(player);
            lastBotAttackTimes.remove(npc);
        }

        // 清理玩家攻擊時間記錄
        lastPlayerAttackTimes.remove(player.getUniqueId());

        // 移除 NPC
        if (playerBots.containsKey(player)) {
            NPC npc = playerBots.get(player);
            try {
                if (npc.isSpawned()) {
                    npc.despawn();
                }
                npc.destroy();
            } catch (Exception e) {
                BotPractice.getInstance().getLogger().warning("移除機器人時發生錯誤: " + e.getMessage());
            }
            playerBots.remove(player);
        }
    }

    public void removeAllBots() {
        for (Player player : new HashMap<>(playerBots).keySet()) {
            removeBot(player);
        }
        lastBotAttackTimes.clear();
        lastPlayerAttackTimes.clear();
    }

    public NPC getBot(Player player) {
        return playerBots.get(player);
    }

    public boolean hasBot(Player player) {
        return playerBots.containsKey(player);
    }

    public void updateBotBehavior(Player player) {
        if (!playerBots.containsKey(player)) {
            return;
        }

        NPC npc = playerBots.get(player);
        if (npc == null || !npc.isSpawned()) {
            return;
        }

        GameManager.GameState state = BotPractice.getInstance().getGameManager().getGameState(player);

        // 取消現有任務
        if (botTasks.containsKey(player)) {
            botTasks.get(player).cancel();
            botTasks.remove(player);
        }

        if (state == GameManager.GameState.IN_GAME) {
            startCitizensAI(player, npc);
        } else {
            try {
                npc.getNavigator().cancelNavigation();
            } catch (Exception e) {
                // 忽略錯誤
            }
        }
    }

    /**
     * 獲取攻擊冷卻時間（毫秒）
     */
    public static long getAttackCooldownMs() {
        return ATTACK_COOLDOWN_MS;
    }

    /**
     * 檢查玩家是否在攻擊冷卻期內
     */
    public boolean isPlayerOnAttackCooldown(Player player) {
        Long lastAttack = lastPlayerAttackTimes.get(player.getUniqueId());
        if (lastAttack == null) return false;

        return System.currentTimeMillis() - lastAttack < ATTACK_COOLDOWN_MS;
    }

    /**
     * 檢查Bot是否在攻擊冷卻期內
     */
    public boolean isBotOnAttackCooldown(Player player) {
        NPC npc = playerBots.get(player);
        if (npc == null) return false;

        Long lastAttack = lastBotAttackTimes.get(npc);
        if (lastAttack == null) return false;

        return System.currentTimeMillis() - lastAttack < ATTACK_COOLDOWN_MS;
    }
}