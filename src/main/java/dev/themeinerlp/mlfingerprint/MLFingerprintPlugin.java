package dev.themeinerlp.mlfingerprint;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.netty.buffer.ByteBufHelper;
import com.google.gson.Gson;
import com.google.inject.Inject;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.themeinerlp.mlfingerprint.config.MLConfiguration;
import dev.themeinerlp.mlfingerprint.config.RabbitMQ;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(id = "MLFingerprint", name = "ML Fingerprint", version = "999.0.0",
        url = "https://themeinerlp.dev", description = "A simple packet capture plugin", authors = {"TheMeinerLP"},
dependencies = {
        @Dependency(id = "PacketEvents")
})
public class MLFingerprintPlugin implements PacketListener {

    private final ProxyServer server;
    private final Logger logger;

    private final ConcurrentHashMap<UUID, ClientState> state = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    private Connection rabbitConn;
    private Channel rabbitChannel;
    private final Path dataDirectory;
    private String exchange;


    @Inject
    public MLFingerprintPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent e) {
        logger.info("MLFingerprint is initializing...");
        if (Files.notExists(dataDirectory)) {
            logger.info("Data directory does not exist, creating it at {}", dataDirectory);
            try {
                Files.createDirectories(dataDirectory);
            } catch (Exception ex) {
                logger.error("Failed to create data directory", ex);
                return;
            }
        }
        Path file = dataDirectory.resolve("config.conf");
        final HoconConfigurationLoader loader = HoconConfigurationLoader.builder()
                .defaultOptions(opts -> opts.shouldCopyDefaults(true))
                .path(file)
                .build();
        try {
            CommentedConfigurationNode load = loader.load();
            MLConfiguration mlConfiguration = load.get(MLConfiguration.class);
            RabbitMQ rabbitMQConfig = mlConfiguration.getRabbitMQ();
            ConnectionFactory factory = new ConnectionFactory();
            factory.setPort(rabbitMQConfig.getPort());
            factory.setVirtualHost(rabbitMQConfig.getVhost());
            factory.setHost(rabbitMQConfig.getHost());
            factory.setUsername(rabbitMQConfig.getUsername());
            factory.setPassword(rabbitMQConfig.getPassword());
            rabbitConn = factory.newConnection();
            rabbitChannel = rabbitConn.createChannel();
            logger.info("Connected to RabbitMQ at {}:{}", rabbitMQConfig.getHost(), rabbitMQConfig.getPort());
            rabbitChannel.exchangeDeclare(rabbitMQConfig.getExchange(), rabbitMQConfig.getType(), true);
            logger.info("Exchange '{}' declared with type '{}'", rabbitMQConfig.getExchange(), rabbitMQConfig.getType());
            this.exchange = rabbitMQConfig.getExchange();
        } catch (ConfigurateException ex) {
            logger.error("Failed to load configuration file", ex);
            return;
        } catch (Exception ex) {
            logger.error("Failed to connect to RabbitMQ", ex);
            return;
        }
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.HIGHEST);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent e) {
        try {
            if (rabbitChannel != null) rabbitChannel.close();
            if (rabbitConn != null) rabbitConn.close();
        } catch (Exception ignored) {
            logger.error("Failed to close RabbitMQ connection", ignored);
        } finally {
            logger.info("MLFingerprint has been shut down.");
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent evt) {
        handlePacket(evt.getUser().getUUID(), evt, Direction.INCOMING);

    }

    @Override
    public void onPacketSend(PacketSendEvent evt) {
        handlePacket(evt.getUser().getUUID(), evt, Direction.OUTGOING);

    }

    private void handlePacket(UUID clientId, ProtocolPacketEvent packet, Direction dir) {
        long now = System.nanoTime();
        ClientState st = state.computeIfAbsent(clientId, id -> new ClientState());
        long iat = (st.lastTimestamp > 0) ? now - st.lastTimestamp : 0;
        st.lastTimestamp = now;

        int packetId = packet.getPacketId();
        int length = ByteBufHelper.readableBytes(packet.getByteBuf());
        byte[] raw = ByteBufHelper.array(packet.getByteBuf());
        double entropy = FeatureUtils.calcEntropy(raw);

        FeatureVec vec = new FeatureVec(clientId.toString(), packetId, length, dir, iat, entropy, now);
        sendToRabbit(vec);
    }

    private void sendToRabbit(FeatureVec vec) {
        try {
            if (rabbitChannel == null || !rabbitChannel.isOpen()) {
                logger.error("RabbitMQ channel is not open, cannot send packet data");
                return;
            }
            if (exchange == null || exchange.isEmpty()) {
                logger.error("Exchange is not set, cannot send packet data");
                return;
            }
            String payload = gson.toJson(vec);
            rabbitChannel.basicPublish(this.exchange, "", null, payload.getBytes("UTF-8"));
        } catch (Exception ex) {
            logger.error("Failed to send packet data to RabbitMQ", ex);
        }
    }
}
