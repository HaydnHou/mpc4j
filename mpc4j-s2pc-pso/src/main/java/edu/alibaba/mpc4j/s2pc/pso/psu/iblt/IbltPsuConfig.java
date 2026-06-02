package edu.alibaba.mpc4j.s2pc.pso.psu.iblt;

import com.google.common.base.Preconditions;
import edu.alibaba.mpc4j.common.rpc.desc.SecurityModel;
import edu.alibaba.mpc4j.common.rpc.pto.AbstractMultiPartyPtoConfig;
import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.opf.oprf.rs21.Rs21MpOprfConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotConfig;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuConfig;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuFactory.PsuType;

/**
 * IBLT-PSU config.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class IbltPsuConfig extends AbstractMultiPartyPtoConfig implements PsuConfig {
    /**
     * MP-OPRF config.
     */
    private final MpOprfConfig mpOprfConfig;
    /**
     * core COT config.
     */
    private final CoreCotConfig coreCotConfig;
    /**
     * IBLT table length multiplier.
     */
    private final double ibltMultiplier;

    private IbltPsuConfig(Builder builder) {
        super(SecurityModel.SEMI_HONEST, builder.mpOprfConfig, builder.coreCotConfig);
        mpOprfConfig = builder.mpOprfConfig;
        coreCotConfig = builder.coreCotConfig;
        ibltMultiplier = builder.ibltMultiplier;
    }

    @Override
    public PsuType getPtoType() {
        return PsuType.IBLT;
    }

    /**
     * Gets the MP-OPRF config.
     *
     * @return the MP-OPRF config.
     */
    public MpOprfConfig getMpOprfConfig() {
        return mpOprfConfig;
    }

    /**
     * Gets the core COT config.
     *
     * @return the core COT config.
     */
    public CoreCotConfig getCoreCotConfig() {
        return coreCotConfig;
    }

    /**
     * Gets the IBLT multiplier.
     *
     * @return the IBLT multiplier.
     */
    public double getIbltMultiplier() {
        return ibltMultiplier;
    }

    /**
     * Builder.
     */
    public static class Builder implements org.apache.commons.lang3.builder.Builder<IbltPsuConfig> {
        /**
         * MP-OPRF config.
         */
        private MpOprfConfig mpOprfConfig;
        /**
         * core COT config.
         */
        private CoreCotConfig coreCotConfig;
        /**
         * IBLT table length multiplier.
         */
        private double ibltMultiplier;

        public Builder() {
            mpOprfConfig = new Rs21MpOprfConfig.Builder(SecurityModel.SEMI_HONEST).build();
            coreCotConfig = CoreCotFactory.createDefaultConfig(SecurityModel.SEMI_HONEST);
            ibltMultiplier = H5LongIblt.DEFAULT_MULTIPLIER;
        }

        public Builder setMpOprfConfig(MpOprfConfig mpOprfConfig) {
            this.mpOprfConfig = mpOprfConfig;
            return this;
        }

        public Builder setCoreCotConfig(CoreCotConfig coreCotConfig) {
            this.coreCotConfig = coreCotConfig;
            return this;
        }

        public Builder setIbltMultiplier(double ibltMultiplier) {
            Preconditions.checkArgument(Double.isFinite(ibltMultiplier));
            Preconditions.checkArgument(ibltMultiplier > 0);
            this.ibltMultiplier = ibltMultiplier;
            return this;
        }

        @Override
        public IbltPsuConfig build() {
            return new IbltPsuConfig(this);
        }
    }
}
