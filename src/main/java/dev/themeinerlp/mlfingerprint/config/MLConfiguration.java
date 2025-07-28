package dev.themeinerlp.mlfingerprint.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public class MLConfiguration {

    private RabbitMQ rabbitMQ = new RabbitMQ();
    private RabbitMQResult rabbitMQResult = new RabbitMQResult();
    
    @Comment("Interval in minutes between client evaluations (default: 5)")
    private int evaluationIntervalMinutes = 1;
    
    @Comment("Interval in seconds for displaying client information (default: 1)")
    private int displayIntervalSeconds = 1;

    public RabbitMQ getRabbitMQ() {
        return rabbitMQ;
    }

    public RabbitMQResult getRabbitMQResult() {
        return rabbitMQResult;
    }
    
    public int getEvaluationIntervalMinutes() {
        return evaluationIntervalMinutes;
    }
    
    public int getDisplayIntervalSeconds() {
        return displayIntervalSeconds;
    }
}
