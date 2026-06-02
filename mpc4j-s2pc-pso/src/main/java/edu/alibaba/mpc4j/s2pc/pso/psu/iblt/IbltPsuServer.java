package edu.alibaba.mpc4j.s2pc.pso.psu.iblt;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSender;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfSenderOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuServer;
import edu.alibaba.mpc4j.s2pc.pso.psu.iblt.IbltPsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * IBLT-PSU server.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class IbltPsuServer extends AbstractPsuServer {
    /**
     * MP-OPRF sender.
     */
    private final MpOprfSender mpOprfSender;
    /**
     * COT receiver for client singleton elements.
     */
    private final CoreCotReceiver singletonCotReceiver;
    /**
     * COT sender for union-peel messages.
     */
    private final CoreCotSender unionCotSender;
    /**
     * IBLT multiplier.
     */
    private final double ibltMultiplier;
    /**
     * protocol IBLT hash key.
     */
    private byte[] ibltHashKey;
    /**
     * server IBLT.
     */
    private H5LongIblt serverIblt;
    /**
     * server elements indexed by their byte representation.
     */
    private Set<ByteBuffer> serverElementSet;
    /**
     * remaining server-owned elements.
     */
    private Set<ByteBuffer> serverRemainSet;
    /**
     * OPRF sender output.
     */
    private MpOprfSenderOutput mpOprfSenderOutput;

    public IbltPsuServer(Rpc serverRpc, Party clientParty, IbltPsuConfig config) {
        super(IbltPsuPtoDesc.getInstance(), serverRpc, clientParty, config);
        mpOprfSender = OprfFactory.createMpOprfSender(serverRpc, clientParty, config.getMpOprfConfig());
        addSubPto(mpOprfSender);
        singletonCotReceiver = CoreCotFactory.createReceiver(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(singletonCotReceiver);
        unionCotSender = CoreCotFactory.createSender(serverRpc, clientParty, config.getCoreCotConfig());
        addSubPto(unionCotSender);
        ibltMultiplier = config.getIbltMultiplier();
    }

    @Override
    public void init(int maxServerElementSize, int maxClientElementSize) throws MpcAbortException {
        setInitInput(maxServerElementSize, maxClientElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        mpOprfSender.init(maxClientElementSize);
        singletonCotReceiver.init();
        unionCotSender.init(BlockUtils.randomBlock(secureRandom));
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public void psu(Set<ByteBuffer> serverElementSet, int clientElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(serverElementSet, clientElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        initProtocolState(serverElementSet);
        sendIbltHashKey();
        mpOprfSenderOutput = mpOprfSender.oprf(clientElementSize);
        stopWatch.stop();
        long initStateTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, initStateTime, "Server initializes IBLT and runs MP-OPRF");

        stopWatch.start();
        runUnionPeel();
        stopWatch.stop();
        long peelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, peelTime, "Server runs union peel");

        mpOprfSenderOutput = null;
        serverIblt = null;
        this.serverElementSet = null;
        serverRemainSet = null;
        logPhaseInfo(PtoState.PTO_END);
    }

    private void initProtocolState(Set<ByteBuffer> inputServerElementSet) {
        ibltHashKey = BlockUtils.randomBlock(secureRandom);
        int threshold = Math.addExact(serverElementSize, clientElementSize);
        serverIblt = IbltPsuUtils.createIblt(envType, threshold, ibltMultiplier, elementByteLength, ibltHashKey);
        serverElementSet = new HashSet<>(serverElementSize);
        serverRemainSet = new HashSet<>(serverElementSize);
        for (ByteBuffer element : inputServerElementSet) {
            byte[] elementBytes = BytesUtils.clone(element.array());
            ByteBuffer elementBuffer = ByteBuffer.wrap(elementBytes);
            serverElementSet.add(elementBuffer);
            serverRemainSet.add(ByteBuffer.wrap(BytesUtils.clone(elementBytes)));
            serverIblt.add(IbltPsuUtils.elementKey(envType, elementBytes), elementBytes);
        }
    }

    private void sendIbltHashKey() {
        List<byte[]> keyPayload = new ArrayList<>(1);
        keyPayload.add(ibltHashKey);
        DataPacketHeader keyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_IBLT_KEY.ordinal(), extraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(keyHeader, keyPayload));
    }

    private void runUnionPeel() throws MpcAbortException {
        boolean[] peeled = new boolean[serverIblt.tableLength()];
        int maxRound = Math.max(1, serverIblt.tableLength());
        long nextExtraInfo = extraInfo;
        try {
            int[] probeIndexes = IbltPsuUtils.allUnpeeledPositions(peeled);
            for (int round = 0; round <= maxRound; round++) {
                long roundExtraInfo = nextExtraInfo++;
                if (probeIndexes.length == 0) {
                    sendFinish(roundExtraInfo);
                    return;
                }
                byte[][] clientSingletonElements = receiveClientSingletonElements(roundExtraInfo, probeIndexes);
                sendUnionPeelMessages(roundExtraInfo, round, probeIndexes, clientSingletonElements);
                List<byte[]> peeledPayload = receivePeeledElements(roundExtraInfo);
                if (peeledPayload.isEmpty()) {
                    sendFinish(roundExtraInfo);
                    return;
                }
                handlePeeledElements(peeledPayload, peeled);
                probeIndexes = nextProbeIndexes(peeledPayload, peeled);
            }
            throw new MpcAbortException("IBLT union peel exceeds the maximum round number");
        } finally {
            extraInfo = nextExtraInfo;
        }
    }

    private byte[][] receiveClientSingletonElements(long roundExtraInfo, int[] probeIndexes) throws MpcAbortException {
        int probeNum = probeIndexes.length;
        int[] counts = serverIblt.counts();
        boolean[] choices = new boolean[probeNum];
        for (int i = 0; i < probeNum; i++) {
            choices[i] = counts[probeIndexes[i]] == 0;
        }
        CotReceiverOutput cotReceiverOutput = singletonCotReceiver.receive(choices);
        DataPacketHeader singletonHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_SINGLETON_ELEMENTS.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> singletonPayload = rpc.receive(singletonHeader).getPayload();
        return IbltPsuUtils.handleCotPayload(
            envType, cotReceiverOutput, singletonPayload, Byte.BYTES + elementByteLength
        );
    }

    private void sendUnionPeelMessages(long roundExtraInfo, int round, int[] probeIndexes,
                                       byte[][] clientSingletonElements)
        throws MpcAbortException {
        int probeNum = probeIndexes.length;
        MathPreconditions.checkEqual(
            "clientSingletonElements.length", "probeNum", clientSingletonElements.length, probeNum
        );
        int[] counts = serverIblt.counts();
        byte[][] values = serverIblt.valueSums();
        int messageByteLength = Math.max(Byte.BYTES + elementByteLength, CommonConstants.BLOCK_BYTE_LENGTH);
        byte[][] message0 = new byte[probeNum][messageByteLength];
        byte[][] message1 = new byte[probeNum][messageByteLength];
        for (int i = 0; i < probeNum; i++) {
            int index = probeIndexes[i];
            if (counts[index] == 1) {
                message0[i] = IbltPsuUtils.pad(
                    IbltPsuUtils.encodeOptionalElement(values[index], elementByteLength), messageByteLength
                );
                byte[] seed = IbltPsuUtils.seed(envType, mpOprfSenderOutput.getPrf(values[index]));
                message1[i] = IbltPsuUtils.seedTag(envType, seed, round, index);
            } else {
                byte[] clientElement = IbltPsuUtils.decodeOptionalElement(
                    clientSingletonElements[i], elementByteLength
                );
                if (clientElement == null) {
                    continue;
                }
                byte[] seed = IbltPsuUtils.seed(envType, mpOprfSenderOutput.getPrf(clientElement));
                message1[i] = IbltPsuUtils.seedTag(envType, seed, round, index);
            }
        }
        CotSenderOutput cotSenderOutput = unionCotSender.send(probeNum);
        List<byte[]> unionPayload = IbltPsuUtils.generateCotPayload(
            envType, cotSenderOutput, message0, message1, messageByteLength
        );
        DataPacketHeader unionHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_UNION_PEEL_MESSAGES.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(unionHeader, unionPayload));
    }

    private List<byte[]> receivePeeledElements(long roundExtraInfo) {
        DataPacketHeader peeledHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PEELED_ELEMENTS.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        return rpc.receive(peeledHeader).getPayload();
    }

    private void handlePeeledElements(List<byte[]> peeledPayload, boolean[] peeled) throws MpcAbortException {
        for (byte[] encoded : peeledPayload) {
            int index = IbltPsuUtils.decodePeeledIndex(encoded);
            MpcAbortPreconditions.checkArgument(index >= 0 && index < peeled.length);
            peeled[index] = true;
            byte[] element = IbltPsuUtils.decodePeeledElement(encoded, elementByteLength);
            ByteBuffer elementBuffer = ByteBuffer.wrap(element);
            if (serverElementSet.contains(elementBuffer) && serverRemainSet.remove(elementBuffer)) {
                serverIblt.remove(IbltPsuUtils.elementKey(envType, element), element);
            }
        }
    }

    private int[] nextProbeIndexes(List<byte[]> peeledPayload, boolean[] peeled) {
        long[] peeledKeys = IbltPsuUtils.peeledElementKeys(envType, peeledPayload, elementByteLength);
        return serverIblt.uniquePositions(peeledKeys, peeled);
    }

    private void sendFinish(long roundExtraInfo) throws MpcAbortException {
        boolean success = serverRemainSet.isEmpty();
        List<byte[]> finishPayload = new ArrayList<>(1);
        finishPayload.add(new byte[]{(byte) (success ? 1 : 0)});
        DataPacketHeader finishHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_FINISH.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(finishHeader, finishPayload));
        MpcAbortPreconditions.checkArgument(success);
    }
}
