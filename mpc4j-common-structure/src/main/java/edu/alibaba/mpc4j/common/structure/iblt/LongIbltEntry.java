package edu.alibaba.mpc4j.common.structure.iblt;

import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;

/**
 * Long-key IBLT peel entry.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class LongIbltEntry {
    /**
     * key.
     */
    private final long key;
    /**
     * value.
     */
    private final byte[] value;
    /**
     * sign, +1 for this IBLT and -1 for the subtracted IBLT.
     */
    private final int sign;

    LongIbltEntry(long key, byte[] value, int sign) {
        this.key = key;
        this.value = BytesUtils.clone(value);
        this.sign = sign;
    }

    /**
     * Gets key.
     *
     * @return key.
     */
    public long key() {
        return key;
    }

    /**
     * Gets value.
     *
     * @return value.
     */
    public byte[] value() {
        return BytesUtils.clone(value);
    }

    /**
     * Gets sign.
     *
     * @return sign.
     */
    public int sign() {
        return sign;
    }
}
