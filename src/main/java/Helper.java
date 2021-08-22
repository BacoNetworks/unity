import de.randombyte.unity.config.Config;
import io.github.nucleuspowered.nucleus.api.NucleusAPI;
import io.github.nucleuspowered.nucleus.api.exceptions.PluginAlreadyRegisteredException;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.service.pagination.PaginationList;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;

import java.text.SimpleDateFormat;
import java.util.*;

public class Helper {
    public static HashMap<UUID, Config.Unity> MarriedMap = new HashMap<>();
    public static Text MarriedPrefix;
    private static SimpleDateFormat dateOutputFormat = new SimpleDateFormat("dd.MM.yyyy");
    public static boolean isKissingEnabled = true;
    public static PluginContainer container = null;

    public static void FillHashMap(List<Config.Unity> unities){
        for(Config.Unity unity : unities){
            MarriedMap.put(unity.getMember1(), unity);
            MarriedMap.put(unity.getMember2(), unity);
        }
    }

    public static void IsKissingEnabled(Config config){
        isKissingEnabled = config.getKissingEnabled();
    }

    public static void SendList(CommandSource source, Text title){
            List<Text> texts = new ArrayList<>();
            for(Config.Unity unity : MarriedMap.values()){
                String username1 = "Unknown";
                String username2 = "Unknown";
                User user1 = getUserFromUUID(unity.getMember1());
                if(user1 != null){
                    username1 = user1.getName();
                }
                User user2 = getUserFromUUID(unity.getMember1());
                if(user2 != null){
                    username2 = user2.getName();
                }
                Text hoverText = Text.of(TextColors.GOLD, "Married on: " , TextColors.WHITE, dateOutputFormat.format(unity.getDate()));
                Text text = Text.builder()
                        .append(Text.builder()
                                .append(Text.of(TextColors.AQUA, "- ", TextColors.WHITE, username1, TextColors.AQUA, " is married to ", TextColors.WHITE, username2))
                                .onHover(TextActions.showText(hoverText))
                                .build()).build();
                texts.add(text);
            }
            PaginationList.builder()
                    .title(title)
                    .contents(texts)
                    .sendTo(source);
    }

    public static void RegisterNucleusToken() {
        if (container != null) {
            PluginContainer pluginContainer = container;
            try {
                NucleusAPI.getMessageTokenService().register(pluginContainer, (tokenInput, source, variables) -> {
                    if (source instanceof Player) {
                        if (tokenInput.equalsIgnoreCase("marry")) {
                            final Player player = (Player) source;
                            final UUID playerUUID = player.getUniqueId();
                            Config.Unity unity = MarriedMap.get(playerUUID);
                            if (unity == null) {
                                return Optional.empty();
                            }
                            UUID marriedTo = null;
                            if (!unity.getMember1().equals(playerUUID)) {
                                marriedTo = unity.getMember1();
                            } else if (!unity.getMember2().equals(playerUUID)) {
                                marriedTo = unity.getMember2();
                            }
                            if (marriedTo == null) {
                                return Optional.empty();
                            }
                            User marriedToUser = getUserFromUUID(marriedTo);
                            if (marriedToUser == null) {
                                return Optional.empty();
                            }

                            Text hoverText = Text.of("Married to ", TextColors.GOLD, marriedToUser.getName(), TextColors.WHITE, ", " + dateOutputFormat.format(unity.getDate()));
                            Text returnText = Text.builder()
                                    .append(Text.builder()
                                            .append(MarriedPrefix)
                                            .onHover(TextActions.showText(hoverText))
                                            .build()).build();
                            return Optional.of(returnText);
                        }
                    }
                    return Optional.empty();
                });
            } catch (PluginAlreadyRegisteredException ignore) {
            }
        }
    }

    private static User getUserFromUUID(UUID uuid) {
        Optional<UserStorageService> userStorage = Sponge.getServiceManager().provide(UserStorageService.class);
        return userStorage.get().get(uuid).orElse(null);
    }
}
