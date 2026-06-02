package edu.alibaba.mpc4j.common.structure.iblt;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;

/**
 * Factory for long-key IBLT.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class LongIbltFactory {
    /**
     * private constructor.
     */
    private LongIbltFactory() {
        // empty
    }

    /**
     * Long-key IBLT type.
     */
    public enum LongIbltType {
        /**
         * five-hash long IBLT.
         */
        H5_LONG,
    }

    /**
     * Gets the number of hash keys.
     *
     * @param type type.
     * @return the number of hash keys.
     */
    public static int getHashKeyNum(LongIbltType type) {
        return switch (type) {
            case H5_LONG -> 1;
        };
    }

    /**
     * Creates an empty long-key IBLT with the default table length multiplier.
     *
     * @param envType         environment.
     * @param type            type.
     * @param threshold       expected number of items to peel.
     * @param valueByteLength value byte length.
     * @param key             hash key.
     * @return an empty long-key IBLT.
     */
    public static LongIblt createInstance(EnvType envType, LongIbltType type,
                                          int threshold, int valueByteLength, byte[][] keys) {
        checkKeys(type, keys);
        return switch (type) {
            case H5_LONG -> H5LongIblt.create(envType, threshold, valueByteLength, keys[0]);
        };
    }

    /**
     * Creates an empty long-key IBLT.
     *
     * @param envType         environment.
     * @param type            type.
     * @param threshold       expected number of items to peel.
     * @param multiplier      table length multiplier.
     * @param valueByteLength value byte length.
     * @param key             hash key.
     * @return an empty long-key IBLT.
     */
    public static LongIblt createInstance(EnvType envType, LongIbltType type,
                                          int threshold, double multiplier, int valueByteLength, byte[][] keys) {
        checkKeys(type, keys);
        return switch (type) {
            case H5_LONG -> H5LongIblt.create(envType, threshold, multiplier, valueByteLength, keys[0]);
        };
    }

    private static void checkKeys(LongIbltType type, byte[][] keys) {
        Preconditions.checkNotNull(keys);
        MathPreconditions.checkEqual("keys.length", "hashKeyNum", keys.length, getHashKeyNum(type));
    }
}
