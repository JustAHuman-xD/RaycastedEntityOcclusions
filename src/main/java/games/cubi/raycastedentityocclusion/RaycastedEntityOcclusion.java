package games.cubi.raycastedentityocclusion;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import games.cubi.raycastedentityocclusion.engine.Engine;
import games.cubi.raycastedentityocclusion.listener.CacheListener;
import games.cubi.raycastedentityocclusion.manager.ChunkSnapshotManager;
import games.cubi.raycastedentityocclusion.manager.CommandsManager;
import games.cubi.raycastedentityocclusion.manager.ConfigManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class RaycastedEntityOcclusion extends JavaPlugin implements CommandExecutor {
    private ConfigManager cfg;
    private ChunkSnapshotManager snapMgr;
    private CommandsManager commands;

    public int tick = 0;

    @Override
    public void onEnable() {
        cfg = new ConfigManager(this);
        snapMgr = new ChunkSnapshotManager(this);
        commands = new CommandsManager(this, cfg);
        getServer().getPluginManager().registerEvents(new CacheListener(snapMgr), this);

        //Brigadier API
        LiteralCommandNode<CommandSourceStack> buildCommand = commands.registerCommand();

        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, commands -> {
            commands.registrar().register(buildCommand);
            //alias "reo"
            commands.registrar().register(Commands.literal("reo")
                    .requires(sender -> sender.getSender().hasPermission("raycastedentityocclusions.command"))
                    .executes(context -> {
                        new CommandsManager(this, cfg).helpCommand(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .redirect(buildCommand).build());
        });

        new BukkitRunnable() {
            @Override
            public void run() {
                if (tick % cfg.engineRate == 0) {
                    Engine.runEngine(cfg, snapMgr, RaycastedEntityOcclusion.this);
                }
                tick++;
            }
        }.runTaskTimerAsynchronously(this, 1L, 1);
    }

    public ConfigManager getConfigManager() {
        return cfg;
    }
}