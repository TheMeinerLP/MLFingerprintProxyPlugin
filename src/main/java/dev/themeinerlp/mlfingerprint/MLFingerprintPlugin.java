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
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.themeinerlp.mlfingerprint.config.MLConfiguration;
import dev.themeinerlp.mlfingerprint.config.RabbitMQ;
import dev.themeinerlp.mlfingerprint.config.RabbitMQResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Plugin(id = "ml_fingerprint", name = "ML Fingerprint", version = "999.0.0",
        url = "https://themeinerlp.dev", description = "A simple packet capture plugin", authors = {"TheMeinerLP"},
dependencies = {
        @Dependency(id = "packetevents")
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
    private String routingKey;
    
    // Configuration options
    private int evaluationIntervalMinutes;
    private int displayIntervalSeconds;
    private double accuracyThreshold;
    private int packetQueueThreshold;
    private int asyncQueueProcessingInterval;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    // Scheduler for periodic tasks
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> displayTask;
    private ScheduledFuture<?> queueProcessorTask;
    
    // Packet queue for async processing
    private final BlockingQueue<FeatureVec> packetQueue = new LinkedBlockingQueue<>();


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
            if (Files.notExists(file)) {
                loader.save(load);
            }
            MLConfiguration mlConfiguration = load.get(MLConfiguration.class);
            RabbitMQ rabbitMQConfig = mlConfiguration.getRabbitMQ();
            RabbitMQResult rabbitMQResultConfig = mlConfiguration.getRabbitMQResult();
            
            // Load configuration options
            this.evaluationIntervalMinutes = mlConfiguration.getEvaluationIntervalMinutes();
            this.displayIntervalSeconds = mlConfiguration.getDisplayIntervalSeconds();
            this.accuracyThreshold = mlConfiguration.getAccuracyThreshold();
            this.packetQueueThreshold = mlConfiguration.getPacketQueueThreshold();
            this.asyncQueueProcessingInterval = mlConfiguration.getAsyncQueueProcessingInterval();
            logger.info("Evaluation interval: {} minutes, Display interval: {} seconds, Accuracy threshold: {}%, Packet queue threshold: {}, Async queue processing interval: {} ms", 
                    this.evaluationIntervalMinutes, this.displayIntervalSeconds, this.accuracyThreshold, this.packetQueueThreshold, this.asyncQueueProcessingInterval);
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

            // Declare queue for packet data
            rabbitChannel.queueDeclare(rabbitMQConfig.getQueue(), true, false, false, null);
            rabbitChannel.queueBind(rabbitMQConfig.getQueue(), rabbitMQConfig.getExchange(), rabbitMQConfig.getRoutingKey());
            logger.info("Queue '{}' declared and bound to exchange '{}'", rabbitMQConfig.getQueue(), rabbitMQConfig.getExchange());
            
            // Declare queue for client percentage messages
            rabbitChannel.queueDeclare(rabbitMQResultConfig.getQueue(), true, false, false, null);
            rabbitChannel.queueBind(rabbitMQResultConfig.getQueue(), rabbitMQConfig.getExchange(), rabbitMQResultConfig.getRoutingKey());
            rabbitChannel.basicConsume(rabbitMQResultConfig.getQueue(), true, "proxy", new DefaultConsumer(rabbitChannel) {
                @Override
                public void handleDelivery(final String consumerTag, final Envelope envelope, final AMQP.BasicProperties properties, final byte[] body) throws IOException {
                    String message = new String(body, StandardCharsets.UTF_8);
                    logger.debug("Received message from RabbitMQ: {}", message);
                    // Parse the message to extract client ID and type
                    try {
                        ClientPercentageMessage clientPercentageMessage = gson.fromJson(message, ClientPercentageMessage.class);
                        if (clientPercentageMessage == null) {
                            logger.error("Failed to parse message: {}", message);
                            return;
                        }
                        UUID clientId = UUID.fromString(clientPercentageMessage.getClientId());
                        String clientType = clientPercentageMessage.getClient();
                        double percentage = Math.floor(clientPercentageMessage.getPercentage() * 100) / 100.0;
                        if (clientType == null || percentage < 0) {
                            logger.error("Invalid client percentage message: {}", message);
                            return;
                        }
                        
                        // Get or create client state
                        ClientState clientState = state.computeIfAbsent(clientId, id -> new ClientState());
                        
                        // Check if the accuracy threshold has already been met
                        if (clientState.thresholdMet) {
                            // Skip evaluation if threshold has been met
                            logger.debug("Skipping evaluation for client {} (accuracy threshold of {}% has been met with {}%)",
                                    clientId, accuracyThreshold, clientState.lastPercentage);
                            return;
                        }
                        
                        // Check if enough time has passed since the last evaluation
                        long currentTime = System.currentTimeMillis();
                        long evaluationIntervalMillis = TimeUnit.MINUTES.toMillis(evaluationIntervalMinutes);
                        
                        if (clientState.lastEvaluationTime == 0 || 
                            currentTime - clientState.lastEvaluationTime >= evaluationIntervalMillis) {
                            // Time to re-evaluate
                            clientState.lastEvaluationTime = currentTime;
                            clientState.lastClientType = clientType;
                            clientState.lastPercentage = percentage;
                            
                            // Check if the accuracy threshold has been met
                            if (percentage >= accuracyThreshold) {
                                clientState.thresholdMet = true;
                                logger.info("Accuracy threshold of {}% met for client {} with {}%. No further evaluations will occur.",
                                        accuracyThreshold, clientId, percentage);
                            }
                            
                            logger.info("Re-evaluated client {} as {} with {}% accuracy", 
                                    clientId, clientType, percentage);
                            
                            // The scheduler will handle displaying the information to the player
                        } else {
                            // Skip evaluation, log for debugging
                            logger.debug("Skipping evaluation for client {} (last evaluation was {} ms ago, interval is {} ms)",
                                    clientId, currentTime - clientState.lastEvaluationTime, evaluationIntervalMillis);
                        }
                    } catch (Exception ex) {
                        logger.error("Failed to parse RabbitMQ message", ex);
                    }
                }
            });
            logger.info("Queue '{}' declared and bound to exchange '{}'", rabbitMQResultConfig.getQueue(), rabbitMQConfig.getExchange());
            
            logger.info("Exchange '{}' declared with type '{}'", rabbitMQConfig.getExchange(), rabbitMQConfig.getType());
            this.exchange = rabbitMQConfig.getExchange();
            this.routingKey = rabbitMQConfig.getRoutingKey();
        } catch (ConfigurateException ex) {
            logger.error("Failed to load configuration file", ex);
            return;
        } catch (Exception ex) {
            logger.error("Failed to connect to RabbitMQ", ex);
            return;
        }
        
        // Initialize scheduler for periodic tasks
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.displayTask = this.scheduler.scheduleAtFixedRate(
                this::displayClientInformation,
                displayIntervalSeconds, // initial delay
                displayIntervalSeconds, // period
                TimeUnit.SECONDS
        );
        logger.info("Scheduled client information display task every {} seconds", displayIntervalSeconds);
        
        // Initialize async packet queue processor
        this.queueProcessorTask = this.scheduler.scheduleAtFixedRate(
                this::processPacketQueue,
                asyncQueueProcessingInterval, // initial delay
                asyncQueueProcessingInterval, // period
                TimeUnit.MILLISECONDS
        );
        logger.info("Scheduled async packet queue processor task every {} milliseconds", asyncQueueProcessingInterval);
        
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.MONITOR);
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent e) {
        try {
            // Process any remaining packets in the queue
            logger.info("Processing remaining {} packets in the queue before shutdown", packetQueue.size());
            processPacketQueue();
            
            // Shutdown scheduler
            if (queueProcessorTask != null) {
                queueProcessorTask.cancel(false);
                logger.info("Cancelled async packet queue processor task");
            }
            if (displayTask != null) {
                displayTask.cancel(false);
                logger.info("Cancelled client information display task");
            }
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                    logger.info("Scheduler shut down successfully");
                } catch (InterruptedException ex) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                    logger.error("Scheduler shutdown interrupted", ex);
                }
            }
            
            // Close RabbitMQ connections
            if (rabbitChannel != null) rabbitChannel.close();
            if (rabbitConn != null) rabbitConn.close();
        } catch (Exception ignored) {
            logger.error("Failed to close RabbitMQ connection", ignored);
        } finally {
            logger.info("MLFingerprint has been shut down.");
        }
    }

    @Subscribe
    public void onPlayerQuit(DisconnectEvent event) {
        UUID clientId = event.getPlayer().getUniqueId();
        if (clientId != null) {
            state.remove(clientId);
            logger.info("Removed client state for disconnected player: {}", clientId);
        }
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent evt) {
        PacketReceiveEvent clone = evt.clone();
        server.getScheduler().buildTask(this, () -> {
            // Handle the packet in a separate thread to avoid blocking the main thread
            handlePacket(clone.getUser().getUUID(), clone, Direction.INCOMING);
        }).schedule();
    }

    @Override
    public void onPacketSend(PacketSendEvent evt) {
        PacketSendEvent clone = evt.clone();
        server.getScheduler().buildTask(this, () -> {
            // Handle the packet in a separate thread to avoid blocking the main thread
            handlePacket(clone.getUser().getUUID(), clone, Direction.OUTGOING);
        }).schedule();
    }

    private void handlePacket(UUID clientId, ProtocolPacketEvent packet, Direction dir) {
        if (clientId == null) {
            return;
        }
        if (packet.getPacketId() < 0) {
            logger.warn("Received packet with invalid ID: {}", packet.getPacketId());
            return;
        }
        if (state == null) {
            logger.warn("State map is null, cannot process packet for client {}", clientId);
            return;
        }
        ClientState st = state.computeIfAbsent(clientId, id -> new ClientState());
        long now = System.nanoTime();
        long iat = (st.lastTimestamp > 0) ? now - st.lastTimestamp : 0; // TODO: Use monotonic time as base
        st.lastTimestamp = now;

        if (iat < 0) {
            logger.warn("Negative inter-arrival time detected for client {}: {}", clientId, iat);
            iat = 0; // Reset to zero to avoid negative values
        }
        int protocolVersion = packet.getUser().getClientVersion().getProtocolVersion();
        int packetId = packet.getPacketId();
        int length = ByteBufHelper.readableBytes(packet.getByteBuf());
        byte[] raw = ByteBufHelper.array(packet.getByteBuf());
        double entropy = FeatureUtils.calcEntropy(raw);

        FeatureVec vec = FeatureVec.builder()
                .clientId(clientId.toString())
                .clientProtocolVersion(protocolVersion)
                .packetId(packetId)
                .length(length)
                .direction(dir)
                .iat(iat)
                .entropy(entropy)
                .timestamp(System.currentTimeMillis())
                .build();
        sendToRabbit(vec);
    }


    /**
     * Sends a packet directly to RabbitMQ without queuing.
     * This method is used by the async queue processor.
     */
    private void sendToRabbitDirectly(FeatureVec vec) {
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
            rabbitChannel.basicPublish(this.exchange, this.routingKey, null, payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            logger.error("Failed to send packet data to RabbitMQ", ex);
        }
    }
    
    /**
     * Queues a packet for async sending to RabbitMQ.
     * If the accuracy threshold for the player is met or exceeded, the packet will be skipped.
     */
    private void sendToRabbit(FeatureVec vec) {
        try {
            // Get the client state to check accuracy
            UUID clientId = UUID.fromString(vec.clientId);
            ClientState clientState = state.get(clientId);
            
            if (clientState != null) {
                // Check if the accuracy meets or exceeds the threshold
                if (clientState.lastPercentage >= accuracyThreshold) {
                    // Skip this packet
                    logger.debug("Skipping packet for client {} as accuracy threshold of {}% is met with {}%", clientId, accuracyThreshold, clientState.lastPercentage);
                    return;
                }
                
                // Increment packet count
                clientState.packetCount++;
            }
            
            // Add to queue for async processing
            packetQueue.offer(vec);
        } catch (Exception ex) {
            logger.error("Failed to queue packet data", ex);
        }
    }
    
    /**
     * Formats a timestamp in milliseconds to a human-readable time string (HH:mm:ss).
     *
     * @param timestamp The timestamp in milliseconds
     * @return A formatted time string
     */
    private String formatTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
        return dateTime.format(timeFormatter);
    }
    
    /**
     * Processes the packet queue asynchronously.
     * This method is called periodically by the scheduler.
     */
    private void processPacketQueue() {
        try {
            // Process up to 100 packets at a time to avoid blocking the thread for too long
            int processedCount = 0;
            while (!packetQueue.isEmpty() && processedCount < 100) {
                FeatureVec vec = packetQueue.poll();
                if (vec != null) {
                    try {
                        sendToRabbitDirectly(vec);
                        processedCount++;
                    } catch (Exception ex) {
                        logger.error("Failed to process packet from queue", ex);
                    }
                }
            }
            
            if (processedCount > 0) {
                logger.debug("Processed {} packets from queue, {} remaining", processedCount, packetQueue.size());
            }
        } catch (Exception ex) {
            logger.error("Error in packet queue processor", ex);
        }
    }
    
    /**
     * Displays client information to all online players.
     * This method is called periodically by the scheduler.
     */
    private void displayClientInformation() {
        long currentTime = System.currentTimeMillis();
        long evaluationIntervalMillis = TimeUnit.MINUTES.toMillis(evaluationIntervalMinutes);
        
        // Iterate through all online players
        server.getAllPlayers().forEach(player -> {
            UUID clientId = player.getUniqueId();
            ClientState clientState = state.get(clientId);
            
            // Skip if no evaluation has been done yet
            if (clientState == null || clientState.lastEvaluationTime == 0 || clientState.lastClientType == null) {
                return;
            }
            
            // Format last evaluation time
            String lastEvalTime = formatTime(clientState.lastEvaluationTime);
            
            // Set up title display
            player.sendTitlePart(TitlePart.TIMES, Title.Times.times(
                    Duration.of(500, ChronoUnit.MILLIS), // fade in
                    Duration.of(1, ChronoUnit.SECONDS), // stay
                    Duration.of(500, ChronoUnit.MILLIS) // fade out
            ));
            
            // Display client type and accuracy
            player.sendTitlePart(TitlePart.TITLE, MiniMessage.miniMessage().deserialize("<green>Your client type is <yellow><client> <green>with <yellow><accuracy>% <green>accuracy.",
                    Placeholder.component("client", Component.text(clientState.lastClientType)),
                    Placeholder.component("accuracy", Component.text(clientState.lastPercentage))
            ));
            
            // Check if accuracy threshold has been met
            if (clientState.thresholdMet) {
                // Display message indicating that the threshold has been met
                player.sendTitlePart(TitlePart.SUBTITLE, MiniMessage.miniMessage().deserialize(
                        "<green>Last evaluation: <yellow><last_eval_time> <green>- <yellow>Accuracy threshold of <threshold>% met! <green>No further evaluations needed.",
                        Placeholder.component("last_eval_time", Component.text(lastEvalTime)),
                        Placeholder.component("threshold", Component.text(accuracyThreshold))
                ));
            } else {
                // Calculate next evaluation time
                long nextEvaluationTime = clientState.lastEvaluationTime + evaluationIntervalMillis;
                String nextEvalTime = formatTime(nextEvaluationTime);
                
                // Calculate time remaining until next evaluation
                long timeRemainingMillis = Math.max(0, nextEvaluationTime - currentTime);
                long minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMillis);
                long secondsRemaining = TimeUnit.MILLISECONDS.toSeconds(timeRemainingMillis) % 60;
                
                // Display next evaluation time and time remaining
                player.sendTitlePart(TitlePart.SUBTITLE, MiniMessage.miniMessage().deserialize(
                        "<green>Last evaluation: <yellow><last_eval_time>,<green> Next evaluation: <yellow><next_eval_time>(<yellow><minutes_remaining>m <seconds_remaining>s <green>remaining)",
                        Placeholder.component("last_eval_time", Component.text(lastEvalTime)),
                        Placeholder.component("next_eval_time", Component.text(nextEvalTime)),
                        Placeholder.component("minutes_remaining", Component.text(minutesRemaining)),
                        Placeholder.component("seconds_remaining", Component.text(secondsRemaining))
                ));
            }
        });
    }
}
