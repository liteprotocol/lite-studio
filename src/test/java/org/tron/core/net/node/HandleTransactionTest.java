package org.tron.core.net.node;

import com.google.protobuf.ByteString;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.testng.collections.Lists;
import org.tron.common.application.Application;
import org.tron.common.application.ApplicationFactory;
import org.tron.common.application.TronApplicationContext;
import org.tron.common.overlay.discover.node.Node;
import org.tron.common.overlay.server.Channel;
import org.tron.common.overlay.server.ChannelManager;
import org.tron.common.overlay.server.SyncPool;
import org.tron.common.utils.FileUtil;
import org.tron.common.utils.ReflectUtils;
import org.tron.core.Constant;
import org.tron.core.capsule.TransactionCapsule;
import org.tron.core.config.DefaultConfig;
import org.tron.core.config.args.Args;
import org.tron.core.db.ByteArrayWrapper;
import org.tron.core.db.Manager;
import org.tron.core.net.message.TransactionMessage;
import org.tron.core.net.message.TransactionsMessage;
import org.tron.core.net.node.override.HandshakeHandlerTest;
import org.tron.core.net.node.override.PeerClientTest;
import org.tron.core.net.node.override.TronChannelInitializerTest;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.services.RpcApiService;
import org.tron.core.services.WitnessService;
import org.tron.protos.Contract.TransferContract;
import org.tron.protos.Protocol;
import org.tron.protos.Protocol.Inventory.InventoryType;
import org.tron.protos.Protocol.Transaction.Contract.ContractType;


@Slf4j
public class HandleTransactionTest {

    private static TronApplicationContext context;
    private static NodeImpl node;
    private RpcApiService rpcApiService;
    private static PeerClientTest peerClient;
    private ChannelManager channelManager;
    private SyncPool pool;
    private static Application appT;
    private static Manager dbManager;
    private Node nodeEntity;
    private static HandshakeHandlerTest handshakeHandlerTest;

    private static final String dbPath = "output-HandleTransactionTest";
    private static final String dbDirectory = "db_HandleTransaction_test";
    private static final String indexDirectory = "index_HandleTransaction_test";

    private static Boolean deleteFolder(File index) {
        if (!index.isDirectory() || index.listFiles().length <= 0) {
            return index.delete();
        }
        for (File file : index.listFiles()) {
            if (null != file && !deleteFolder(file)) {
                return false;
            }
        }
        return index.delete();
    }

    @Test
    public void testHandleTransactionMessage() throws Exception{

        TransferContract tc =
            TransferContract.newBuilder()
                .setAmount(10)
                .setOwnerAddress(ByteString.copyFromUtf8("aaa"))
                .setToAddress(ByteString.copyFromUtf8("bbb"))
                .build();

        TransactionCapsule trx = new TransactionCapsule(tc, ContractType.TransferContract);

        Protocol.Transaction transaction = trx.getInstance();

        TransactionMessage transactionMessage = new TransactionMessage(transaction);
        List list = Lists.newArrayList();
        list.add(transaction);
        TransactionsMessage transactionsMessage = new TransactionsMessage(list);

        PeerConnection peer = new PeerConnection();
        //?????????peer???????????????????????????
        peer.getAdvObjWeRequested().clear();
        peer.setSyncFlag(true);
        //nodeImpl.onMessage(peer, transactionMessage);
        //Assert.assertEquals(peer.getSyncFlag(), false);

        //???peer???????????????????????????
        peer.getAdvObjWeRequested().put(new Item(transactionMessage.getMessageId(), InventoryType.TRX), System.currentTimeMillis());
        peer.setSyncFlag(true);
        node.onMessage(peer, transactionsMessage);
        //Assert.assertEquals(peer.getAdvObjWeRequested().isEmpty(), true);
        //ConcurrentHashMap<Sha256Hash, InventoryType> advObjToSpread = ReflectUtils.getFieldValue(nodeImpl, "advObjToSpread");
        //Assert.assertEquals(advObjToSpread.contains(transactionMessage.getMessageId()), true);
    }

    private static boolean go = false;

