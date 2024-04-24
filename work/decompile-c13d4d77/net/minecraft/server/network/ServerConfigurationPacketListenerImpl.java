package net.minecraft.server.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.annotation.Nullable;
import net.minecraft.core.IRegistryCustom;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.RegistrySynchronization;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.PlayerConnectionUtils;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundUpdateTagsPacket;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.configuration.ClientboundRegistryDataPacket;
import net.minecraft.network.protocol.configuration.ClientboundUpdateEnabledFeaturesPacket;
import net.minecraft.network.protocol.configuration.ServerConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ServerboundFinishConfigurationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.network.config.JoinWorldTask;
import net.minecraft.server.network.config.ServerResourcePackConfigurationTask;
import net.minecraft.server.players.PlayerList;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.thread.IAsyncTaskHandler;
import net.minecraft.world.flag.FeatureFlags;
import org.slf4j.Logger;

public class ServerConfigurationPacketListenerImpl extends ServerCommonPacketListenerImpl implements TickablePacketListener, ServerConfigurationPacketListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final IChatBaseComponent DISCONNECT_REASON_INVALID_DATA = IChatBaseComponent.translatable("multiplayer.disconnect.invalid_player_data");
    private final GameProfile gameProfile;
    private final Queue<ConfigurationTask> configurationTasks = new ConcurrentLinkedQueue();
    @Nullable
    private ConfigurationTask currentTask;
    private ClientInformation clientInformation;

    public ServerConfigurationPacketListenerImpl(MinecraftServer minecraftserver, NetworkManager networkmanager, CommonListenerCookie commonlistenercookie) {
        super(minecraftserver, networkmanager, commonlistenercookie);
        this.gameProfile = commonlistenercookie.gameProfile();
        this.clientInformation = commonlistenercookie.clientInformation();
    }

    @Override
    protected GameProfile playerProfile() {
        return this.gameProfile;
    }

    @Override
    public void onDisconnect(IChatBaseComponent ichatbasecomponent) {
        ServerConfigurationPacketListenerImpl.LOGGER.info("{} lost connection: {}", this.gameProfile, ichatbasecomponent.getString());
        super.onDisconnect(ichatbasecomponent);
    }

    @Override
    public boolean isAcceptingMessages() {
        return this.connection.isConnected();
    }

    public void startConfiguration() {
        this.send(new ClientboundCustomPayloadPacket(new BrandPayload(this.server.getServerModName())));
        LayeredRegistryAccess<RegistryLayer> layeredregistryaccess = this.server.registries();

        this.send(new ClientboundUpdateEnabledFeaturesPacket(FeatureFlags.REGISTRY.toNames(this.server.getWorldData().enabledFeatures())));
        this.send(new ClientboundRegistryDataPacket((new IRegistryCustom.c(RegistrySynchronization.networkedRegistries(layeredregistryaccess))).freeze()));
        this.send(new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(layeredregistryaccess)));
        this.addOptionalTasks();
        this.configurationTasks.add(new JoinWorldTask());
        this.startNextTask();
    }

    public void returnToWorld() {
        this.configurationTasks.add(new JoinWorldTask());
        this.startNextTask();
    }

    private void addOptionalTasks() {
        this.server.getServerResourcePack().ifPresent((minecraftserver_serverresourcepackinfo) -> {
            this.configurationTasks.add(new ServerResourcePackConfigurationTask(minecraftserver_serverresourcepackinfo));
        });
    }

    @Override
    public void handleClientInformation(ServerboundClientInformationPacket serverboundclientinformationpacket) {
        this.clientInformation = serverboundclientinformationpacket.information();
    }

    @Override
    public void handleResourcePackResponse(ServerboundResourcePackPacket serverboundresourcepackpacket) {
        super.handleResourcePackResponse(serverboundresourcepackpacket);
        if (serverboundresourcepackpacket.action().isTerminal()) {
            this.finishCurrentTask(ServerResourcePackConfigurationTask.TYPE);
        }

    }

    @Override
    public void handleConfigurationFinished(ServerboundFinishConfigurationPacket serverboundfinishconfigurationpacket) {
        this.connection.suspendInboundAfterProtocolChange();
        PlayerConnectionUtils.ensureRunningOnSameThread(serverboundfinishconfigurationpacket, this, (IAsyncTaskHandler) this.server);
        this.finishCurrentTask(JoinWorldTask.TYPE);

        try {
            PlayerList playerlist = this.server.getPlayerList();

            if (playerlist.getPlayer(this.gameProfile.getId()) != null) {
                this.disconnect(PlayerList.DUPLICATE_LOGIN_DISCONNECT_MESSAGE);
                return;
            }

            IChatBaseComponent ichatbasecomponent = playerlist.canPlayerLogin(this.connection.getRemoteAddress(), this.gameProfile);

            if (ichatbasecomponent != null) {
                this.disconnect(ichatbasecomponent);
                return;
            }

            EntityPlayer entityplayer = playerlist.getPlayerForLogin(this.gameProfile, this.clientInformation);

            playerlist.placeNewPlayer(this.connection, entityplayer, this.createCookie(this.clientInformation));
            this.connection.resumeInboundAfterProtocolChange();
        } catch (Exception exception) {
            ServerConfigurationPacketListenerImpl.LOGGER.error("Couldn't place player in world", exception);
            this.connection.send(new ClientboundDisconnectPacket(ServerConfigurationPacketListenerImpl.DISCONNECT_REASON_INVALID_DATA));
            this.connection.disconnect(ServerConfigurationPacketListenerImpl.DISCONNECT_REASON_INVALID_DATA);
        }

    }

    @Override
    public void tick() {
        this.keepConnectionAlive();
    }

    private void startNextTask() {
        if (this.currentTask != null) {
            throw new IllegalStateException("Task " + this.currentTask.type().id() + " has not finished yet");
        } else if (this.isAcceptingMessages()) {
            ConfigurationTask configurationtask = (ConfigurationTask) this.configurationTasks.poll();

            if (configurationtask != null) {
                this.currentTask = configurationtask;
                configurationtask.start(this::send);
            }

        }
    }

    private void finishCurrentTask(ConfigurationTask.a configurationtask_a) {
        ConfigurationTask.a configurationtask_a1 = this.currentTask != null ? this.currentTask.type() : null;

        if (!configurationtask_a.equals(configurationtask_a1)) {
            throw new IllegalStateException("Unexpected request for task finish, current task: " + configurationtask_a1 + ", requested: " + configurationtask_a);
        } else {
            this.currentTask = null;
            this.startNextTask();
        }
    }
}
