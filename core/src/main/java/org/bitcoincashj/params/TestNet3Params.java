/*
 * Copyright 2013 Google Inc.
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

package org.bitcoincashj.params;

import org.bitcoincashj.core.Block;
import org.bitcoincashj.core.Utils;

import java.math.BigInteger;
import java.util.Date;

import static com.google.common.base.Preconditions.checkState;

/**
 * Parameters for the testnet, a separate public instance of Bitcoin that has relaxed rules suitable for development
 * and testing of applications and new Bitcoin versions.
 */
public class TestNet3Params extends AbstractBitcoinNetParams {
    public static final int TESTNET_MAJORITY_WINDOW = 100;
    public static final int TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED = 75;
    public static final int TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 51;

    public TestNet3Params() {
        super();
        id = ID_TESTNET;
        packetMagic = 0xf4e5f3f4L;
        targetTimespan = TARGET_TIMESPAN;
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        port = 18333;
        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        dumpedPrivateKeyHeader = 239;
        genesisBlock.setTime(1296688602L);
        genesisBlock.setDifficultyTarget(0x1d00ffffL);
        genesisBlock.setNonce(414098458);
        spendableCoinbaseDepth = 100;
        defaultPeerCount = 4;
        String genesisHash = genesisBlock.getHashAsString();
        checkState(genesisHash.equals("000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"));

        dnsSeeds = new String[]{
                "testnet-seed-bch.bitcoinforks.org",
                "testnet-seed.bchd.cash",
                "seed.tbch.loping.net"
        };
        httpSeeds = null;
        addrSeeds = new String[] {
                "3.1.200.231",
                "18.202.213.168",
                "18.208.207.151",
                "34.76.213.233",
                "34.77.146.192",
                "35.209.103.132",
                "35.226.242.164",
                "47.111.162.146",
                "47.75.218.79",
                "51.15.125.5",
                "52.137.48.45",
                "52.210.18.84",
                "52.211.211.11",
                "54.164.174.73",
                "71.139.127.73",
                "84.38.3.199",
                "91.121.88.52",
                "95.179.183.143",
                "95.216.174.16",
                "103.76.36.113",
                "119.3.168.84",
                "121.40.76.64",
                "139.162.76.144",
                "157.230.114.14",
                "159.65.163.15",
                "165.22.88.163",
                "172.105.233.17",
                "193.135.10.215",
                "194.14.247.131",
                "195.154.177.49",
                "203.162.80.101",
                "206.189.203.168",
                "212.47.254.88",
                "[2001:41d0:1:8d34::1]",
                "[2a01:4f8:190:4210::2]",
                "[2a0a:51c0:0:136::4]"
        };
        bip32HeaderP2PKHpub = 0x043587cf; // The 4 byte header that serializes in base58 to "tpub".
        bip32HeaderP2PKHpriv = 0x04358394; // The 4 byte header that serializes in base58 to "tprv"

        majorityEnforceBlockUpgrade = TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TESTNET_MAJORITY_WINDOW;
        asertReferenceBlockBits = 0x1d00ffff;
        asertReferenceBlockHeight = BigInteger.valueOf(1421481L);
        asertReferenceBlockAncestorTime = BigInteger.valueOf(1605445400L);
        asertUpdateTime = 1605441600L;
        // Aug, 1 hard fork
        uahfHeight = 1155876;
        // Nov, 13 hard fork
        daaUpdateHeight = 1188697;
        cashAddrPrefix = "bchtest";
        simpleledgerPrefix = "slptest";

        asertHalfLife = 60L * 60L;
        allowMinDifficultyBlocks = true;
        maxBlockSize = 32 * 1000 * 1000;
        maxBlockSigops = maxBlockSize / 50;
    }

    private static TestNet3Params instance;

    public static synchronized TestNet3Params get() {
        if (instance == null) {
            instance = new TestNet3Params();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_TESTNET;
    }

    // February 16th 2012
    private static final Date testnetDiffDate = new Date(1329264000000L);

    public static boolean isValidTestnetDateBlock(Block block) {
        return block.getTime().after(testnetDiffDate);
    }
}
