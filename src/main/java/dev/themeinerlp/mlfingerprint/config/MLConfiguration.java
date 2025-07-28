package dev.themeinerlp.mlfingerprint.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class MLConfiguration {

    private RabbitMQ rabbitMQ = new RabbitMQ();
    private RabbitMQResult rabbitMQResult = new RabbitMQResult();

    public RabbitMQ getRabbitMQ() {
        return rabbitMQ;
    }

    public RabbitMQResult getRabbitMQResult() {
        return rabbitMQResult;
    }
}
