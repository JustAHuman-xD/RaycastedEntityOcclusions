package games.cubi.raycastedentityocclusion.manager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import games.cubi.raycastedentityocclusion.engine.Engine;
import games.cubi.raycastedentityocclusion.util.EntityOctree;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

public class CommandsManager {
    private final RaycastedEntityOcclusion plugin;
    private final ConfigManager cfg;

    public CommandsManager(RaycastedEntityOcclusion plugin, ConfigManager cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public LiteralCommandNode<CommandSourceStack> registerCommand() {
        //run help command if no context provided
        LiteralCommandNode<CommandSourceStack> buildCommand = Commands.literal("raycastedentityocclusions")
                .requires(sender -> sender.getSender().hasPermission("raycastedentityocclusions.command"))
                .executes(context -> {
                    helpCommand(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("help")
                    .executes(context -> helpCommand(context)))
                .then(Commands.literal("reload")
                    .executes(context -> {
                    cfg.load();
                    context.getSource().getSender().sendMessage("[EntityOcclusions] Config reloaded.");
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("dump-octree")
                        .executes(context -> {
                            EntityOctree octree = Engine.getOctree();
                            if (octree != null) {
                                context.getSource().getSender().sendMessage("octree[max_depth=" + octree.maxDepth() + "] with " + octree.getEntities() + " entities[lit=" + octree.getLit() + "] in " + octree.getChunks() + " loaded chunks. (More will be added as chunks are loaded)");
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("repair-meg")
                        .executes(context -> {
                            EntityOctree octree = Engine.getOctree();
                            int repaired = 0;
                            if (octree != null) {
                                octree.repairMegEntities();
                                repaired = octree.getRepairedMeg();
                            }
                            context.getSource().getSender().sendMessage("Repaired ModelEngine render radius for " + repaired + " entities in the octree.");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("config-values")
                    .executes(context -> {
                        CommandSender sender = context.getSource().getSender();
                        //dynamic config values
                        sender.sendMessage("[EntityOcclusions] Config values: ");

                        ConfigurationSection root = cfg.cfg.getConfigurationSection("");
                        for (String path : root.getKeys(true)) {
                            Object val = cfg.cfg.get(path);

                            sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>" + path + "<gray> = <white>" + val));
                        }
                        return Command.SINGLE_SUCCESS;
                    }))
                .then(Commands.literal("set")
                        .executes(context -> {
                            CommandSender sender = context.getSource().getSender();
                            sender.sendRichMessage("<red>Usage: /raycastedentityocclusions set <key> <value>");;
                            return 0;
                        })
                        .then(Commands.argument("key", StringArgumentType.string())
                                .then(Commands.argument("value", StringArgumentType.string())
                                        .executes(context -> {
                                            CommandSender sender = context.getSource().getSender();
                                            String key = StringArgumentType.getString(context, "key");
                                            String value = StringArgumentType.getString(context, "value");

                                            int result = cfg.setConfigValue(key, value);
                                            if (result == -1) {
                                                sender.sendRichMessage("<red>Invalid inputs");
                                            } else if (result == 0) {
                                                //Integer value out of bounds 0 - 256
                                                sender.sendRichMessage("<red>Invalid value for <white>" + key + "<red>, must be between 0 and 256");
                                            }
                                            else {
                                                sender.sendRichMessage("<white>Set <green>" + key + "<white> to <green>" + value);
                                            }
                                            return 0;
                                        })
                                )
                        )
                )
                .build();
        return buildCommand;
    }

    public int helpCommand(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        sender.sendRichMessage("<white>RaycastedEntityOcclusions <yellow>v" + plugin.getDescription().getVersion());
        sender.sendRichMessage("<white>Commands:");
        sender.sendRichMessage("<green>/raycastedentityocclusions reload <gray>- Reloads the config");
        sender.sendRichMessage("<green>/raycastedentityocclusions config-values <gray>- Shows all config values");
        sender.sendRichMessage("<green>/raycastedentityocclusions set <key> <value> <gray>- Sets a config value");
        return Command.SINGLE_SUCCESS;
    }
}
