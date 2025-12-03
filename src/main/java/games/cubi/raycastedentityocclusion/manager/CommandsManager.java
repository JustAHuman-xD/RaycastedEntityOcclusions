package games.cubi.raycastedentityocclusion.manager;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import games.cubi.raycastedentityocclusion.RaycastedEntityOcclusion;
import games.cubi.raycastedentityocclusion.engine.Engine;
import games.cubi.raycastedentityocclusion.util.EntityNode;
import games.cubi.raycastedentityocclusion.util.EntityOctree;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
                            CommandSender sender = context.getSource().getSender();
                            sender.sendRichMessage("<red>Usage: /raycastedentityocclusions repair-meg <verbose>");
                            return 0;
                        })
                        .then(Commands.argument("verbose", BoolArgumentType.bool())
                                .executes(context -> {
                                    boolean verbose = BoolArgumentType.getBool(context, "verbose");
                                    EntityOctree octree = Engine.getOctree();
                                    Set<UUID> savedFixed = new HashSet<>();
                                    Set<UUID> radiusFixed = new HashSet<>();
                                    Set<UUID> lockFixed = new HashSet<>();
                                    Set<UUID> rotationFixed = new HashSet<>();
                                    Set<UUID> mythicDataFixed = new HashSet<>();
                                    Set<UUID> positionFixed = new HashSet<>();
                                    long now = System.currentTimeMillis();
                                    if (octree != null) {
                                        List<EntityNode> nodes = octree.getAllNodes();
                                        for (EntityNode node : nodes) {
                                            boolean[] repairs = node.repairMeg(cfg);
                                            if (repairs[0]) savedFixed.add(node.uuid());
                                            if (repairs[1]) radiusFixed.add(node.uuid());
                                            if (repairs[2]) lockFixed.add(node.uuid());
                                            if (repairs[3]) rotationFixed.add(node.uuid());
                                            if (repairs[4]) mythicDataFixed.add(node.uuid());
                                            if (repairs[5]) positionFixed.add(node.uuid());
                                        }
                                    }
                                    long duration = System.currentTimeMillis() - now;
                                    context.getSource().getSender().sendMessage("Attempted repair for npc entities in the octree in " + duration + "ms");
                                    if (!savedFixed.isEmpty()) {
                                        context.getSource().getSender().sendMessage(" - " + savedFixed.size() + " entities had 'shouldBeSaved' fixed.");
                                        if (verbose) {
                                            for (UUID uuid : savedFixed) {
                                                context.getSource().getSender().sendMessage(Component.text("    - " + uuid.toString())
                                                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                                                        .clickEvent(ClickEvent.copyToClipboard(uuid.toString())));
                                            }
                                        }
                                    }
                                    if (!radiusFixed.isEmpty()) {
                                        context.getSource().getSender().sendMessage(" - " + radiusFixed.size() + " entities had 'renderRadius' fixed.");
                                        if (verbose) {
                                            for (UUID uuid : radiusFixed) {
                                                context.getSource().getSender().sendMessage(Component.text("    - " + uuid.toString())
                                                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                                                        .clickEvent(ClickEvent.copyToClipboard(uuid.toString())));
                                            }
                                        }
                                    }
                                    if (!lockFixed.isEmpty()) {
                                        context.getSource().getSender().sendMessage(" - " + lockFixed.size() + " entities had 'rotationLocked' fixed.");
                                        if (verbose) {
                                            for (UUID uuid : lockFixed) {
                                                context.getSource().getSender().sendMessage(Component.text("    - " + uuid.toString())
                                                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                                                        .clickEvent(ClickEvent.copyToClipboard(uuid.toString())));
                                            }
                                        }
                                    }
                                    if (!rotationFixed.isEmpty()) {
                                        context.getSource().getSender().sendMessage(" - " + rotationFixed.size() + " entities had 'bodyRotation' fixed.");
                                        if (verbose) {
                                            for (UUID uuid : rotationFixed) {
                                                context.getSource().getSender().sendMessage(Component.text("    - " + uuid.toString())
                                                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                                                        .clickEvent(ClickEvent.copyToClipboard(uuid.toString())));
                                            }
                                        }
                                    }
                                    if (!positionFixed.isEmpty()) {
                                        context.getSource().getSender().sendMessage(" - " + positionFixed.size() + " entities had 'position' fixed.");
                                        if (verbose) {
                                            for (UUID uuid : positionFixed) {
                                                context.getSource().getSender().sendMessage(Component.text("    - " + uuid.toString())
                                                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                                                        .clickEvent(ClickEvent.copyToClipboard(uuid.toString())));
                                            }
                                        }
                                    }
                                    if (!mythicDataFixed.isEmpty()) {
                                        context.getSource().getSender().sendMessage(" - " + mythicDataFixed.size() + " entities had MythicMob data reloaded.");
                                        if (verbose) {
                                            for (UUID uuid : mythicDataFixed) {
                                                context.getSource().getSender().sendMessage(Component.text("    - " + uuid.toString())
                                                        .hoverEvent(HoverEvent.showText(Component.text("Click to copy UUID")))
                                                        .clickEvent(ClickEvent.copyToClipboard(uuid.toString())));
                                            }
                                        }
                                    }
                                    return Command.SINGLE_SUCCESS;
                                })))
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
                            sender.sendRichMessage("<red>Usage: /raycastedentityocclusions set <key> <value>");
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
