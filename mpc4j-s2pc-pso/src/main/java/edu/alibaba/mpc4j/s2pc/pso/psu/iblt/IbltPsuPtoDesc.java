package edu.alibaba.mpc4j.s2pc.pso.psu.iblt;

import edu.alibaba.mpc4j.common.rpc.desc.PtoDesc;
import edu.alibaba.mpc4j.common.rpc.desc.PtoDescManager;

/**
 * IBLT-PSU protocol description. The protocol follows the IBLT-based PSU line from the paper:
 * <p>
 * Lucas Piske and Ni Trieu. Is PSI Really Faster Than PSU? Achieving Efficient PSU with Invertible Bloom Filters.
 * </p>
 *
 * @author donghai hou
 * @date 2026/06/02
 */
class IbltPsuPtoDesc implements PtoDesc {
    /**
     * protocol ID.
     */
    private static final int PTO_ID = Math.abs((int) -7812364983214567021L);
    /**
     * protocol name.
     */
    private static final String PTO_NAME = "IBLT_PSU";

    /**
     * protocol step.
     */
    enum PtoStep {
        /**
         * server sends IBLT hash key.
         */
        SERVER_SEND_IBLT_KEY,
        /**
         * client sends OT-encrypted singleton elements.
         */
        CLIENT_SEND_SINGLETON_ELEMENTS,
        /**
         * server sends OT-encrypted union-peel messages.
         */
        SERVER_SEND_UNION_PEEL_MESSAGES,
        /**
         * client sends peeled union elements.
         */
        CLIENT_SEND_PEELED_ELEMENTS,
        /**
         * server sends finish status.
         */
        SERVER_SEND_FINISH,
    }

    /**
     * singleton.
     */
    private static final IbltPsuPtoDesc INSTANCE = new IbltPsuPtoDesc();

    /**
     * private constructor.
     */
    private IbltPsuPtoDesc() {
        // empty
    }

    /**
     * Gets the singleton instance.
     *
     * @return the singleton instance.
     */
    static PtoDesc getInstance() {
        return INSTANCE;
    }

    static {
        PtoDescManager.registerPtoDesc(getInstance());
    }

    @Override
    public int getPtoId() {
        return PTO_ID;
    }

    @Override
    public String getPtoName() {
        return PTO_NAME;
    }
}
