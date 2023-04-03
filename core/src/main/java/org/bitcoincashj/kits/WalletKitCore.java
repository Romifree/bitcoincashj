package org.bitcoincashj.kits;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.*;
import org.bitcoincashj.core.*;
import org.bitcoincashj.core.listeners.DownloadProgressTracker;
import org.bitcoincashj.core.slp.*;
import org.bitcoincashj.core.slp.nft.NonFungibleSlpToken;
import org.bitcoincashj.core.slp.opreturn.NftOpReturnOutputGenesis;
import org.bitcoincashj.core.slp.opreturn.SlpOpReturnOutputGenesis;
import org.bitcoincashj.crypto.DeterministicKey;
import org.bitcoincashj.net.*;
import org.bitcoincashj.net.discovery.DnsDiscovery;
import org.bitcoincashj.net.discovery.PeerDiscovery;
import org.bitcoincashj.net.discovery.SeedPeers;
import org.bitcoincashj.protocols.payments.slp.SlpPaymentSession;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.store.BlockStore;
import org.bitcoincashj.store.BlockStoreException;
import org.bitcoincashj.store.SPVBlockStore;
import org.bitcoincashj.wallet.*;
import org.bouncycastle.crypto.params.KeyParameter;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class WalletKitCore extends AbstractIdleService {
    protected static final Logger log = LoggerFactory.getLogger(WalletAppKit.class);
    protected volatile Context context;
    protected NetworkParameters params;
    protected Script.ScriptType preferredOutputScriptType;
    protected KeyChainGroupStructure structure;

    protected WalletProtobufSerializer.WalletFactory walletFactory;
    @Nullable
    protected DeterministicSeed restoreFromSeed;
    @Nullable
    protected DeterministicKey restoreFromKey;
    protected File directory;
    protected volatile File vWalletFile;
    protected String filePrefix;

    protected boolean useAutoSave = true;
    protected DownloadProgressTracker downloadListener;
    protected boolean blockingStartup = true;
    protected boolean autoStop = true;
    protected InputStream checkpoints;
    protected String userAgent, version;
    protected PeerAddress[] peerAddresses;
    protected volatile BlockChain vChain;
    protected volatile SPVBlockStore vStore;
    protected volatile Wallet vWallet;
    protected volatile PeerGroup vPeerGroup;
    @Nullable
    protected PeerDiscovery discovery;

    public boolean useTor = false;
    public String torProxyIp = "127.0.0.1";
    public String torProxyPort = "9050";

    /** SLP common stuff **/
    protected File tokensFile;
    protected File nftsFile;
    protected ArrayList<SlpUTXO> slpUtxos = new ArrayList<>();
    protected ArrayList<SlpToken> slpTokens = new ArrayList<>();
    protected ArrayList<SlpTokenBalance> slpBalances = new ArrayList<>();
    protected ArrayList<String> verifiedSlpTxs = new ArrayList<>();
    protected ArrayList<SlpUTXO> nftUtxos = new ArrayList<>();
    protected ArrayList<NonFungibleSlpToken> nfts = new ArrayList<>();
    protected ArrayList<SlpTokenBalance> nftBalances = new ArrayList<>();
    protected ArrayList<SlpUTXO> nftParentUtxos = new ArrayList<>();
    protected ArrayList<SlpTokenBalance> nftParentBalances = new ArrayList<>();

    protected SlpDbProcessor slpDbProcessor;
    protected boolean recalculatingTokens = false;
    protected boolean recalculatingNfts = false;

    /**
     * Sets a wallet factory which will be used when the kit creates a new wallet.
     */
    public WalletKitCore setWalletFactory(WalletProtobufSerializer.WalletFactory walletFactory) {
        this.walletFactory = walletFactory;
        return this;
    }

    /**
     * If a seed is set here then any existing wallet that matches the file name will be renamed to a backup name,
     * the chain file will be deleted, and the wallet object will be instantiated with the given seed instead of
     * a fresh one being created. This is intended for restoring a wallet from the original seed. To implement restore
     * you would shut down the existing appkit, if any, then recreate it with the seed given by the user, then start
     * up the new kit. The next time your app starts it should work as normal (that is, don't keep calling this each
     * time).
     */
    public WalletKitCore restoreWalletFromSeed(DeterministicSeed seed) {
        this.restoreFromSeed = seed;
        return this;
    }

    /**
     * If an account key is set here then any existing wallet that matches the file name will be renamed to a backup name,
     * the chain file will be deleted, and the wallet object will be instantiated with the given key instead of
     * a fresh seed being created. This is intended for restoring a wallet from an account key. To implement restore
     * you would shut down the existing appkit, if any, then recreate it with the key given by the user, then start
     * up the new kit. The next time your app starts it should work as normal (that is, don't keep calling this each
     * time).
     */
    public WalletKitCore restoreWalletFromKey(DeterministicKey accountKey) {
        this.restoreFromKey = accountKey;
        return this;
    }

    /**
     * <p>Override this to return wallet extensions if any are necessary.</p>
     *
     * <p>When this is called, chain(), store(), and peerGroup() will return the created objects, however they are not
     * initialized/started.</p>
     */
    protected List<WalletExtension> provideWalletExtensions() throws Exception {
        return ImmutableList.of();
    }

    public ArrayList<SlpTokenBalance> getSlpBalances() {
        return this.slpBalances;
    }

    public ArrayList<SlpTokenBalance> getNftBalances() {
        return this.nftBalances;
    }

    public ArrayList<SlpUTXO> getNftParentUtxos() {
        return this.nftParentUtxos;
    }

    public ArrayList<SlpTokenBalance> getNftParentBalances() {
        return this.nftParentBalances;
    }

    public ArrayList<SlpToken> getSlpTokens() {
        return this.slpTokens;
    }

    public ArrayList<SlpUTXO> getSlpUtxos() {
        return this.slpUtxos;
    }

    public ArrayList<SlpUTXO> getNftUtxos() {
        return this.nftUtxos;
    }

    public SlpAddress currentSlpReceiveAddress() {
        return this.wallet().currentReceiveAddress().toSlp();
    }

    public SlpAddress currentSlpChangeAddress() {
        return this.wallet().currentChangeAddress().toSlp();
    }

    public SlpAddress freshSlpReceiveAddress() {
        return this.wallet().freshReceiveAddress().toSlp();
    }

    public SlpAddress freshSlpChangeAddress() {
        return this.wallet().freshChangeAddress().toSlp();
    }

    public SlpToken getSlpToken(String tokenId) {
        for (SlpToken slpToken : this.slpTokens) {
            String slpTokenTokenId = slpToken.getTokenId();
            if (slpTokenTokenId != null) {
                if (slpTokenTokenId.equals(tokenId)) {
                    return slpToken;
                }
            }
        }

        return null;
    }

    public NonFungibleSlpToken getNft(String tokenId) {
        for (NonFungibleSlpToken slpToken : this.nfts) {
            String slpTokenTokenId = slpToken.getTokenId();
            if (slpTokenTokenId != null) {
                if (slpTokenTokenId.equals(tokenId)) {
                    return slpToken;
                }
            }
        }

        return null;
    }

    public NetworkParameters params() {
        return params;
    }

    public void setUseTor(boolean status) {
        this.useTor = status;
    }

    public void setTorProxyIp(String ip) {
        this.torProxyIp = ip;
    }

    public void setTorProxyPort(String port) {
        this.torProxyPort = port;
    }

    public BlockChain chain() {
        checkState(state() == Service.State.STARTING || state() == Service.State.RUNNING, "Cannot call until startup is complete");
        return vChain;
    }

    public BlockStore store() {
        checkState(state() == Service.State.STARTING || state() == Service.State.RUNNING, "Cannot call until startup is complete");
        return vStore;
    }

    public Wallet wallet() {
        checkState(state() == Service.State.STARTING || state() == Service.State.RUNNING, "Cannot call until startup is complete");
        return vWallet;
    }

    public PeerGroup peerGroup() {
        checkState(state() == Service.State.STARTING || state() == Service.State.RUNNING, "Cannot call until startup is complete");
        return vPeerGroup;
    }

    public File directory() {
        return directory;
    }

    /**
     * Will only connect to the given addresses. Cannot be called after startup.
     */
    public WalletKitCore setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
        return this;
    }

    /**
     * Will only connect to localhost. Cannot be called after startup.
     */
    public WalletKitCore connectToLocalHost() {
        try {
            final InetAddress localHost = InetAddress.getLocalHost();
            return setPeerNodes(new PeerAddress(params, localHost, params.getPort()));
        } catch (UnknownHostException e) {
            // Borked machine with no loopback adapter configured properly.
            throw new RuntimeException(e);
        }
    }

    /**
     * If true, the wallet will save itself to disk automatically whenever it changes.
     */
    public WalletKitCore setAutoSave(boolean value) {
        checkState(state() == State.NEW, "Cannot call after startup");
        useAutoSave = value;
        return this;
    }

    /**
     * If you want to learn about the sync process, you can provide a listener here. For instance, a
     * {@link DownloadProgressTracker} is a good choice. This has no effect unless setBlockingStartup(false) has been called
     * too, due to some missing implementation code.
     */
    public WalletKitCore setDownloadListener(DownloadProgressTracker listener) {
        this.downloadListener = listener;
        return this;
    }

    /**
     * If true, will register a shutdown hook to stop the library. Defaults to true.
     */
    public WalletKitCore setAutoStop(boolean autoStop) {
        this.autoStop = autoStop;
        return this;
    }

    /**
     * If set, the file is expected to contain a checkpoints file calculated with BuildCheckpoints. It makes initial
     * block sync faster for new users - please refer to the documentation on the bitcoincashj website
     * (https://bitcoincashj.github.io/speeding-up-chain-sync) for further details.
     */
    public WalletKitCore setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Closeables.closeQuietly(checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
        return this;
    }

    /**
     * If true (the default) then the startup of this service won't be considered complete until the network has been
     * brought up, peer connections established and the block chain synchronised. Therefore {@link #awaitRunning()} can
     * potentially take a very long time. If false, then startup is considered complete once the network activity
     * begins and peer connections/block chain sync will continue in the background.
     */
    public WalletKitCore setBlockingStartup(boolean blockingStartup) {
        this.blockingStartup = blockingStartup;
        return this;
    }

    /**
     * Sets the string that will appear in the subver field of the version message.
     *
     * @param userAgent A short string that should be the name of your app, e.g. "My Wallet"
     * @param version   A short string that contains the version number, e.g. "1.0-BETA"
     */
    public WalletKitCore setUserAgent(String userAgent, String version) {
        this.userAgent = checkNotNull(userAgent);
        this.version = checkNotNull(version);
        return this;
    }

    /**
     * Sets the peer discovery class to use. If none is provided then DNS is used, which is a reasonable default.
     */
    public WalletKitCore setDiscovery(@Nullable PeerDiscovery discovery) {
        this.discovery = discovery;
        return this;
    }

    /**
     * Tests to see if the spvchain file has an operating system file lock on it. Useful for checking if your app
     * is already running. If another copy of your app is running and you start the appkit anyway, an exception will
     * be thrown during the startup process. Returns false if the chain file does not exist or is a directory.
     */
    public boolean isChainFileLocked() throws IOException {
        RandomAccessFile file2 = null;
        try {
            File file = new File(directory, filePrefix + ".spvchain");
            if (!file.exists())
                return false;
            if (file.isDirectory())
                return false;
            file2 = new RandomAccessFile(file, "rw");
            FileLock lock = file2.getChannel().tryLock();
            if (lock == null)
                return true;
            lock.release();
            return false;
        } finally {
            if (file2 != null)
                file2.close();
        }
    }

    @Override
    protected void startUp() throws Exception {
        // Runs in a separate thread.
        Context.propagate(context);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        log.info("Starting up with directory = {}", directory);
        try {
            File chainFile = new File(directory, filePrefix + ".spvchain");
            boolean chainFileExists = chainFile.exists();
            vWalletFile = new File(directory, filePrefix + ".wallet");
            boolean shouldReplayWallet = (vWalletFile.exists() && !chainFileExists) || restoreFromSeed != null || restoreFromKey != null;
            vWallet = createOrLoadWallet(shouldReplayWallet);

            // Initiate Bitcoin network objects (block store, blockchain and peer group)
            vStore = new SPVBlockStore(params, chainFile);
            if (!chainFileExists || restoreFromSeed != null || restoreFromKey != null) {
                if (checkpoints == null && !Utils.isAndroidRuntime()) {
                    checkpoints = CheckpointManager.openStream(params);
                }

                if (checkpoints != null) {
                    // Initialize the chain file with a checkpoint to speed up first-run sync.
                    long time;
                    if (restoreFromSeed != null) {
                        time = restoreFromSeed.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Clearing the chain file in preparation for restore.");
                            vStore.clear();
                        }
                    } else if (restoreFromKey != null) {
                        time = restoreFromKey.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Clearing the chain file in preparation for restore.");
                            vStore.clear();
                        }
                    } else {
                        time = vWallet.getEarliestKeyCreationTime();
                    }
                    if (time > 0)
                        CheckpointManager.checkpoint(params, checkpoints, vStore, time);
                    else
                        log.warn("Creating a new uncheckpointed block store due to a wallet with a creation time of zero: this will result in a very slow chain sync");
                } else if (chainFileExists) {
                    log.info("Clearing the chain file in preparation for restore.");
                    vStore.clear();
                }
            }
            vChain = new BlockChain(params, vStore);
            vPeerGroup = createPeerGroup();
            if (this.userAgent != null)
                vPeerGroup.setUserAgent(userAgent, version);

            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null) {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                vPeerGroup.setMaxConnections(peerAddresses.length);
                peerAddresses = null;
            } else if (!params.getId().equals(NetworkParameters.ID_REGTEST)) {
                if(discovery != null) {
                    vPeerGroup.addPeerDiscovery(discovery);
                } else {
                    vPeerGroup.addPeerDiscovery(new DnsDiscovery(params));
                    vPeerGroup.addPeerDiscovery(new SeedPeers(params));
                }
            }
            vChain.addWallet(vWallet);
            vPeerGroup.addWallet(vWallet);
            onSetupCompleted();

            if (blockingStartup) {
                vPeerGroup.start();
                // Make sure we shut down cleanly.
                installShutdownHook();

                // TODO: Be able to use the provided download listener when doing a blocking startup.
                final DownloadProgressTracker listener = new DownloadProgressTracker();
                vPeerGroup.startBlockChainDownload(listener);
                listener.await();
            } else {
                Futures.addCallback(vPeerGroup.startAsync(), new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        final DownloadProgressTracker l = downloadListener == null ? new DownloadProgressTracker() : downloadListener;
                        vPeerGroup.startBlockChainDownload(l);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);

                    }
                }, MoreExecutors.directExecutor());
            }
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected void shutDown() throws Exception {
        // Runs in a separate thread.
        try {
            Context.propagate(context);
            vPeerGroup.stop();
            vWallet.saveToFile(vWalletFile);
            vStore.close();

            vPeerGroup = null;
            vWallet = null;
            vStore = null;
            vChain = null;
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    protected Wallet createOrLoadWallet(boolean shouldReplayWallet) throws Exception {
        Wallet wallet;

        maybeMoveOldWalletOutOfTheWay();

        if (vWalletFile.exists()) {
            wallet = loadWallet(shouldReplayWallet);
        } else {
            wallet = createWallet();
            wallet.currentReceiveAddress();
            for (WalletExtension e : provideWalletExtensions()) {
                wallet.addExtension(e);
            }

            // Currently the only way we can be sure that an extension is aware of its containing wallet is by
            // deserializing the extension (see WalletExtension#deserializeWalletExtension(Wallet, byte[]))
            // Hence, we first save and then load wallet to ensure any extensions are correctly initialized.
            wallet.saveToFile(vWalletFile);
            wallet = loadWallet(false);
        }

        if (useAutoSave) {
            this.setupAutoSave(wallet);
        }

        return wallet;
    }

    protected void setupAutoSave(Wallet wallet) {
        wallet.autosaveToFile(vWalletFile, 5, TimeUnit.SECONDS, null);
    }

    private Wallet loadWallet(boolean shouldReplayWallet) throws Exception {
        Wallet wallet;
        FileInputStream walletStream = new FileInputStream(vWalletFile);
        try {
            List<WalletExtension> extensions = provideWalletExtensions();
            WalletExtension[] extArray = extensions.toArray(new WalletExtension[extensions.size()]);
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer;
            if (walletFactory != null)
                serializer = new WalletProtobufSerializer(walletFactory);
            else
                serializer = new WalletProtobufSerializer();
            wallet = serializer.readWallet(params, extArray, proto);
            if (shouldReplayWallet)
                wallet.reset();
        } finally {
            walletStream.close();
        }
        return wallet;
    }

    protected Wallet createWallet() {
        KeyChainGroup.Builder kcg = KeyChainGroup.builder(params, structure);
        if (restoreFromSeed != null)
            kcg.fromSeed(restoreFromSeed, preferredOutputScriptType);
        else if (restoreFromKey != null)
            kcg.fromKey(restoreFromKey, preferredOutputScriptType);
        else
            kcg.fromRandom(preferredOutputScriptType);
        if (walletFactory != null) {
            return walletFactory.create(params, kcg.build());
        } else {
            return new Wallet(params, kcg.build()); // default
        }
    }

    private void maybeMoveOldWalletOutOfTheWay() {
        if (restoreFromSeed == null && restoreFromKey == null) return;
        if (!vWalletFile.exists()) return;
        int counter = 1;
        File newName;
        do {
            newName = new File(vWalletFile.getParent(), "Backup " + counter + " for " + vWalletFile.getName());
            counter++;
        } while (newName.exists());
        log.info("Renaming old wallet file {} to {}", vWalletFile, newName);
        if (!vWalletFile.renameTo(newName)) {
            // This should not happen unless something is really messed up.
            throw new RuntimeException("Failed to rename wallet for restore");
        }
    }

    /**
     * This method is invoked on a background thread after all objects are initialised, but before the peer group
     * or block chain download is started. You can tweak the objects configuration here.
     */
    protected void onSetupCompleted() {
    }

    protected PeerGroup createPeerGroup() {
        if (useTor) {
            System.setProperty("socksProxyHost", torProxyIp);
            System.setProperty("socksProxyPort", torProxyPort);
            return new PeerGroup(this.vWallet.getParams(), this.vChain, new BlockingClientManager());
        } else {
            return new PeerGroup(this.vWallet.getParams(), this.vChain);
        }
    }

    protected void installShutdownHook() {
        if (autoStop) Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    WalletKitCore.this.stopAsync();
                    WalletKitCore.this.awaitTerminated();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public void calculateTokenBalance(SlpOpReturn.SlpTokenType tokenType, SlpUTXO utxo, SlpToken token) {
        String tokenId = token.getTokenId();
        double tokenAmount = BigDecimal.valueOf(utxo.getTokenAmountRaw()).scaleByPowerOfTen(-token.getDecimals()).doubleValue();
        if (this.isBalanceRecorded(tokenType, tokenId)) {
            Objects.requireNonNull(this.getTokenBalance(tokenType, tokenId)).addToBalance(tokenAmount);
        } else {
            if(tokenType == SlpOpReturn.SlpTokenType.SLP) {
                this.slpBalances.add(new SlpTokenBalance(tokenId, tokenAmount));
            } else if(tokenType == SlpOpReturn.SlpTokenType.NFT) {
                this.nftBalances.add(new SlpTokenBalance(tokenId, tokenAmount));
            }
        }
    }

    public void calculateNftParentBalance(SlpUTXO slpUTXO, SlpToken nftParentToken) {
        String tokenId = nftParentToken.getTokenId();
        double tokenAmount = BigDecimal.valueOf(slpUTXO.getTokenAmountRaw()).scaleByPowerOfTen(-nftParentToken.getDecimals()).doubleValue();
        if (this.isNftParentBalanceRecorded(tokenId)) {
            Objects.requireNonNull(this.getNftParentBalance(tokenId)).addToBalance(tokenAmount);
        } else {
            this.nftParentBalances.add(new SlpTokenBalance(tokenId, tokenAmount));
        }
    }

    public boolean isBalanceRecorded(SlpOpReturn.SlpTokenType tokenType, String tokenId) {
        ArrayList<SlpTokenBalance> balancesArray = new ArrayList<SlpTokenBalance>(tokenType == SlpOpReturn.SlpTokenType.SLP ? this.slpBalances : this.nftBalances);
        for(SlpTokenBalance balance : balancesArray) {
            String slpTokenId = balance.getTokenId();
            return slpTokenId != null && slpTokenId.equals(tokenId);
        }
        return false;
    }

    public SlpTokenBalance getTokenBalance(SlpOpReturn.SlpTokenType tokenType, String tokenId) {
        ArrayList<SlpTokenBalance> tokensArray = new ArrayList<SlpTokenBalance>(tokenType == SlpOpReturn.SlpTokenType.SLP ? this.slpBalances : this.nftBalances);
        for(SlpTokenBalance tokenBalance : tokensArray) {
            if(tokenBalance.getTokenId().equals(tokenId)) {
                return tokenBalance;
            }
        }
        return null;
    }

    public boolean isNftParentBalanceRecorded(String tokenId) {
        for (SlpTokenBalance tokenBalance : this.nftParentBalances) {
            if (tokenBalance.getTokenId().equals(tokenId)) {
                return true;
            }
        }

        return false;
    }

    public SlpTokenBalance getNftParentBalance(String tokenId) {
        for (SlpTokenBalance tokenBalance : this.nftParentBalances) {
            if (tokenBalance.getTokenId().equals(tokenId)) {
                return tokenBalance;
            }
        }

        return null;
    }

    public boolean isTokenMapped(SlpOpReturn.SlpTokenType tokenType, String tokenId) {
        ArrayList<SlpToken> tokensArray = new ArrayList<SlpToken>(tokenType == SlpOpReturn.SlpTokenType.SLP ? this.slpTokens : this.nfts);
        for(SlpToken token : tokensArray) {
            String slpTokenId = token.getTokenId();
            return slpTokenId != null && slpTokenId.equals(tokenId);
        }
        return false;
    }

    public boolean hasTransactionBeenRecorded(String txid) {
        return this.verifiedSlpTxs.contains(txid);
    }

    public Transaction createSlpTransaction(String slpDestinationAddress, String tokenId, double numTokens, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        return this.createSlpTransaction(slpDestinationAddress, tokenId, numTokens, aesKey, true);
    }

    public Transaction createSlpTransaction(String slpDestinationAddress, String tokenId, double numTokens, @Nullable KeyParameter aesKey, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildTx(tokenId, numTokens, slpDestinationAddress, this, aesKey, allowUnconfirmed).blockingGet();
    }

    public Transaction createSlpTransactionBip70(String tokenId, @Nullable KeyParameter aesKey, List<Long> rawTokens, List<String> addresses, SlpPaymentSession paymentSession) throws InsufficientMoneyException {
        return this.createSlpTransactionBip70(tokenId, aesKey, rawTokens, addresses, paymentSession, true);
    }

    public Transaction createSlpTransactionBip70(String tokenId, @Nullable KeyParameter aesKey, List<Long> rawTokens, List<String> addresses, SlpPaymentSession paymentSession, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildTxBip70(tokenId, this, aesKey, rawTokens, addresses, paymentSession, allowUnconfirmed).blockingGet();
    }

    public SendRequest createSlpGenesisTransaction(String ticker, String name, String url, int decimals, long tokenQuantity, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        SendRequest req = SendRequest.createSlpTransaction(this.params());
        req.aesKey = aesKey;
        req.shuffleOutputs = false;
        req.feePerKb = Coin.valueOf(1000L);
        req.ensureMinRequiredFee = true;
        SlpOpReturnOutputGenesis slpOpReturn = new SlpOpReturnOutputGenesis(ticker, name, url, decimals, tokenQuantity);
        req.tx.addOutput(Coin.ZERO, slpOpReturn.getScript());
        req.tx.addOutput(this.wallet().getParams().getMinNonDustOutput(), this.wallet().currentChangeAddress());
        return req;
    }

    public SendRequest createNftChildGenesisTransaction(String nftParentId, String ticker, String name, String url, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        return createNftChildGenesisTransaction(nftParentId, ticker, name, url, aesKey, true);
    }

    public SendRequest createNftChildGenesisTransaction(String nftParentId, String ticker, String name, String url, @Nullable KeyParameter aesKey, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildNftChildGenesisTx(nftParentId, ticker, name, url, this, aesKey, allowUnconfirmed);
    }

    public Transaction createNftChildSendTx(String slpDestinationAddress, String nftTokenId, double numTokens, @Nullable KeyParameter aesKey) throws InsufficientMoneyException {
        return this.createNftChildSendTx(slpDestinationAddress, nftTokenId, numTokens, aesKey, true);
    }

    public Transaction createNftChildSendTx(String slpDestinationAddress, String nftTokenId, double numTokens, @Nullable KeyParameter aesKey, boolean allowUnconfirmed) throws InsufficientMoneyException {
        return SlpTxBuilder.buildNftChildSendTx(nftTokenId, numTokens, slpDestinationAddress, this, aesKey, allowUnconfirmed).blockingGet();
    }

    protected SlpUTXO processSlpUtxo(SlpOpReturn slpOpReturn, TransactionOutput utxo) {
        long tokenRawAmount = slpOpReturn.getRawAmountOfUtxo(utxo.getIndex() - 1);
        return new SlpUTXO(slpOpReturn.getTokenId(), tokenRawAmount, utxo, SlpUTXO.SlpUtxoType.NORMAL);
    }

    public void recalculateSlpUtxos() {
        if (!recalculatingTokens) {
            boolean addedVerifiedTx = false;
            boolean addedToken = false;
            recalculatingTokens = true;
            this.slpUtxos.clear();
            this.slpBalances.clear();
            this.nftParentUtxos.clear();
            this.nftParentBalances.clear();
            List<TransactionOutput> utxos = this.wallet().getAllDustUtxos(true, true);
            ArrayList<SlpUTXO> slpUtxosToAdd = new ArrayList<>();
            ArrayList<SlpUTXO> nftParentUtxosToAdd = new ArrayList<>();
            ArrayList<SlpToken> tokensToAdd = new ArrayList<>();

            for (TransactionOutput utxo : utxos) {
                Transaction tx = utxo.getParentTransaction();
                if (tx != null) {
                    if (SlpOpReturn.isSlpTx(tx)) {
                        if (hasTransactionBeenRecorded(tx.getTxId().toString())) {
                            SlpOpReturn slpOpReturn = new SlpOpReturn(tx);
                            String tokenId = slpOpReturn.getTokenId();

                            if (!this.isTokenMapped(SlpOpReturn.SlpTokenType.SLP, tokenId)) {
                                SlpToken slpToken = this.tryCacheToken(tokenId);
                                if(slpToken != null) {
                                    tokensToAdd.add(slpToken);
                                    addedToken = true;
                                }
                            } else {
                                SlpUTXO slpUTXO = processSlpUtxo(slpOpReturn, utxo);
                                SlpToken slpToken = this.getSlpToken(tokenId);

                                if(slpOpReturn.getSlpTxType() == SlpOpReturn.SlpTxType.SEND || slpOpReturn.getSlpTxType() == SlpOpReturn.SlpTxType.GENESIS || slpOpReturn.getSlpTxType() == SlpOpReturn.SlpTxType.MINT) {
                                    this.calculateTokenBalance(SlpOpReturn.SlpTokenType.SLP, slpUTXO, slpToken);
                                    slpUtxosToAdd.add(slpUTXO);
                                } else if(slpOpReturn.getSlpTxType() == SlpOpReturn.SlpTxType.NFT_PARENT_SEND || slpOpReturn.getSlpTxType() == SlpOpReturn.SlpTxType.NFT_PARENT_GENESIS || slpOpReturn.getSlpTxType() == SlpOpReturn.SlpTxType.NFT_PARENT_MINT) {
                                    this.calculateNftParentBalance(slpUTXO, slpToken);
                                    nftParentUtxosToAdd.add(slpUTXO);
                                }
                            }
                        } else {
                            SlpDbValidTransaction validTxQuery = new SlpDbValidTransaction(tx.getTxId().toString());
                            boolean valid = this.slpDbProcessor.isValidSlpTx(validTxQuery.getEncoded());
                            if (valid) {
                                this.verifiedSlpTxs.add(tx.getTxId().toString());
                                addedVerifiedTx = true;
                            }
                        }
                    }
                }
            }

            this.slpUtxos.addAll(slpUtxosToAdd);
            this.nftParentUtxos.addAll(nftParentUtxosToAdd);

            if(addedVerifiedTx) {
                this.saveVerifiedTxs(this.verifiedSlpTxs);
            }

            if(addedToken) {
                this.slpTokens.addAll(tokensToAdd);
                this.saveTokens(this.slpTokens);
            }
            recalculatingTokens = false;
        }
    }

    public void recalculateNftUtxos() {
        if (!recalculatingNfts) {
            boolean addedVerifiedTx = false;
            boolean addedNft = false;
            recalculatingNfts = true;
            this.nftUtxos.clear();
            this.nftBalances.clear();
            List<TransactionOutput> utxos = this.wallet().getAllDustUtxos(true, true);
            ArrayList<SlpUTXO> nftUtxosToAdd = new ArrayList<>();
            ArrayList<NonFungibleSlpToken> nftsToAdd = new ArrayList<>();

            for (TransactionOutput utxo : utxos) {
                Transaction tx = utxo.getParentTransaction();
                if (tx != null) {
                    if (SlpOpReturn.isNftChildTx(tx)) {
                        if (hasTransactionBeenRecorded(tx.getTxId().toString())) {
                            SlpOpReturn slpOpReturn = new SlpOpReturn(tx);
                            String tokenId = slpOpReturn.getTokenId();

                            if (!this.isTokenMapped(SlpOpReturn.SlpTokenType.NFT, tokenId)) {
                                NonFungibleSlpToken nft = this.tryCacheNft(tokenId);
                                if(nft != null) {
                                    nftsToAdd.add(nft);
                                    addedNft = true;
                                }
                            } else {
                                SlpUTXO slpUTXO = processSlpUtxo(slpOpReturn, utxo);
                                NonFungibleSlpToken slpToken = this.getNft(tokenId);
                                this.calculateTokenBalance(SlpOpReturn.SlpTokenType.NFT, slpUTXO, slpToken);
                                nftUtxosToAdd.add(slpUTXO);
                            }
                        } else {
                            SlpDbValidTransaction validTxQuery = new SlpDbValidTransaction(tx.getTxId().toString());
                            boolean valid = this.slpDbProcessor.isValidSlpTx(validTxQuery.getEncoded());
                            if (valid) {
                                this.verifiedSlpTxs.add(tx.getTxId().toString());
                                addedVerifiedTx = true;
                            }
                        }
                    }
                }
            }

            this.nftUtxos.addAll(nftUtxosToAdd);

            if(addedVerifiedTx) {
                this.saveVerifiedTxs(this.verifiedSlpTxs);
            }

            if(addedNft) {
                this.nfts.addAll(nftsToAdd);
                this.saveNfts(this.nfts);
            }
            recalculatingNfts = false;
        }
    }

    private SlpToken tryCacheToken(String tokenId) {
        if (!this.isTokenMapped(SlpOpReturn.SlpTokenType.SLP, tokenId)) {
            SlpDbTokenDetails tokenQuery = new SlpDbTokenDetails(tokenId);
            JSONObject tokenData = this.slpDbProcessor.getTokenData(tokenQuery.getEncoded());

            if (tokenData != null) {
                int decimals = tokenData.getInt("decimals");
                String ticker = tokenData.getString("ticker");
                return new SlpToken(tokenId, ticker, decimals);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private NonFungibleSlpToken tryCacheNft(String tokenId) {
        if (!this.isTokenMapped(SlpOpReturn.SlpTokenType.NFT, tokenId)) {
            SlpDbNftDetails tokenQuery = new SlpDbNftDetails(tokenId);
            JSONObject tokenData = this.slpDbProcessor.getTokenData(tokenQuery.getEncoded());

            if (tokenData != null) {
                int decimals = tokenData.getInt("decimals");
                String ticker = tokenData.getString("ticker");
                String nftParentId = tokenData.getString("nftParentId");
                String name = tokenData.getString("name");
                return new NonFungibleSlpToken(tokenId, nftParentId, name, ticker, decimals);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    protected void saveTokens(ArrayList<SlpToken> slpTokens) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.directory(), tokensFile.getName())), StandardCharsets.UTF_8))) {
            JSONArray json = new JSONArray();
            for (SlpToken slpToken : slpTokens) {
                JSONObject tokenObj = new JSONObject();
                tokenObj.put("tokenId", slpToken.getTokenId());
                tokenObj.put("ticker", slpToken.getTicker());
                tokenObj.put("decimals", slpToken.getDecimals());
                json.put(tokenObj);
            }
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void saveNfts(ArrayList<NonFungibleSlpToken> nfts) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.directory(), nftsFile.getName())), StandardCharsets.UTF_8))) {
            JSONArray json = new JSONArray();
            for (NonFungibleSlpToken nft : nfts) {
                JSONObject tokenObj = new JSONObject();
                tokenObj.put("tokenId", nft.getTokenId());
                tokenObj.put("nftParentId", nft.getNftParentId());
                tokenObj.put("name", nft.getName());
                tokenObj.put("ticker", nft.getTicker());
                tokenObj.put("decimals", nft.getDecimals());
                json.put(tokenObj);
            }
            writer.write(json.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void loadTokens() {
        BufferedReader br = null;
        try {
            FileInputStream is = new FileInputStream(new File(this.directory(), this.tokensFile.getName()));
            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String jsonString = sb.toString();

            try {
                JSONArray tokensJson = new JSONArray(jsonString);
                for (int x = 0; x < tokensJson.length(); x++) {
                    JSONObject tokenObj = tokensJson.getJSONObject(x);
                    String tokenId = tokenObj.getString("tokenId");
                    String ticker = tokenObj.getString("ticker");
                    int decimals = tokenObj.getInt("decimals");
                    SlpToken slpToken = new SlpToken(tokenId, ticker, decimals);
                    if (!this.isTokenMapped(SlpOpReturn.SlpTokenType.SLP, tokenId)) {
                        this.slpTokens.add(slpToken);
                    }
                }
            } catch (Exception e) {
                this.slpTokens = new ArrayList<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert br != null;
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void loadNfts() {
        BufferedReader br = null;
        try {
            FileInputStream is = new FileInputStream(new File(this.directory(), this.nftsFile.getName()));
            br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
            }
            String jsonString = sb.toString();

            try {
                JSONArray tokensJson = new JSONArray(jsonString);
                for (int x = 0; x < tokensJson.length(); x++) {
                    JSONObject tokenObj = tokensJson.getJSONObject(x);
                    String tokenId = tokenObj.getString("tokenId");
                    String nftParentId = tokenObj.getString("nftParentId");
                    String ticker = tokenObj.getString("ticker");
                    String name = tokenObj.getString("name");
                    int decimals = tokenObj.getInt("decimals");
                    NonFungibleSlpToken nft = new NonFungibleSlpToken(tokenId, nftParentId, name, ticker, decimals);
                    if (!this.isTokenMapped(SlpOpReturn.SlpTokenType.NFT, tokenId)) {
                        this.nfts.add(nft);
                    }
                }
            } catch (Exception e) {
                this.nfts = new ArrayList<>();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                assert br != null;
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void saveVerifiedTxs(ArrayList<String> recordedSlpTxs) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(new File(this.directory(), this.filePrefix + ".txs")), StandardCharsets.UTF_8))) {
            StringBuilder text = new StringBuilder();
            for (String txHash : recordedSlpTxs) {
                text.append(txHash).append("\n");
            }
            writer.write(text.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void loadRecordedTxs() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(new File(this.directory(), this.filePrefix + ".txs")));
            String line = br.readLine();
            while (line != null) {
                String txHash = line;
                this.verifiedSlpTxs.add(txHash);
                line = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                assert br != null;
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
