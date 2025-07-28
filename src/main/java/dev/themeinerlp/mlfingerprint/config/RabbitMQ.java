package dev.themeinerlp.mlfingerprint.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@ConfigSerializable
public class RabbitMQ {

    private String host = "localhost";
    private int port = 5672;
    private String username = "guest";
    private String password = "guest";
    private String exchange = "mlfingerprint";
    private String queue = "packets_queue";
    private String routingKey = "packets";
    private String type = "direct";
    private String vhost = "/";

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getExchange() {
        return exchange;
    }

    public String getQueue() {
        return queue;
    }

    public String getType() {
        return type;
    }

    public String getVhost() {
        return vhost;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
