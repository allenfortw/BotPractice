package allenme.top.botpractice;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BotPractice extends JavaPlugin {

    private static BotPractice instance;
    private BotManager botManager;
    private GameManager gameManager;

    @Override
    public void onEnable() {
        instance = this;

        if (!checkCitizens()) {
            getLogger().severe("Citizens 插件未找到！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 延遲初始化
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                this.gameManager = new GameManager();
                this.botManager = new BotManager();

                PluginManager pm = getServer().getPluginManager();
                pm.registerEvents(gameManager, this);
                pm.registerEvents(botManager, this);

                getLogger().info("BotPractice 插件初始化完成！");
                getLogger().info("使用原版 Minecraft 戰鬥系統 - 無插件干預");
                getLogger().info("最小化 AI - 只負責導航，戰鬥交給原版");
            } catch (Exception e) {
                getLogger().severe("初始化錯誤: " + e.getMessage());
                e.printStackTrace();
            }
        }, 20L);

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        getLogger().info("BotPractice 插件已啟用！");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.removeAllBots();
        }
        getLogger().info("BotPractice 插件已禁用！");
    }

    private boolean checkCitizens() {
        return getServer().getPluginManager().getPlugin("Citizens") != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此指令只能由玩家執行！");
            return true;
        }

        Player player = (Player) sender;

        if (botManager == null || gameManager == null) {
            player.sendMessage("§c插件正在初始化中，請稍後再試...");
            return true;
        }

        try {
            if (command.getName().equalsIgnoreCase("spawnbot")) {
                if (args.length != 1) {
                    player.sendMessage("§c用法: /spawnbot <競技場名稱>");
                    return true;
                }

                botManager.spawnBot(player, args[0]);
                return true;
            }

            if (command.getName().equalsIgnoreCase("removebot")) {
                botManager.removeBot(player);
                player.sendMessage("§a機器人已移除！");
                return true;
            }

            if (command.getName().equalsIgnoreCase("setarena")) {
                if (args.length != 1) {
                    player.sendMessage("§c用法: /setarena <競技場名稱>");
                    return true;
                }

                botManager.setArenaPosition(player, args[0]);
                return true;
            }

            if (command.getName().equalsIgnoreCase("startgame")) {
                gameManager.setGameState(player, GameManager.GameState.IN_GAME);
                player.sendMessage("§a遊戲已開始！");
                player.sendMessage("§7機器人使用原版戰鬥系統");
                return true;
            }

            if (command.getName().equalsIgnoreCase("stopgame")) {
                gameManager.setGameState(player, GameManager.GameState.WAITING);
                player.sendMessage("§c遊戲已停止！");
                return true;
            }
        } catch (Exception e) {
            player.sendMessage("§c執行指令時發生錯誤: " + e.getMessage());
            getLogger().severe("指令執行錯誤: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public static BotPractice getInstance() {
        return instance;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public BotManager getBotManager() {
        return botManager;
    }
}