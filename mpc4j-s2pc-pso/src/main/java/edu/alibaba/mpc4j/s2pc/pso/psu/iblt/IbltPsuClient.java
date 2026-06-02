package edu.alibaba.mpc4j.s2pc.pso.psu.iblt;

import edu.alibaba.mpc4j.common.rpc.*;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacket;
import edu.alibaba.mpc4j.common.rpc.utils.DataPacketHeader;
import edu.alibaba.mpc4j.common.structure.iblt.H5LongIblt;
import edu.alibaba.mpc4j.common.tool.CommonConstants;
import edu.alibaba.mpc4j.common.tool.MathPreconditions;
import edu.alibaba.mpc4j.common.tool.utils.BlockUtils;
import edu.alibaba.mpc4j.common.tool.utils.BytesUtils;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiver;
import edu.alibaba.mpc4j.s2pc.opf.oprf.MpOprfReceiverOutput;
import edu.alibaba.mpc4j.s2pc.opf.oprf.OprfFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotReceiverOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.CotSenderOutput;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotFactory;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotReceiver;
import edu.alibaba.mpc4j.s2pc.pcg.ot.cot.core.CoreCotSender;
import edu.alibaba.mpc4j.s2pc.pso.psu.AbstractPsuClient;
import edu.alibaba.mpc4j.s2pc.pso.psu.PsuClientOutput;
import edu.alibaba.mpc4j.s2pc.pso.psu.iblt.IbltPsuPtoDesc.PtoStep;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * IBLT-PSU client.
 *
 * @author donghai hou
 * @date 2026/06/02
 */
public class IbltPsuClient extends AbstractPsuClient {
    /**
     * MP-OPRF receiver.
     */
    private final MpOprfReceiver mpOprfReceiver;
    /**
     * COT sender for client singleton elements.
     */
    private final CoreCotSender singletonCotSender;
    /**
     * COT receiver for union-peel messages.
     */
    private final CoreCotReceiver unionCotReceiver;
    /**
     * IBLT multiplier.
     */
    private final double ibltMultiplier;
    /**
     * element IBLT.
     */
    private H5LongIblt clientIblt;
    /**
     * OPRF seed IBLT.
     */
    private H5LongIblt clientSeedIblt;
    /**
     * client elements.
     */
    private Set<ByteBuffer> clientElementSet;
    /**
     * remaining client-owned elements.
     */
    private Set<ByteBuffer> clientRemainSet;
    /**
     * client OPRF seeds.
     */
    private Map<ByteBuffer, byte[]> clientSeedMap;

    public IbltPsuClient(Rpc clientRpc, Party serverParty, IbltPsuConfig config) {
        super(IbltPsuPtoDesc.getInstance(), clientRpc, serverParty, config);
        mpOprfReceiver = OprfFactory.createMpOprfReceiver(clientRpc, serverParty, config.getMpOprfConfig());
        addSubPto(mpOprfReceiver);
        singletonCotSender = CoreCotFactory.createSender(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(singletonCotSender);
        unionCotReceiver = CoreCotFactory.createReceiver(clientRpc, serverParty, config.getCoreCotConfig());
        addSubPto(unionCotReceiver);
        ibltMultiplier = config.getIbltMultiplier();
    }

    @Override
    public void init(int maxClientElementSize, int maxServerElementSize) throws MpcAbortException {
        setInitInput(maxClientElementSize, maxServerElementSize);
        logPhaseInfo(PtoState.INIT_BEGIN);

        stopWatch.start();
        mpOprfReceiver.init(maxClientElementSize);
        singletonCotSender.init(BlockUtils.randomBlock(secureRandom));
        unionCotReceiver.init();
        stopWatch.stop();
        long initTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.INIT_STEP, 1, 1, initTime);

        logPhaseInfo(PtoState.INIT_END);
    }

    @Override
    public PsuClientOutput psu(Set<ByteBuffer> clientElementSet, int serverElementSize, int elementByteLength)
        throws MpcAbortException {
        setPtoInput(clientElementSet, serverElementSize, elementByteLength);
        logPhaseInfo(PtoState.PTO_BEGIN);

        stopWatch.start();
        initProtocolState(clientElementSet);
        stopWatch.stop();
        long initStateTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 1, 2, initStateTime, "Client initializes OPRF seeds and IBLT");

        stopWatch.start();
        Set<ByteBuffer> union = runUnionPeel();
        int psica = serverElementSize + clientElementSize - union.size();
        stopWatch.stop();
        long peelTime = stopWatch.getTime(TimeUnit.MILLISECONDS);
        stopWatch.reset();
        logStepInfo(PtoState.PTO_STEP, 2, 2, peelTime, "Client runs union peel");

