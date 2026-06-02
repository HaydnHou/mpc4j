package edu.alibaba.mpc4j.common.structure.iblt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Long-key IBLT peel result.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class LongIbltPeelResult {
    /**
     * success.
     */
    private final boolean success;
    /**
     * entries.
     */
    private final List<LongIbltEntry> entries;

    LongIbltPeelResult(boolean success, List<LongIbltEntry> entries) {
        this.success = success;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /**
     * Gets success.
     *
     * @return success.
     */
    public boolean success() {
        return success;
    }

    /**
     * Gets entries.
     *
     * @return entries.
     */
    public List<LongIbltEntry> entries() {
        return entries;
    }

    /**
     * Gets positive entries.
     *
     * @return positive entries.
     */
    public List<LongIbltEntry> positiveEntries() {
        List<LongIbltEntry> positiveEntries = new ArrayList<>();
        for (LongIbltEntry entry : entries) {
            if (entry.sign() > 0) {
                positiveEntries.add(entry);
            }
        }
        return positiveEntries;
    }

    /**
     * Gets negative entries.
     *
     * @return negative entries.
     */
    public List<LongIbltEntry> negativeEntries() {
        List<LongIbltEntry> negativeEntries = new ArrayList<>();
        for (LongIbltEntry entry : entries) {
            if (entry.sign() < 0) {
                negativeEntries.add(entry);
            }
        }
        return negativeEntries;
    }
}
