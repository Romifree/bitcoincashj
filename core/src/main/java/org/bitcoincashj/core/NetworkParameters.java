/*
 * Copyright 2011 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoincashj.core;

import org.bitcoincashj.net.discovery.HttpDiscovery;
import org.bitcoincashj.params.*;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.ScriptOpCodes;
import org.bitcoincashj.utils.MonetaryFormat;
import org.bitcoincashj.utils.VersionTally;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.bitcoincashj.core.Coin.COIN;
import static org.bitcoincashj.core.Coin.FIFTY_COINS;

/**
 * <p>NetworkParameters contains the data needed for working with an instantiation of a Bitcoin chain.</p>
 *
 * <p>This is an abstract class, concrete instantiations can be found in the params package. There are four:
 * one for the main network ({@link MainNetParams}), one for the public test network, and two others that are
 * intended for unit testing and local app development purposes. Although this class contains some aliases for
 * them, you are encouraged to call the static get() methods on each specific params class directly.</p>
 */
public abstract class NetworkParameters {
    /** The string returned by getId() for the main, production network where people trade things. */
    public static final String ID_MAINNET = "org.bitcoincash.production";
    /**
     * The string returned by getId() for the testnet.
     */
    public static final String ID_TESTNET = "org.bitcoincash.test";
    /**
     * The string returned by getId() for the testnet.
     */
    public static final String ID_TESTNET4 = "org.bitcoincash.test4";
    /**
     * The string returned by getId() for the scalenet.
     */
    public static final String ID_SCALENET = "org.bitcoincash.scalenet";
    /**
     * The string returned by getId() for regtest mode.
     */
    public static final String ID_REGTEST = "org.bitcoincash.regtest";
    /**
     * Unit test network.
     */
    public static final String ID_UNITTESTNET = "org.bitcoincashj.unittest";

    /**
     * The string used by the payment protocol to represent the main net.
     */
    public static final String PAYMENT_PROTOCOL_ID_MAINNET = "main";
    /**
     * The string used by the payment protocol to represent the test net.
     */
    public static final String PAYMENT_PROTOCOL_ID_TESTNET = "test";
    /**
     * The string used by the payment protocol to represent the test net.
     */
    public static final String PAYMENT_PROTOCOL_ID_TESTNET4 = "test4";
    /**
     * The string used by the payment protocol to represent the scale net.
     */
    public static final String PAYMENT_PROTOCOL_ID_SCALENET = "scale";
    /**
     * The string used by the payment protocol to represent unit testing (note that this is non-standard).
     */
    public static final String PAYMENT_PROTOCOL_ID_UNIT_TESTS = "unittest";
    public static final String PAYMENT_PROTOCOL_ID_REGTEST = "regtest";

    // TODO: Seed nodes should be here as well.

    protected final Block genesisBlock;
    protected BigInteger maxTarget;
    protected int port;
    protected long packetMagic;  // Indicates message origin network and is used to seek to the next message when stream state is unknown.
    protected int[] acceptableAddressCodes;
    protected int addressHeader;
    protected int p2shHeader;
    protected int dumpedPrivateKeyHeader;
    protected int interval;
    protected int targetTimespan;
    protected int defaultPeerCount;
    protected int bip32HeaderP2PKHpub;
    protected int bip32HeaderP2PKHpriv;

    /**
     * Used to check majorities for block version upgrade
     */
    protected int majorityEnforceBlockUpgrade;
    protected int majorityRejectBlockOutdated;
    protected int majorityWindow;

    // Aug, 1 2017 hard fork
    protected int uahfHeight;
    // Nov, 13 2017 hard fork
    protected int daaUpdateHeight;
    // May, 15 2018 hard fork
    protected long monolithActivationTime = 1526400000L;
    // Nov, 15 2018 hard fork
    protected static long november2018ActivationTime = 1542300000L;
    // Nov, 15 2020 hard fork
    protected long asertUpdateTime;
    /**
     * See getId(). This may be null for old deserialized wallets. In that case we derive it heuristically
     * by looking at the port number.
     */
    protected String id;

