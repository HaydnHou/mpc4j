package edu.alibaba.mpc4j.common.structure.iblt;

import java.util.Collection;

/**
 * Long-key IBLT.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public interface LongIblt {
    /**
     * Gets type.
     *
     * @return type.
     */
    LongIbltFactory.LongIbltType getType();

    /**
     * Adds a key without value.
     *
     * @param key key.
     */
    void addKey(long key);

    /**
     * Adds keys without values.
     *
     * @param keys keys.
     */
    void addKeys(long[] keys);

    /**
     * Adds keys without values.
     *
     * @param keys keys.
     */
    void addKeys(Collection<Long> keys);

    /**
     * Adds a key-value item.
     *
     * @param key   key.
     * @param value value.
     */
    void add(long key, byte[] value);

    /**
     * Adds key-value items.
     *
     * @param keys   keys.
     * @param values values.
     */
    void add(long[] keys, byte[][] values);

    /**
     * Removes a key without value.
     *
     * @param key key.
     */
    void removeKey(long key);

    /**
     * Removes keys without values.
     *
     * @param keys keys.
     */
    void removeKeys(long[] keys);

    /**
     * Removes keys without values.
     *
     * @param keys keys.
     */
    void removeKeys(Collection<Long> keys);

    /**
     * Removes a key-value item.
     *
     * @param key   key.
     * @param value value.
     */
    void remove(long key, byte[] value);

    /**
     * Removes key-value items.
     *
     * @param keys   keys.
     * @param values values.
     */
    void remove(long[] keys, byte[][] values);

    /**
     * Subtracts another IBLT from this IBLT in place.
     *
     * @param other the other IBLT.
     */
    void subtract(LongIblt other);

    /**
     * Peels the IBLT in place.
     *
     * @return peel result.
     */
    LongIbltPeelResult peel();

    /**
     * Tests if this IBLT is peelable without modifying the current IBLT.
     *
     * @return true if this IBLT is peelable.
     */
    boolean isPeelable();

    /**
     * Returns all hash positions of a key.
     *
     * @param key key.
     * @return positions.
     */
    int[] positions(long key);

    /**
     * Returns deduplicated positions hit by the given keys, excluding positions marked by the bitmap.
     *
     * @param keys     keys.
     * @param excluded excluded bitmap.
     * @return deduplicated positions.
     */
    int[] uniquePositions(long[] keys, boolean[] excluded);

    /**
     * Returns deduplicated positions hit by the given keys.
     *
     * @param keys keys.
     * @return deduplicated positions.
     */
    int[] uniquePositions(long[] keys);

    /**
     * Clears this IBLT.
     */
    void clear();

    /**
     * Gets threshold.
     *
     * @return threshold.
     */
    int threshold();

    /**
     * Gets table length multiplier.
     *
     * @return table length multiplier.
     */
    double multiplier();

    /**
     * Gets subtable length.
     *
     * @return subtable length.
     */
    int subTableLength();

    /**
     * Gets table length.
     *
     * @return table length.
     */
    int tableLength();

    /**
     * Gets value byte length.
     *
     * @return value byte length.
     */
    int valueByteLength();

    /**
     * Gets net item count.
     *
     * @return net item count.
     */
    int size();

    /**
     * Gets the hash key.
     *
     * @return hash key.
     */
    byte[] hashKey();

    /**
     * Gets cloned key sums.
     *
     * @return cloned key sums.
     */
    long[] keySums();

    /**
     * Gets cloned key checksum sums.
     *
     * @return cloned key checksum sums.
     */
    long[] keyCheckSums();

    /**
     * Gets cloned value sums.
     *
     * @return cloned value sums.
     */
    byte[][] valueSums();

    /**
     * Gets cloned counts.
     *
     * @return cloned counts.
     */
    int[] counts();
}
