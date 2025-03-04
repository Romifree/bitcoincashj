/*
 * Copyright 2013 Google Inc.
 * Copyright 2019 Andreas Schildbach
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

/**
 * Network parameters used by the bitcoincashj unit tests (and potentially your own). This lets you solve a block using
 * {@link Block#solve()} by setting difficulty to the easiest possible.
 */
public class UnitTestParams extends AbstractBitcoinNetParams {
    public static final int UNITNET_MAJORITY_WINDOW = 8;
    public static final int TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED = 6;
    public static final int TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 4;

    public UnitTestParams() {
        super();
        id = ID_UNITTESTNET;
        packetMagic = 0x0b110907;
        addressHeader = 111;
        p2shHeader = 196;
        acceptableAddressCodes = new int[]{addressHeader, p2shHeader};
        maxTarget = Utils.decodeCompactBits(0x1d00ffffL);
        genesisBlock.setTime(Utils.currentTimeSeconds());
        genesisBlock.setDifficultyTarget(Block.EASIEST_DIFFICULTY_TARGET);
        genesisBlock.solve();
        port = 18333;
        interval = 10;
        dumpedPrivateKeyHeader = 239;
        targetTimespan = 200000000;  // 6 years. Just a very big number.
        spendableCoinbaseDepth = 5;
        subsidyDecreaseBlockCount = 100;
        dnsSeeds = null;
        addrSeeds = null;
        bip32HeaderP2PKHpub = 0x043587cf; // The 4 byte header that serializes in base58 to "tpub".
        bip32HeaderP2PKHpriv = 0x04358394; // The 4 byte header that serializes in base58 to "tprv"

        majorityEnforceBlockUpgrade = 3;
        majorityRejectBlockOutdated = 4;
        majorityWindow = 7;

        asertReferenceBlockBits = 0x00000000;
        asertReferenceBlockHeight = BigInteger.ZERO;
        asertReferenceBlockAncestorTime = BigInteger.ZERO;
        asertUpdateTime = 1597096200L;

        cashAddrPrefix = "bchtest";
        simpleledgerPrefix = "slptest";

        asertHalfLife = 60L * 60L;
        allowMinDifficultyBlocks = true;
        maxBlockSize = 32 * 1000 * 1000;
        maxBlockSigops = maxBlockSize / 50;
    }

    private static UnitTestParams instance;

    public static synchronized UnitTestParams get() {
        if (instance == null) {
            instance = new UnitTestParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return "unittest";
    }
}