    /**
     * The depth of blocks required for a coinbase transaction to be spendable.
     */
    protected int spendableCoinbaseDepth;
    protected int subsidyDecreaseBlockCount;

    protected String[] dnsSeeds;
    protected String[] addrSeeds;
    protected HttpDiscovery.Details[] httpSeeds = {};
    protected Map<Integer, Sha256Hash> checkpoints = new HashMap<>();
    protected volatile transient MessageSerializer defaultSerializer = null;
    protected String cashAddrPrefix;
    protected String simpleledgerPrefix;
    protected int asertReferenceBlockBits;
    protected BigInteger asertReferenceBlockAncestorTime;
    protected BigInteger asertReferenceBlockHeight;
    protected long asertHalfLife;
    protected boolean allowMinDifficultyBlocks;
    protected int maxBlockSize;
    /**
     * A "sigop" is a signature verification operation. Because they're expensive we also impose a separate limit on
     * the number in a block to prevent somebody mining a huge block that has way more sigops than normal, so is very
     * expensive/slow to verify.
     */
    protected int maxBlockSigops;

    protected NetworkParameters() {
        genesisBlock = createGenesis(this);
    }

    private static Block createGenesis(NetworkParameters n) {
        Block genesisBlock = new Block(n, Block.BLOCK_VERSION_GENESIS);
        Transaction t = new Transaction(n);
        try {
            // A script containing the difficulty bits and the following message:
            //
            //   "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
            byte[] bytes = Utils.HEX.decode
                    ("04ffff001d0104455468652054696d65732030332f4a616e2f32303039204368616e63656c6c6f72206f6e206272696e6b206f66207365636f6e64206261696c6f757420666f722062616e6b73");
            t.addInput(new TransactionInput(n, t, bytes));
            ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
            Script.writeBytes(scriptPubKeyBytes, Utils.HEX.decode
                    ("04678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5f"));
            scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
            t.addOutput(new TransactionOutput(n, t, FIFTY_COINS, scriptPubKeyBytes.toByteArray()));
        } catch (Exception e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
        genesisBlock.addTransaction(t);
        return genesisBlock;
    }

    public static final int TARGET_TIMESPAN = 14 * 24 * 60 * 60;  // 2 weeks per difficulty cycle, on average.
    public static final int TARGET_SPACING = 10 * 60;  // 10 minutes per block.
    public static final BigInteger TARGET_SPACING_BIGINT = BigInteger.valueOf(10L * 60L);  // 10 minutes per block.

    /**
     * Blocks with a timestamp after this should enforce BIP 16, aka "Pay to script hash". This BIP changed the
     * network rules in a soft-forking manner, that is, blocks that don't follow the rules are accepted but not
     * mined upon and thus will be quickly re-orged out as long as the majority are enforcing the rule.
     */
    public static final int BIP16_ENFORCE_TIME = 1333238400;

    /**
     * The maximum number of coins to be generated
     */
    public static final long MAX_COINS = 21000000;

    /**
     * The maximum money to be generated
     */
    public static final Coin MAX_MONEY = COIN.multiply(MAX_COINS);

    /**
     * A Java package style string acting as unique ID for these parameters
     */
    public String getId() {
        return id;
    }

    public abstract String getPaymentProtocolId();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return getId().equals(((NetworkParameters) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    /**
     * Returns the network parameters for the given string ID or NULL if not recognized.
     */
    @Nullable
    public static NetworkParameters fromID(String id) {
        switch (id) {
            case ID_MAINNET:
                return MainNetParams.get();
            case ID_TESTNET:
                return TestNet3Params.get();
            case ID_TESTNET4:
                return TestNet4Params.get();
            case ID_SCALENET:
                return ScaleNetParams.get();
            case ID_UNITTESTNET:
                return UnitTestParams.get();
            case ID_REGTEST:
                return RegTestParams.get();
            default:
                return null;
        }
    }

    /**
     * Returns the network parameters for the given string paymentProtocolID or NULL if not recognized.
     */
    @Nullable
    public static NetworkParameters fromPmtProtocolID(String pmtProtocolId) {
        if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_MAINNET)) {
            return MainNetParams.get();
        } else if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_TESTNET)) {
            return TestNet3Params.get();
        } else if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_TESTNET4)) {
            return TestNet4Params.get();
        } else if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_UNIT_TESTS)) {
            return UnitTestParams.get();
        } else if (pmtProtocolId.equals(PAYMENT_PROTOCOL_ID_REGTEST)) {
            return RegTestParams.get();
        } else {
            return null;
        }
    }

    public int getSpendableCoinbaseDepth() {
        return spendableCoinbaseDepth;
    }

    public void verifyDifficulty(BigInteger newTarget, Block nextBlock) {
        if (newTarget.compareTo(this.getMaxTarget()) > 0) {
            newTarget = this.getMaxTarget();
        }

        int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;
        long receivedTargetCompact = nextBlock.getDifficultyTarget();

        // The calculated difficulty is to a higher precision than received, so reduce here.
        BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        newTarget = newTarget.and(mask);
        long newTargetCompact = Utils.encodeCompactBits(newTarget);

        if (newTargetCompact != receivedTargetCompact)
            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    Long.toHexString(newTargetCompact) + " vs " + Long.toHexString(receivedTargetCompact));
    }

    public void verifyAsertDifficulty(BigInteger newTarget, Block nextBlock) {
        if (newTarget.compareTo(this.getMaxTarget()) > 0) {
            newTarget = this.getMaxTarget();
        }

        BigInteger receivedTarget = BigInteger.valueOf(Utils.encodeCompactBits(nextBlock.getDifficultyTargetAsInteger()));
        if (!newTarget.equals(receivedTarget))
            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    newTarget.toString(16) + " vs " + receivedTarget.toString(16));
    }

    /**
     * Returns true if the block height is either not a checkpoint, or is a checkpoint and the hash matches.
     */
    public boolean passesCheckpoint(int height, Sha256Hash hash) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash == null || checkpointHash.equals(hash);
    }

    public int[] getAcceptableAddressCodes() {
        return acceptableAddressCodes;
    }

    public long getAsertHalfLife() {
        return asertHalfLife;
    }

    public boolean allowMinDifficultyBlocks() {
        return allowMinDifficultyBlocks;
    }

    public int getMaxBlockSize() {
        return maxBlockSize;
    }

    public int getMaxBlockSigops() {
        return maxBlockSigops;
    }

    /**
     * Returns true if the given height has a recorded checkpoint.
     */
    public boolean isCheckpoint(int height) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash != null;
    }

    public int getSubsidyDecreaseBlockCount() {
        return subsidyDecreaseBlockCount;
    }

    /**
     * Returns DNS names that when resolved, give IP addresses of active peers.
     */
    public String[] getDnsSeeds() {
        return dnsSeeds;
    }

    public int getDefaultPeerCount() {
        return defaultPeerCount;
    }

    /**
     * Returns IP address of active peers.
     */
    public String[] getAddrSeeds() {
        return addrSeeds;
    }

    /**
     * Returns discovery objects for seeds implementing the Cartographer protocol. See {@link HttpDiscovery} for more info.
     */
    public HttpDiscovery.Details[] getHttpSeeds() {
        return httpSeeds;
    }

    /**
     * <p>Genesis block for this chain.</p>
     *
     * <p>The first block in every chain is a well known constant shared between all Bitcoin implementations. For a
     * block to be valid, it must be eventually possible to work backwards to the genesis block by following the
     * prevBlockHash pointers in the block headers.</p>
     *
     * <p>The genesis blocks for both test and main networks contain the timestamp of when they were created,
     * and a message in the coinbase transaction. It says, <i>"The Times 03/Jan/2009 Chancellor on brink of second
     * bailout for banks"</i>.</p>
     */
    public Block getGenesisBlock() {
        return genesisBlock;
    }

    /**
     * Default TCP port on which to connect to nodes.
     */
    public int getPort() {
        return port;
    }

    /**
     * The header bytes that identify the start of a packet on this network.
     */
    public long getPacketMagic() {
        return packetMagic;
    }

    /**
     * First byte of a base58 encoded address. See {@link Address}. This is the same as acceptableAddressCodes[0] and
     * is the one used for "normal" addresses. Other types of address may be encountered with version codes found in
     * the acceptableAddressCodes array.
     */
    public int getAddressHeader() {
        return addressHeader;
    }

    /**
     * First byte of a base58 encoded P2SH address.  P2SH addresses are defined as part of BIP0013.
     */
    public int getP2SHHeader() {
        return p2shHeader;
    }

    /**
     * First byte of a base58 encoded dumped private key. See {@link DumpedPrivateKey}.
     */
    public int getDumpedPrivateKeyHeader() {
        return dumpedPrivateKeyHeader;
    }

    /**
     * How much time in seconds is supposed to pass between "interval" blocks. If the actual elapsed time is
     * significantly different from this value, the network difficulty formula will produce a different value. Both
     * test and main Bitcoin networks use 2 weeks (1209600 seconds).
     */
    public int getTargetTimespan() {
        return targetTimespan;
    }

    /**
     * If we are running in testnet-in-a-box mode, we allow connections to nodes with 0 non-genesis blocks.
     */
    public boolean allowEmptyPeerChain() {
        return true;
    }

    /**
     * How many blocks pass between difficulty adjustment periods. Bitcoin standardises this to be 2016.
     */
    public int getInterval() {
        return interval;
    }

    /**
     * Maximum target represents the easiest allowable proof of work.
     */
    public BigInteger getMaxTarget() {
        return maxTarget;
    }

    /** Returns the 4 byte header for BIP32 wallet P2PKH - public key part. */
    public int getBip32HeaderP2PKHpub() {
        return bip32HeaderP2PKHpub;
    }

    /**
     * Returns the 4 byte header for BIP32 wallet P2PKH - private key part.
     */
    public int getBip32HeaderP2PKHpriv() {
        return bip32HeaderP2PKHpriv;
    }

    public int getDAAUpdateHeight() {
        return daaUpdateHeight;
    }

    public int getAsertReferenceBlockBits() {
        return asertReferenceBlockBits;
    }

    public BigInteger getAsertReferenceBlockAncestorTime() {
        return asertReferenceBlockAncestorTime;
    }

    public BigInteger getAsertReferenceBlockHeight() {
        return asertReferenceBlockHeight;
    }

    public long getAsertUpdateTime() {
        return asertUpdateTime;
    }

    /**
     * MTP activation time for May 15th, 2018 upgrade
     **/
    public long getMonolithActivationTime() {
        return monolithActivationTime;
    }

    /**
     * Returns the number of coins that will be produced in total, on this
     * network. Where not applicable, a very large number of coins is returned
     * instead (i.e. the main coin issue for Dogecoin).
     */
    public abstract Coin getMaxMoney();

    /**
     * @deprecated use {@link TransactionOutput#getMinNonDustValue()}
     */
    @Deprecated
    public abstract Coin getMinNonDustOutput();

    /**
     * The monetary object for this currency.
     */
    public abstract MonetaryFormat getMonetaryFormat();

    /**
     * Scheme part for URIs, for example "bitcoin".
     */
    public String getUriScheme() {
        return getCashAddrPrefix();
    }


    /**
     * Returns whether this network has a maximum number of coins (finite supply) or
     * not. Always returns true for Bitcoin, but exists to be overridden for other
     * networks.
     */
    public abstract boolean hasMaxMoney();

    /**
     * Return the default serializer for this network. This is a shared serializer.
     *
     * @return the default serializer for this network.
     */
    public final MessageSerializer getDefaultSerializer() {
        // Construct a default serializer if we don't have one
        if (null == this.defaultSerializer) {
            // Don't grab a lock unless we absolutely need it
            synchronized (this) {
                // Now we have a lock, double check there's still no serializer
                // and create one if so.
                if (null == this.defaultSerializer) {
                    // As the serializers are intended to be immutable, creating
                    // two due to a race condition should not be a problem, however
                    // to be safe we ensure only one exists for each network.
                    this.defaultSerializer = getSerializer(false);
                }
            }
        }
        return defaultSerializer;
    }

    /**
     * Construct and return a custom serializer.
     */
    public abstract BitcoinSerializer getSerializer(boolean parseRetain);

    /**
     * The number of blocks in the last {@link #getMajorityWindow()} blocks
     * at which to trigger a notice to the user to upgrade their client, where
     * the client does not understand those blocks.
     */
    public int getMajorityEnforceBlockUpgrade() {
        return majorityEnforceBlockUpgrade;
    }

    /**
     * The number of blocks in the last {@link #getMajorityWindow()} blocks
     * at which to enforce the requirement that all new blocks are of the
     * newer type (i.e. outdated blocks are rejected).
     */
    public int getMajorityRejectBlockOutdated() {
        return majorityRejectBlockOutdated;
    }

    /**
     * The sampling window from which the version numbers of blocks are taken
     * in order to determine if a new block version is now the majority.
     */
    public int getMajorityWindow() {
        return majorityWindow;
    }

    /**
     * The flags indicating which block validation tests should be applied to
     * the given block. Enables support for alternative blockchains which enable
     * tests based on different criteria.
     *
     * @param block  block to determine flags for.
     * @param height height of the block, if known, null otherwise. Returned
     *               tests should be a safe subset if block height is unknown.
     */
    public EnumSet<Block.VerifyFlag> getBlockVerificationFlags(final Block block,
                                                               final VersionTally tally, final Integer height) {
        final EnumSet<Block.VerifyFlag> flags = EnumSet.noneOf(Block.VerifyFlag.class);

        if (block.isBIP34()) {
            final Integer count = tally.getCountAtOrAbove(Block.BLOCK_VERSION_BIP34);
            if (null != count && count >= getMajorityEnforceBlockUpgrade()) {
                flags.add(Block.VerifyFlag.HEIGHT_IN_COINBASE);
            }
        }
        return flags;
    }

    /**
     * The flags indicating which script validation tests should be applied to
     * the given transaction. Enables support for alternative blockchains which enable
     * tests based on different criteria.
     *
     * @param block       block the transaction belongs to.
     * @param transaction to determine flags for.
     * @param height      height of the block, if known, null otherwise. Returned
     *                    tests should be a safe subset if block height is unknown.
     */
    public EnumSet<Script.VerifyFlag> getTransactionVerificationFlags(final Block block,
                                                                      final Transaction transaction, final VersionTally tally, final Integer height) {
        final EnumSet<Script.VerifyFlag> verifyFlags = EnumSet.noneOf(Script.VerifyFlag.class);
        if (block.getTimeSeconds() >= NetworkParameters.BIP16_ENFORCE_TIME)
            verifyFlags.add(Script.VerifyFlag.P2SH);

        // Start enforcing CHECKLOCKTIMEVERIFY, (BIP65) for block.nVersion=4
        // blocks, when 75% of the network has upgraded:
        if (block.getVersion() >= Block.BLOCK_VERSION_BIP65 &&
                tally.getCountAtOrAbove(Block.BLOCK_VERSION_BIP65) > this.getMajorityEnforceBlockUpgrade()) {
            verifyFlags.add(Script.VerifyFlag.CHECKLOCKTIMEVERIFY);
        }

        // Start enforcing CHECKDATASIG, if November 15 2018 hardfork reached
        if (block.getTimeSeconds() >= NetworkParameters.november2018ActivationTime) {
            verifyFlags.add(Script.VerifyFlag.CHECKDATASIG);
        }

        return verifyFlags;
    }

    public abstract int getProtocolVersionNum(final ProtocolVersion version);

    public String getCashAddrPrefix() {
        return cashAddrPrefix;
    }

    public String getSimpleledgerPrefix() {
        return simpleledgerPrefix;
    }

    public enum ProtocolVersion {
        MINIMUM(70000),
        PONG(60001),
        BLOOM_FILTER(70000), // BIP37
        BLOOM_FILTER_BIP111(70011), // BIP111
        CURRENT(70013);

        private final int bitcoinProtocol;

        ProtocolVersion(final int bitcoinProtocol) {
            this.bitcoinProtocol = bitcoinProtocol;
        }

        public int getBitcoinProtocolVersion() {
            return bitcoinProtocol;
        }
    }
}
