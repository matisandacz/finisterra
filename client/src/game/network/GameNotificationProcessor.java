package game.network;

import com.artemis.Component;
import com.artemis.E;
import com.artemis.Entity;
import com.artemis.EntityEdit;
import com.artemis.annotations.Wire;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.esotericsoftware.minlog.Log;
import entity.character.info.Inventory;
import game.AOGame;
import game.handlers.SoundsHandler;
import game.managers.WorldManager;
import game.screens.GameScreen;
import game.screens.LobbyScreen;
import game.screens.RoomScreen;
import game.systems.camera.CameraShakeSystem;
import game.ui.AOConsole;
import game.ui.GUI;
import game.utils.WorldUtils;
import shared.network.interfaces.DefaultNotificationProcessor;
import shared.network.inventory.InventoryUpdate;
import shared.network.lobby.JoinRoomNotification;
import shared.network.lobby.NewRoomNotification;
import shared.network.movement.MovementNotification;
import shared.network.notifications.ConsoleMessage;
import shared.network.notifications.EntityUpdate;
import shared.network.notifications.RemoveEntity;
import shared.network.sound.SoundNotification;

import static com.artemis.E.E;

@Wire
public class GameNotificationProcessor extends DefaultNotificationProcessor {

    private WorldManager worldManager;
    private CameraShakeSystem cameraShakeSystem;
    private SoundsHandler soundsHandler;

    @Override
    public void processNotification(EntityUpdate entityUpdate) {
        if (!worldManager.entityExsists(entityUpdate.entityId)) {
            Log.info("Network entity doesn't exist: " + entityUpdate.entityId + ". So we create it");
            Entity newEntity = getWorld().createEntity();
            worldManager.registerEntity(entityUpdate.entityId, newEntity.getId());
            addComponentsToEntity(newEntity, entityUpdate);
            if (E(newEntity).hasFocused()) {
                Log.info("New focused player: " + newEntity.getId());
                GameScreen.setPlayer(newEntity.getId());
            }
        } else {
            Log.info("Network entity exists: " + entityUpdate.entityId + ". Updating");
            updateActions(entityUpdate.entityId, () -> updateEntity(entityUpdate));

        }
    }

    private void updateActions(int id, Runnable update) {
        if (worldManager.hasNetworkedEntity(id)) {
            int networkedEntity = worldManager.getNetworkedEntity(id);
            if (networkedEntity == GameScreen.getPlayer()) {
                E e = E(networkedEntity);
                int preHealth = e.getHealth().min;
                update.run();
                onDamage(networkedEntity, preHealth);
            } else {
                update.run();
            }
        }
    }

    private void onDamage(int id, int preHealth) {
        int postHealth = E(id).getHealth().min;
        if (postHealth < preHealth) {
            Log.info("Shake camera by " + (preHealth - postHealth));
            cameraShakeSystem.shake((preHealth - postHealth) / 10f);
            cameraShakeSystem.push(5, 5);
        }
    }

    @Override
    public void processNotification(RemoveEntity removeEntity) {
        Log.info("Unregistering entity: " + removeEntity.entityId);
        worldManager.unregisterEntity(removeEntity.entityId);
    }

    @Override
    public void processNotification(InventoryUpdate inventoryUpdate) {
        E player = E(GameScreen.getPlayer());
        Inventory inventory = player.getInventory();
        inventoryUpdate.getUpdates().forEach((position, item) -> {
            inventory.set(position, item);
            if (item == null) {
                Log.info("Item removed from position: " + position);
            } else {
                Log.info("Item: " + item.objId + " updated in position: " + position);
                Log.info("Item equipped: " + item.equipped);
            }
        });
        // @todo fix
        AOGame game = (AOGame) Gdx.app.getApplicationListener();
        if (game.getScreen() instanceof GameScreen)
            ((GameScreen) game.getScreen()).getGUI().getInventory().updateUserInventory(0);
    }

    @Override
    public void processNotification(MovementNotification movementNotification) {
        if (worldManager.hasNetworkedEntity(movementNotification.getPlayerId())) {
            int playerId = worldManager.getNetworkedEntity(movementNotification.getPlayerId());
            E(playerId).aOPhysics();
            E(playerId).movementAdd(movementNotification.getDestination());
        }
    }

    private void addComponentsToEntity(Entity newEntity, EntityUpdate entityUpdate) {
        EntityEdit edit = newEntity.edit();
        for (Component component : entityUpdate.components) {
            edit.add(component);
        }
    }

    private void updateEntity(EntityUpdate entityUpdate) {
        int entityId = worldManager.getNetworkedEntity(entityUpdate.entityId);
        Entity entity = world.getEntity(entityId);
        EntityEdit edit = entity.edit();
        for (Component component : entityUpdate.components) {
            // this should replace if already exists
            edit.add(component);
        }
        for (Class remove : entityUpdate.toRemove) {
            edit.remove(remove);
        }
    }

    @Override
    public void processNotification(JoinRoomNotification joinRoomNotification) {
        AOGame game = (AOGame) Gdx.app.getApplicationListener();
        if (game.getScreen() instanceof RoomScreen) {
            RoomScreen room = (RoomScreen) game.getScreen();
            if (joinRoomNotification.isEnter()) {
                room.getRoom().add(joinRoomNotification.getPlayer());
            } else {
                room.getRoom().remove(joinRoomNotification.getPlayer());
            }
            room.updatePlayers();
        }
    }

    @Override
    public void processNotification(NewRoomNotification newRoomNotification) {
        AOGame game = (AOGame) Gdx.app.getApplicationListener();
        if (game.getScreen() instanceof LobbyScreen) {
            final LobbyScreen lobby = (LobbyScreen) game.getScreen();
            lobby.roomCreated(newRoomNotification.getRoom());
        }
    }

    @Override
    public void processNotification(ConsoleMessage consoleMessage) {
        AOGame game = (AOGame) Gdx.app.getApplicationListener();
        if (game.getScreen() instanceof GameScreen) {
            final GUI gui = ((GameScreen) game.getScreen()).getGUI();
            final AOConsole console = gui.getConsole();
            final String message = consoleMessage.getMessage();
            switch (consoleMessage.getKind()) {
                case INFO:
                    console.addInfo(message);
                    break;
                case ERROR:
                    console.addError(message);
                    break;
                case COMBAT:
                    console.addCombat(message);
                    break;
                case WARNING:
                    console.addWarning(message);
                    break;
                default:
                    console.addInfo(message);
                    break;
            }
        }
    }

    @Override
    public void processNotification(SoundNotification soundNotification) {
        int soundNumber = soundNotification.getSoundNumber();
        soundsHandler.playSound(soundNumber);
    }
}
