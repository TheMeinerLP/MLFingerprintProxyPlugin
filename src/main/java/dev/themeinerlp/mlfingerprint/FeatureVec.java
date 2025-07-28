package dev.themeinerlp.mlfingerprint;

final class FeatureVec {
    String clientId;
    int clientProtocolVersion;
    int packetId;
    int length;
    Direction direction;
    long iat;
    double entropy;
    long timestamp;

    static Builder builder() {
        return new Builder();
    }

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
