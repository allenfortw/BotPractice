package allenme.top.botpractice;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class BotName {

    private static final List<String> BOT_NAMES = Arrays.asList(
            "PracticeBot",
            "TrainingBot",
            "SparringBot",
            "CombatBot",
            "DuelBot",
            "FightBot",
            "WarriorBot",
            "ChampionBot",
            "GuardianBot",
            "DefenderBot"
    );

    private static final Random random = new Random();

    public static String getBotName() {
        return BOT_NAMES.get(random.nextInt(BOT_NAMES.size()));
    }
}