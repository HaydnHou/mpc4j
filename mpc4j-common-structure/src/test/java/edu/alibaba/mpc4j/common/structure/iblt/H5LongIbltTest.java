package edu.alibaba.mpc4j.common.structure.iblt;

import edu.alibaba.mpc4j.common.structure.iblt.LongIbltFactory.LongIbltType;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.EnvType;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import org.junit.Assert;
import org.junit.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests for five-hash long IBLT.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class H5LongIbltTest {
    /**
     * default size.
     */
    private static final int DEFAULT_SIZE = 1 << 10;
    /**
     * default value byte length.
     */
    private static final int DEFAULT_VALUE_BYTE_LENGTH = 16;
    /**
     * domain separator used in the C++ source for the second position hash block.
     */
    private static final long POSITION_HASH_DOMAIN_SEPARATOR = 0x9E35468B2FBBEC9CL;
    /**
     * random state.
     */
    private final SecureRandom secureRandom;

    public H5LongIbltTest() {
        secureRandom = new SecureRandom();
    }

    @Test
    public void testFactory() {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        Assert.assertEquals(1, LongIbltFactory.getHashKeyNum(LongIbltType.H5_LONG));
        LongIblt iblt = LongIbltFactory.createInstance(
            EnvType.STANDARD, LongIbltType.H5_LONG, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, new byte[][]{key}
        );
        Assert.assertEquals(LongIbltType.H5_LONG, iblt.getType());
        Assert.assertEquals(DEFAULT_SIZE, iblt.threshold());
        Assert.assertEquals(H5LongIblt.DEFAULT_MULTIPLIER, iblt.multiplier(), 0.0);
        Assert.assertEquals(
            H5LongIblt.calcTableLength(DEFAULT_SIZE, H5LongIblt.DEFAULT_MULTIPLIER), iblt.tableLength()
        );
        Assert.assertThrows(IllegalArgumentException.class, () -> LongIbltFactory.createInstance(
            EnvType.STANDARD, LongIbltType.H5_LONG, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, new byte[0][]
        ));
        Assert.assertThrows(IllegalArgumentException.class, () -> LongIbltFactory.createInstance(
            EnvType.STANDARD, LongIbltType.H5_LONG, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH,
            new byte[][]{key, BlockUtils.randomBlock(secureRandom)}
        ));
    }

    @Test
    public void testIllegalInputs() {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        Assert.assertThrows(IllegalArgumentException.class, () ->
            H5LongIblt.create(EnvType.STANDARD, 0, DEFAULT_VALUE_BYTE_LENGTH, key)
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE, 0.0, DEFAULT_VALUE_BYTE_LENGTH, key)
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE, -1, key)
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            H5LongIblt.create(EnvType.STANDARD, Integer.MAX_VALUE, DEFAULT_VALUE_BYTE_LENGTH, key)
        );
        Assert.assertThrows(IllegalArgumentException.class, () ->
            H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, new byte[1])
        );

        H5LongIblt iblt = H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, key);
        Assert.assertThrows(NullPointerException.class, () -> iblt.add(1L, null));
        Assert.assertThrows(NullPointerException.class, () -> iblt.remove(1L, null));
        Assert.assertThrows(IllegalArgumentException.class, () -> iblt.add(1L, new byte[DEFAULT_VALUE_BYTE_LENGTH - 1]));
        Assert.assertThrows(IllegalArgumentException.class, () -> iblt.remove(1L, new byte[DEFAULT_VALUE_BYTE_LENGTH + 1]));
        Assert.assertThrows(IllegalArgumentException.class, () -> iblt.add(new long[]{1L, 2L}, new byte[1][]));
        Assert.assertThrows(NullPointerException.class, () -> iblt.add(new long[]{1L}, new byte[][]{null}));
        Assert.assertThrows(IllegalArgumentException.class, () ->
            iblt.add(new long[]{1L, 2L}, new byte[][]{new byte[DEFAULT_VALUE_BYTE_LENGTH], new byte[1]})
        );
        assertEmpty(iblt);
        Assert.assertThrows(IllegalArgumentException.class, () -> iblt.uniquePositions(new long[]{1L}, new boolean[1]));

        H5LongIblt otherThreshold = H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE + 1, DEFAULT_VALUE_BYTE_LENGTH, key);
        Assert.assertThrows(IllegalArgumentException.class, () -> iblt.subtract(otherThreshold));
        H5LongIblt otherKey = H5LongIblt.create(
            EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, BlockUtils.randomBlock(secureRandom)
        );
        Assert.assertThrows(IllegalArgumentException.class, () -> iblt.subtract(otherKey));
    }

    @Test
    public void testPositions() throws Exception {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        H5LongIblt iblt = H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, key);
        long item = secureRandom.nextLong();
        int[] positions = iblt.positions(item);
        Assert.assertEquals(H5LongIblt.HASH_NUM, positions.length);
        Assert.assertArrayEquals(positions, iblt.positions(item));
        Assert.assertArrayEquals(expectedPositions(key, item, iblt.subTableLength()), positions);
        for (int hashIndex = 0; hashIndex < H5LongIblt.HASH_NUM; hashIndex++) {
            Assert.assertTrue(positions[hashIndex] >= hashIndex * iblt.subTableLength());
            Assert.assertTrue(positions[hashIndex] < (hashIndex + 1) * iblt.subTableLength());
        }
    }

    @Test
    public void testAddRemoveKeyOnly() {
        long[] keys = randomKeys(DEFAULT_SIZE);
        H5LongIblt iblt = H5LongIblt.create(
            EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, BlockUtils.randomBlock(secureRandom)
        );
        iblt.addKeys(keys);
        Assert.assertEquals(keys.length, iblt.size());
        iblt.removeKeys(keys);
        Assert.assertEquals(0, iblt.size());
        assertEmpty(iblt);

        iblt.addKeys(List.of(keys[0], keys[1]));
        Assert.assertEquals(2, iblt.size());
        iblt.removeKeys(List.of(keys[0], keys[1]));
        Assert.assertEquals(0, iblt.size());
        assertEmpty(iblt);
    }

    @Test
    public void testPeelKeyOnly() {
        long[] keys = randomKeys(DEFAULT_SIZE);
        H5LongIblt iblt = H5LongIblt.create(
            EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, BlockUtils.randomBlock(secureRandom)
        );
        iblt.addKeys(keys);
        Assert.assertTrue(iblt.isPeelable());
        LongIbltPeelResult peelResult = iblt.copy().peel();
        Assert.assertTrue(peelResult.success());
        Assert.assertEquals(keys.length, peelResult.entries().size());

        Set<Long> expectKeySet = toSet(keys);
        Set<Long> actualKeySet = new HashSet<>();
        byte[] zeroValue = new byte[DEFAULT_VALUE_BYTE_LENGTH];
        for (LongIbltEntry peelEntry : peelResult.entries()) {
            Assert.assertEquals(1, peelEntry.sign());
            Assert.assertArrayEquals(zeroValue, peelEntry.value());
            actualKeySet.add(peelEntry.key());
        }
        Assert.assertEquals(expectKeySet, actualKeySet);
    }

    @Test
    public void testPeelValues() {
        long[] keys = randomKeys(DEFAULT_SIZE);
        byte[][] values = randomValues(DEFAULT_SIZE);
        H5LongIblt iblt = H5LongIblt.create(
            EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, BlockUtils.randomBlock(secureRandom)
        );
        iblt.add(keys, values);
        LongIbltPeelResult peelResult = iblt.copy().peel();
        Assert.assertTrue(peelResult.success());
        Assert.assertEquals(keys.length, peelResult.entries().size());

        Map<Long, byte[]> expectKeyValueMap = toMap(keys, values);
        Map<Long, byte[]> actualKeyValueMap = new HashMap<>();
        for (LongIbltEntry peelEntry : peelResult.entries()) {
            Assert.assertEquals(1, peelEntry.sign());
            actualKeyValueMap.put(peelEntry.key(), peelEntry.value());
        }
        Assert.assertEquals(expectKeyValueMap.keySet(), actualKeyValueMap.keySet());
        for (long key : keys) {
            Assert.assertArrayEquals(expectKeyValueMap.get(key), actualKeyValueMap.get(key));
        }
    }

    @Test
    public void testSubtract() {
        long[] allKeys = randomKeys(DEFAULT_SIZE * 3 / 4);
        long[] commonKeys = Arrays.copyOfRange(allKeys, 0, DEFAULT_SIZE / 4);
        long[] leftKeys = Arrays.copyOfRange(allKeys, DEFAULT_SIZE / 4, DEFAULT_SIZE / 2);
        long[] rightKeys = Arrays.copyOfRange(allKeys, DEFAULT_SIZE / 2, DEFAULT_SIZE * 3 / 4);
        Map<Long, byte[]> valueMap = new HashMap<>();
        putRandomValues(valueMap, commonKeys);
        putRandomValues(valueMap, leftKeys);
        putRandomValues(valueMap, rightKeys);

        long[] leftInputKeys = concat(commonKeys, leftKeys);
        long[] rightInputKeys = concat(commonKeys, rightKeys);
        byte[][] leftInputValues = valuesOf(valueMap, leftInputKeys);
        byte[][] rightInputValues = valuesOf(valueMap, rightInputKeys);
        byte[] key = BlockUtils.randomBlock(secureRandom);
        H5LongIblt leftIblt = H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, key);
        H5LongIblt rightIblt = H5LongIblt.create(EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, key);
        leftIblt.add(leftInputKeys, leftInputValues);
        rightIblt.add(rightInputKeys, rightInputValues);

        leftIblt.subtract(rightIblt);
        LongIbltPeelResult peelResult = leftIblt.peel();
        Assert.assertTrue(peelResult.success());
        Assert.assertEquals(leftKeys.length + rightKeys.length, peelResult.entries().size());

        Set<Long> leftKeySet = toSet(leftKeys);
        Set<Long> rightKeySet = toSet(rightKeys);
        Set<Long> decodedLeftKeySet = new HashSet<>();
        Set<Long> decodedRightKeySet = new HashSet<>();
        for (LongIbltEntry peelEntry : peelResult.entries()) {
            if (peelEntry.sign() == 1) {
                decodedLeftKeySet.add(peelEntry.key());
            } else {
                decodedRightKeySet.add(peelEntry.key());
            }
            Assert.assertArrayEquals(valueMap.get(peelEntry.key()), peelEntry.value());
        }
        Assert.assertEquals(leftKeySet, decodedLeftKeySet);
        Assert.assertEquals(rightKeySet, decodedRightKeySet);
        Assert.assertEquals(leftKeys.length, peelResult.positiveEntries().size());
        Assert.assertEquals(rightKeys.length, peelResult.negativeEntries().size());
    }

    @Test
    public void testSameKeyDifferentValuesCannotPeel() {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        H5LongIblt leftIblt = H5LongIblt.create(EnvType.STANDARD, 2, DEFAULT_VALUE_BYTE_LENGTH, key);
        H5LongIblt rightIblt = H5LongIblt.create(EnvType.STANDARD, 2, DEFAULT_VALUE_BYTE_LENGTH, key);
        long item = secureRandom.nextLong();
        byte[] leftValue = new byte[DEFAULT_VALUE_BYTE_LENGTH];
        byte[] rightValue = new byte[DEFAULT_VALUE_BYTE_LENGTH];
        secureRandom.nextBytes(leftValue);
        secureRandom.nextBytes(rightValue);
        leftIblt.add(item, leftValue);
        rightIblt.add(item, rightValue);

        leftIblt.subtract(rightIblt);
        LongIbltPeelResult peelResult = leftIblt.copy().peel();
        Assert.assertFalse(peelResult.success());
        Assert.assertTrue(peelResult.entries().isEmpty());
        Assert.assertFalse(leftIblt.isPeelable());
    }

    @Test
    public void testDuplicateKeyMultisetBehavior() {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        H5LongIblt iblt = H5LongIblt.create(EnvType.STANDARD, 2, 0, key);
        long item = secureRandom.nextLong();
        iblt.addKey(item);
        iblt.addKey(item);
        Assert.assertFalse(iblt.copy().peel().success());

        iblt.removeKey(item);
        LongIbltPeelResult peelResult = iblt.copy().peel();
        Assert.assertTrue(peelResult.success());
        Assert.assertEquals(1, peelResult.entries().size());
        Assert.assertEquals(item, peelResult.entries().get(0).key());
        Assert.assertEquals(1, peelResult.entries().get(0).sign());
    }

    @Test
    public void testNonPeelable() {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        H5LongIblt iblt = H5LongIblt.create(EnvType.STANDARD, 1, 1.0, 0, key);
        Assert.assertEquals(1, iblt.subTableLength());
        iblt.addKey(1L);
        iblt.addKey(2L);
        LongIbltPeelResult peelResult = iblt.copy().peel();
        Assert.assertFalse(peelResult.success());
        Assert.assertTrue(peelResult.entries().isEmpty());
        Assert.assertFalse(iblt.isPeelable());
    }

    @Test
    public void testZeroLengthValueAndBoundaryKeys() {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        H5LongIblt iblt = H5LongIblt.create(EnvType.STANDARD, 3, 0, key);
        long[] keys = new long[]{0L, Long.MIN_VALUE, Long.MAX_VALUE};
        byte[][] values = new byte[][]{new byte[0], new byte[0], new byte[0]};
        iblt.add(keys, values);
        LongIbltPeelResult peelResult = iblt.copy().peel();
        Assert.assertTrue(peelResult.success());
        Assert.assertEquals(keys.length, peelResult.entries().size());
        Set<Long> actualKeySet = new HashSet<>();
        for (LongIbltEntry peelEntry : peelResult.entries()) {
            Assert.assertArrayEquals(new byte[0], peelEntry.value());
            actualKeySet.add(peelEntry.key());
        }
        Assert.assertEquals(toSet(keys), actualKeySet);
    }

    @Test
    public void testCopyClearAndDefensiveCopies() {
        byte[] key = BlockUtils.randomBlock(secureRandom);
        H5LongIblt iblt = H5LongIblt.create(EnvType.STANDARD, 1, DEFAULT_VALUE_BYTE_LENGTH, key);
        long item = secureRandom.nextLong();
        byte[] value = new byte[DEFAULT_VALUE_BYTE_LENGTH];
        secureRandom.nextBytes(value);
        iblt.add(item, value);

        byte[] hashKey = iblt.hashKey();
        hashKey[0] ^= 0x01;
        Assert.assertArrayEquals(key, iblt.hashKey());
        long[] keySums = iblt.keySums();
        keySums[iblt.positions(item)[0]] ^= item;
        Assert.assertTrue(iblt.copy().peel().success());
        byte[][] valueSums = iblt.valueSums();
        valueSums[iblt.positions(item)[0]][0] ^= 0x01;
        Assert.assertTrue(iblt.copy().peel().success());
        int[] counts = iblt.counts();
        counts[iblt.positions(item)[0]] = 0;
        Assert.assertTrue(iblt.copy().peel().success());

        LongIbltEntry peelEntry = iblt.copy().peel().entries().get(0);
        byte[] peelValue = peelEntry.value();
        peelValue[0] ^= 0x01;
        Assert.assertArrayEquals(value, peelEntry.value());

        H5LongIblt copy = iblt.copy();
        copy.remove(item, value);
        assertEmpty(copy);
        Assert.assertEquals(1, iblt.size());
        iblt.clear();
        assertEmpty(iblt);
    }

    @Test
    public void testUniquePositions() {
        long[] keys = randomKeys(64);
        H5LongIblt iblt = H5LongIblt.create(
            EnvType.STANDARD, DEFAULT_SIZE, DEFAULT_VALUE_BYTE_LENGTH, BlockUtils.randomBlock(secureRandom)
        );
        boolean[] excluded = new boolean[iblt.tableLength()];
        int excludedPosition = iblt.positions(keys[0])[0];
        excluded[excludedPosition] = true;
        int[] uniquePositions = iblt.uniquePositions(keys, excluded);
        Set<Integer> uniquePositionSet = new HashSet<>();
        for (int position : uniquePositions) {
            Assert.assertFalse(excluded[position]);
            Assert.assertTrue(uniquePositionSet.add(position));
        }
        int[] allUniquePositions = iblt.uniquePositions(keys);
        Assert.assertTrue(allUniquePositions.length >= uniquePositions.length);
        Assert.assertTrue(Arrays.stream(allUniquePositions).anyMatch(position -> position == excludedPosition));
    }

    private long[] randomKeys(int size) {
        LinkedHashSet<Long> keySet = new LinkedHashSet<>();
        while (keySet.size() < size) {
            keySet.add(secureRandom.nextLong());
        }
        return keySet.stream().mapToLong(Long::longValue).toArray();
    }

    private byte[][] randomValues(int size) {
        byte[][] values = new byte[size][DEFAULT_VALUE_BYTE_LENGTH];
        for (byte[] value : values) {
            secureRandom.nextBytes(value);
        }
        return values;
    }

    private void putRandomValues(Map<Long, byte[]> valueMap, long[] keys) {
        for (long key : keys) {
            byte[] value = new byte[DEFAULT_VALUE_BYTE_LENGTH];
            secureRandom.nextBytes(value);
            valueMap.put(key, value);
        }
    }

    private Map<Long, byte[]> toMap(long[] keys, byte[][] values) {
        Map<Long, byte[]> keyValueMap = new HashMap<>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            keyValueMap.put(keys[i], values[i]);
        }
        return keyValueMap;
    }

    private Set<Long> toSet(long[] keys) {
        Set<Long> keySet = new HashSet<>(keys.length);
        for (long key : keys) {
            keySet.add(key);
        }
        return keySet;
    }

    private long[] concat(long[] left, long[] right) {
        long[] result = new long[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

    private byte[][] valuesOf(Map<Long, byte[]> valueMap, long[] keys) {
        byte[][] values = new byte[keys.length][];
        for (int i = 0; i < keys.length; i++) {
            values[i] = valueMap.get(keys[i]);
        }
        return values;
    }

    private void assertEmpty(H5LongIblt iblt) {
        for (long keySum : iblt.keySums()) {
            Assert.assertEquals(0L, keySum);
        }
        for (long keyCheckSum : iblt.keyCheckSums()) {
            Assert.assertEquals(0L, keyCheckSum);
        }
        for (byte[] valueSum : iblt.valueSums()) {
            Assert.assertArrayEquals(new byte[iblt.valueByteLength()], valueSum);
        }
        for (int count : iblt.counts()) {
            Assert.assertEquals(0, count);
        }
    }

    private int[] expectedPositions(byte[] key, long item, int subTableLength) throws Exception {
        byte[] firstHashBlock = aesHashBlock(key, 0L, item);
        byte[] secondHashBlock = aesHashBlock(key, POSITION_HASH_DOMAIN_SEPARATOR, item);
        ByteBuffer firstHashBuffer = ByteBuffer.wrap(firstHashBlock).order(ByteOrder.LITTLE_ENDIAN);
        int[] positions = new int[H5LongIblt.HASH_NUM];
        for (int hashIndex = 0; hashIndex < H5LongIblt.HASH_NUM - 1; hashIndex++) {
            long hashOutput = Integer.toUnsignedLong(firstHashBuffer.getInt(hashIndex * Integer.BYTES));
            positions[hashIndex] = (int) (hashOutput % subTableLength) + hashIndex * subTableLength;
        }
        long hashOutput = Integer.toUnsignedLong(ByteBuffer.wrap(secondHashBlock).order(ByteOrder.LITTLE_ENDIAN).getInt());
        positions[H5LongIblt.HASH_NUM - 1] = (int) (hashOutput % subTableLength)
            + (H5LongIblt.HASH_NUM - 1) * subTableLength;
        return positions;
    }

    private byte[] aesHashBlock(byte[] key, long domainSeparator, long item) throws Exception {
        byte[] input = ByteBuffer.allocate(CommonConstants.BLOCK_BYTE_LENGTH)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(item)
            .putLong(domainSeparator)
            .array();
        Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
        byte[] output = cipher.doFinal(input);
        BytesUtils.xori(output, input);
        return output;
    }
}
