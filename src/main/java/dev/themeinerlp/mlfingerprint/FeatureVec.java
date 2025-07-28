package dev.themeinerlp.mlfingerprint;

/**
 * Represents a feature vector for machine learning purposes.
 * This class encapsulates various features extracted from packets, such as client ID, protocol version,
 * packet ID, length, direction, inter-arrival time (iat), entropy, and timestamp.
 */
final class FeatureVec {
    String clientId;
    int clientProtocolVersion;
    int packetId;
    int length;
    Direction direction;
    long iat;
    double entropy;
    long timestamp;

    private FeatureVec() {
        // Private constructor to enforce the use of the builder
    }

    /**
     * Creates a new FeatureVec builder instance.
     *
     * @return a new Builder instance for constructing FeatureVec objects
     */
    static Builder builder() {
        return new Builder();
    }


    /**
     * Builder class for constructing FeatureVec instances.
     */
    static class Builder {
        private final FeatureVec vec;
        Builder() {
            vec = new FeatureVec();
        }

        Builder clientId(String clientId) {
            vec.clientId = clientId;
            return this;
        }

        Builder clientProtocolVersion(int clientProtocolVersion) {
            vec.clientProtocolVersion = clientProtocolVersion;
            return this;
        }

        Builder packetId(int packetId) {
            vec.packetId = packetId;
            return this;
        }

        Builder length(int length) {
            vec.length = length;
            return this;
        }

        Builder direction(Direction direction) {
            vec.direction = direction;
            return this;
        }

        Builder iat(long iat) {
            vec.iat = iat;
            return this;
        }

        Builder entropy(double entropy) {
            vec.entropy = entropy;
            return this;
        }

        Builder timestamp(long timestamp) {
            vec.timestamp = timestamp;
            return this;
        }

        Builder from(FeatureVec other) {
            vec.clientId = other.clientId;
            vec.clientProtocolVersion = other.clientProtocolVersion;
            vec.packetId = other.packetId;
            vec.length = other.length;
            vec.direction = other.direction;
            vec.iat = other.iat;
            vec.entropy = other.entropy;
            vec.timestamp = other.timestamp;
            return this;
        }

        FeatureVec build() {
            return vec;
        }
    }
}