    @Before
    public void init() {
        nodeEntity = new Node(
            "enode://e437a4836b77ad9d9ffe73ee782ef2614e6d8370fcf62191a6e488276e23717147073a7ce0b444d485fff5a0c34c4577251a7a990cf80d8542e21b95aa8c5e6c@127.0.0.1:17891");

        new Thread(new Runnable() {
            @Override
            public void run() {
                logger.info("Full node running.");
                Args.setParam(
                        new String[]{
                                "--output-directory", dbPath,
                                "--storage-db-directory", dbDirectory,
                                "--storage-index-directory", indexDirectory
                        },
                        Constant.TEST_CONF
                );
                Args cfgArgs = Args.getInstance();
                cfgArgs.setNodeListenPort(17891);
                cfgArgs.setNodeDiscoveryEnable(false);
                cfgArgs.getSeedNode().getIpList().clear();
                cfgArgs.setNeedSyncCheck(false);
                cfgArgs.setNodeExternalIp("127.0.0.1");

                context = new TronApplicationContext(DefaultConfig.class);

                if (cfgArgs.isHelp()) {
                    logger.info("Here is the help message.");
                    return;
                }
                appT = ApplicationFactory.create(context);
                rpcApiService = context.getBean(RpcApiService.class);
                appT.addService(rpcApiService);
                if (cfgArgs.isWitness()) {
                    appT.addService(new WitnessService(appT, context));
                }
//        appT.initServices(cfgArgs);
//        appT.startServices();
//        appT.startup();
                node = context.getBean(NodeImpl.class);
                peerClient = context.getBean(PeerClientTest.class);
                channelManager = context.getBean(ChannelManager.class);
                pool = context.getBean(SyncPool.class);
                dbManager = context.getBean(Manager.class);
                handshakeHandlerTest = context.getBean(HandshakeHandlerTest.class);
                handshakeHandlerTest.setNode(nodeEntity);
                NodeDelegate nodeDelegate = new NodeDelegateImpl(dbManager);
                node.setNodeDelegate(nodeDelegate);
                pool.init(node);
                prepare();
                rpcApiService.blockUntilShutdown();
            }
        }).start();
        int tryTimes = 0;
        while (tryTimes < 10 && (node == null || peerClient == null
                || channelManager == null || pool == null || !go)) {
            try {
                logger.info("node:{},peerClient:{},channelManager:{},pool:{},{}", node, peerClient,
                        channelManager, pool, go);
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                ++tryTimes;
            }
        }
    }

    private void prepare() {
        try {
            ExecutorService advertiseLoopThread = ReflectUtils.getFieldValue(node, "broadPool");
            advertiseLoopThread.shutdownNow();

            peerClient.prepare(nodeEntity.getHexId());

            ReflectUtils.setFieldValue(node, "isAdvertiseActive", false);
            ReflectUtils.setFieldValue(node, "isFetchActive", false);

            TronChannelInitializerTest tronChannelInitializer = ReflectUtils
                .getFieldValue(peerClient, "tronChannelInitializer");
            tronChannelInitializer.prepare();
            Channel channel = ReflectUtils.getFieldValue(tronChannelInitializer, "channel");
            ReflectUtils.setFieldValue(channel, "handshakeHandler", handshakeHandlerTest);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    peerClient.connect(nodeEntity.getHost(), nodeEntity.getPort(), nodeEntity.getHexId());
                }
            }).start();
            Thread.sleep(1000);
            Map<ByteArrayWrapper, Channel> activePeers = ReflectUtils
                    .getFieldValue(channelManager, "activePeers");
            int tryTimes = 0;
            while (MapUtils.isEmpty(activePeers) && ++tryTimes < 10) {
                Thread.sleep(1000);
            }
            go = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @AfterClass
    public static void destroy() {
        Args.clearParam();
        Collection<PeerConnection> peerConnections = ReflectUtils.invokeMethod(node, "getActivePeer");
        for (PeerConnection peer : peerConnections) {
            peer.close();
        }
        handshakeHandlerTest.close();
        appT.shutdownServices();
        appT.shutdown();
        context.destroy();
        dbManager.getSession().reset();
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        FileUtil.deleteDir(new File(dbPath));
    }
}
