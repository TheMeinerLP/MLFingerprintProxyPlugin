package dev.themeinerlp.mlfingerprint.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class MLConfiguration {

    private RabbitMQ rabbitMQ = new RabbitMQ();

    public RabbitMQ getRabbitMQ() {
        return rabbitMQ;
    }
}
