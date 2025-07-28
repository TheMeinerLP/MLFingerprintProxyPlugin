package dev.themeinerlp.mlfingerprint;

/**
 * Represents a message containing client percentage information.
 * This class encapsulates the client UUID, the percentage value, and the client type.
 */
public class ClientPercentageMessage {
    private String clientId;
    private double percentage;
    private String client;

    private ClientPercentageMessage() {
        // Private constructor to enforce the use of the builder
    }

    public String getClientId() {
        return clientId;
    }

    public double getPercentage() {
        return percentage;
    }

    public String getClient() {
        return client;
    }
}