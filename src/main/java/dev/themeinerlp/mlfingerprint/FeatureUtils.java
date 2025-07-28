package dev.themeinerlp.mlfingerprint;

/**
 * Utility class for feature calculations.
 * This class provides methods to calculate features such as entropy from byte arrays.
 */
final class FeatureUtils {
    private  FeatureUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Calculates the entropy of the given byte array.
     *
     * @param data the byte array to calculate entropy for
     * @return the calculated entropy value
     */
    static double calcEntropy(byte[] data) {
        if (data == null || data.length == 0) return 0.0;
        int[] freq = new int[256];
        for (byte b : data) freq[b & 0xFF]++;
        double ent = 0.0;
        int len = data.length;
        for (int c : freq) {
            if (c > 0) {
                double p = (double) c / len;
                ent -= p * (Math.log(p) / Math.log(2));
            }
        }
        return ent;
    }
}
