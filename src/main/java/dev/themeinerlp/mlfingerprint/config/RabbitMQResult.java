package dev.themeinerlp.mlfingerprint.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class RabbitMQResult {

    private String queue = "results_queue";
    private String routingKey = "results";

    public String getQueue() {
        return queue;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
