import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerLoginEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.block.Block;

public class Main {
    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();
        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();
        instanceContainer.setGenerator(unit ->
                unit.modifier().fillHeight(0, 1, Block.STONE));
        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();

        globalEventHandler.addListener(PlayerLoginEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 2, 0));
        });

        globalEventHandler.addListener(PlayerMoveEvent.class, event -> {
            final Player player = event.getPlayer();

            final Pos old = player.getPosition();
            final Pos newer = event.getNewPosition();

            player.sendMessage("YAW diff: " + (newer.yaw() - old.yaw()));
            player.sendMessage("Pitch diff: " + (newer.pitch() - old.pitch()));
        });

        globalEventHandler.addListener(PlayerBlockBreakEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setCancelled(true);
        });

        // Start the server on port 25565
        minecraftServer.start("0.0.0.0", 25565);
    }
}