        clientIblt = null;
        clientSeedIblt = null;
        this.clientElementSet = null;
        clientRemainSet = null;
        clientSeedMap = null;
        logPhaseInfo(PtoState.PTO_END);
        return new PsuClientOutput(union, psica);
    }

    private void initProtocolState(Set<ByteBuffer> inputClientElementSet) throws MpcAbortException {
        byte[] ibltHashKey = receiveIbltHashKey();
        byte[][] clientInputs = clientElementArrayList.stream()
            .map(ByteBuffer::array)
            .map(BytesUtils::clone)
            .toArray(byte[][]::new);
        MpOprfReceiverOutput mpOprfReceiverOutput = mpOprfReceiver.oprf(clientInputs);
        int threshold = Math.addExact(serverElementSize, clientElementSize);
        clientIblt = IbltPsuUtils.createIblt(envType, threshold, ibltMultiplier, elementByteLength, ibltHashKey);
        clientSeedIblt = IbltPsuUtils.createIblt(
            envType, threshold, ibltMultiplier, CommonConstants.BLOCK_BYTE_LENGTH, ibltHashKey
        );
        clientElementSet = new HashSet<>(clientElementSize);
        clientRemainSet = new HashSet<>(clientElementSize);
        clientSeedMap = new HashMap<>(clientElementSize);
        for (int index = 0; index < clientInputs.length; index++) {
            byte[] element = clientInputs[index];
            byte[] seed = IbltPsuUtils.seed(envType, mpOprfReceiverOutput.getPrf(index));
            long key = IbltPsuUtils.elementKey(envType, element);
            clientIblt.add(key, element);
            clientSeedIblt.add(key, seed);
            ByteBuffer elementBuffer = ByteBuffer.wrap(element);
            clientElementSet.add(elementBuffer);
            clientRemainSet.add(ByteBuffer.wrap(BytesUtils.clone(element)));
            clientSeedMap.put(ByteBuffer.wrap(BytesUtils.clone(element)), seed);
        }
    }

    private byte[] receiveIbltHashKey() throws MpcAbortException {
        DataPacketHeader keyHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_IBLT_KEY.ordinal(), extraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> keyPayload = rpc.receive(keyHeader).getPayload();
        MpcAbortPreconditions.checkArgument(keyPayload.size() == 1);
        MpcAbortPreconditions.checkArgument(keyPayload.get(0).length == CommonConstants.BLOCK_BYTE_LENGTH);
        return keyPayload.get(0);
    }

    private Set<ByteBuffer> runUnionPeel() throws MpcAbortException {
        Set<ByteBuffer> union = new HashSet<>(serverElementSize + clientElementSize);
        boolean[] peeled = new boolean[clientIblt.tableLength()];
        int maxRound = Math.max(1, clientIblt.tableLength());
        long nextExtraInfo = extraInfo;
        try {
            int[] probeIndexes = IbltPsuUtils.allUnpeeledPositions(peeled);
            for (int round = 0; round <= maxRound; round++) {
                long roundExtraInfo = nextExtraInfo++;
                if (probeIndexes.length == 0) {
                    receiveFinish(roundExtraInfo);
                    union.addAll(clientElementSet);
                    return union;
                }
                sendClientSingletonElements(roundExtraInfo, probeIndexes);
                byte[][] unionMessages = receiveUnionPeelMessages(roundExtraInfo, probeIndexes);
                List<byte[]> peeledPayload = handleUnionMessages(round, probeIndexes, unionMessages, union, peeled);
                sendPeeledElements(roundExtraInfo, peeledPayload);
                if (peeledPayload.isEmpty()) {
                    receiveFinish(roundExtraInfo);
                    union.addAll(clientElementSet);
                    return union;
                }
                probeIndexes = nextProbeIndexes(peeledPayload, peeled);
            }
            throw new MpcAbortException("IBLT union peel exceeds the maximum round number");
        } finally {
            extraInfo = nextExtraInfo;
        }
    }

    private void sendClientSingletonElements(long roundExtraInfo, int[] probeIndexes) throws MpcAbortException {
        int probeNum = probeIndexes.length;
        int[] counts = clientIblt.counts();
        byte[][] values = clientIblt.valueSums();
        byte[][] message0 = new byte[probeNum][elementByteLength];
        byte[][] message1 = new byte[probeNum][elementByteLength];
        for (int i = 0; i < probeNum; i++) {
            int index = probeIndexes[i];
            if (counts[index] == 1) {
                message1[i] = IbltPsuUtils.encodeOptionalElement(values[index], elementByteLength);
            }
        }
        CotSenderOutput cotSenderOutput = singletonCotSender.send(probeNum);
        List<byte[]> singletonPayload = IbltPsuUtils.generateCotPayload(
            envType, cotSenderOutput, message0, message1, Byte.BYTES + elementByteLength
        );
        DataPacketHeader singletonHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_SINGLETON_ELEMENTS.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(singletonHeader, singletonPayload));
    }

    private byte[][] receiveUnionPeelMessages(long roundExtraInfo, int[] probeIndexes) throws MpcAbortException {
        int probeNum = probeIndexes.length;
        int[] counts = clientIblt.counts();
        boolean[] choices = new boolean[probeNum];
        for (int i = 0; i < probeNum; i++) {
            choices[i] = counts[probeIndexes[i]] == 1;
        }
        CotReceiverOutput cotReceiverOutput = unionCotReceiver.receive(choices);
        DataPacketHeader unionHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_UNION_PEEL_MESSAGES.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> unionPayload = rpc.receive(unionHeader).getPayload();
        int messageByteLength = Math.max(Byte.BYTES + elementByteLength, CommonConstants.BLOCK_BYTE_LENGTH);
        return IbltPsuUtils.handleCotPayload(envType, cotReceiverOutput, unionPayload, messageByteLength);
    }

    private List<byte[]> handleUnionMessages(int round, int[] probeIndexes, byte[][] unionMessages,
                                             Set<ByteBuffer> union, boolean[] peeled) {
        int[] counts = clientIblt.counts();
        byte[][] values = clientIblt.valueSums();
        byte[][] seedValues = clientSeedIblt.valueSums();
        List<byte[]> peeledPayload = new ArrayList<>();
        for (int i = 0; i < probeIndexes.length; i++) {
            int index = probeIndexes[i];
            byte[] element = null;
            if (counts[index] == 0) {
                element = IbltPsuUtils.decodeOptionalElement(unionMessages[i], elementByteLength);
            } else if (counts[index] == 1) {
                byte[] expectTag = IbltPsuUtils.seedTag(envType, seedValues[index], round, index);
                byte[] actualTag = Arrays.copyOf(unionMessages[i], CommonConstants.BLOCK_BYTE_LENGTH);
                if (Arrays.equals(expectTag, actualTag)) {
                    element = values[index];
                }
            }
            if (element != null) {
                peeled[index] = true;
                union.add(ByteBuffer.wrap(BytesUtils.clone(element)));
                peeledPayload.add(IbltPsuUtils.encodePeeledElement(index, element));
                removeClientElement(element);
            }
        }
        return peeledPayload;
    }

    private void removeClientElement(byte[] element) {
        ByteBuffer elementBuffer = ByteBuffer.wrap(element);
        if (clientElementSet.contains(elementBuffer) && clientRemainSet.remove(elementBuffer)) {
            byte[] seed = clientSeedMap.get(elementBuffer);
            long key = IbltPsuUtils.elementKey(envType, element);
            clientIblt.remove(key, element);
            clientSeedIblt.remove(key, seed);
        }
    }

    private int[] nextProbeIndexes(List<byte[]> peeledPayload, boolean[] peeled) {
        long[] peeledKeys = IbltPsuUtils.peeledElementKeys(envType, peeledPayload, elementByteLength);
        return clientIblt.uniquePositions(peeledKeys, peeled);
    }

    private void sendPeeledElements(long roundExtraInfo, List<byte[]> peeledPayload) {
        DataPacketHeader peeledHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.CLIENT_SEND_PEELED_ELEMENTS.ordinal(), roundExtraInfo,
            ownParty().getPartyId(), otherParty().getPartyId()
        );
        rpc.send(DataPacket.fromByteArrayList(peeledHeader, peeledPayload));
    }

    private void receiveFinish(long roundExtraInfo) throws MpcAbortException {
        DataPacketHeader finishHeader = new DataPacketHeader(
            encodeTaskId, getPtoDesc().getPtoId(), PtoStep.SERVER_SEND_FINISH.ordinal(), roundExtraInfo,
            otherParty().getPartyId(), ownParty().getPartyId()
        );
        List<byte[]> finishPayload = rpc.receive(finishHeader).getPayload();
        MpcAbortPreconditions.checkArgument(finishPayload.size() == 1);
        MpcAbortPreconditions.checkArgument(finishPayload.get(0).length == 1 && finishPayload.get(0)[0] == 1);
        MpcAbortPreconditions.checkArgument(clientRemainSet.isEmpty());
    }
}
