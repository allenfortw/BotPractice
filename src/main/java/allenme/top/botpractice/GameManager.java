package allenme.top.botpractice;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;

public class GameManager implements Listener {

    public enum GameState {
        WAITING,
        IN_GAME,
        FINISHED
    }

    private final Map<Player, GameState> playerStates;

    public GameManager() {
        this.playerStates = new HashMap<>();
    }

    public GameState getGameState(Player player) {
        return playerStates.getOrDefault(player, GameState.WAITING);
    }

    public void setGameState(Player player, GameState state) {
        playerStates.put(player, state);

        // 更新機器人行為
        if (BotPractice.getInstance().getBotManager() != null) {
            try {
                BotPractice.getInstance().getBotManager().updateBotBehavior(player);
            } catch (Exception e) {
                BotPractice.getInstance().getLogger().warning("更新機器人行為時發生錯誤: " + e.getMessage());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 清理玩家狀態
        playerStates.remove(player);

        // 移除玩家的機器人
        if (BotPractice.getInstance().getBotManager() != null) {
            try {
                BotPractice.getInstance().getBotManager().removeBot(player);
            } catch (Exception e) {
                BotPractice.getInstance().getLogger().warning("玩家離開時移除機器人發生錯誤: " + e.getMessage());
            }
        }
    }
}