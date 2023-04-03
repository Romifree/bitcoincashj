/*
 * Copyright 2013 Google Inc.
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

package org.bitcoincashj.wallet;

import org.bitcoincashj.core.*;
import org.bitcoincashj.params.RegTestParams;
import org.bitcoincashj.params.UnitTestParams;
import org.bitcoincashj.testing.FakeTxBuilder;
import org.bitcoincashj.testing.TestWithWallet;
import org.bitcoincashj.wallet.selector.SmallestValueCoinSelector;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.bitcoincashj.core.Coin.CENT;
import static org.bitcoincashj.core.Coin.COIN;
import static org.junit.Assert.*;

public class SmallestCoinSelectorTest extends TestWithWallet {
    private static final NetworkParameters UNITTEST = UnitTestParams.get();
    private static final NetworkParameters REGTEST = RegTestParams.get();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        Utils.setMockClock(); // Use mock clock
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void selectable() throws Exception {
        Transaction t;
        t = new Transaction(UNITTEST);
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        assertFalse(SmallestValueCoinSelector.isSelectable(t));
        t.getConfidence().setSource(TransactionConfidence.Source.SELF);
        assertFalse(SmallestValueCoinSelector.isSelectable(t));
        t.getConfidence().markBroadcastBy(new PeerAddress(UNITTEST, InetAddress.getByName("1.2.3.4")));
        assertTrue(SmallestValueCoinSelector.isSelectable(t));
        t.getConfidence().markBroadcastBy(new PeerAddress(UNITTEST, InetAddress.getByName("5.6.7.8")));
        assertTrue(SmallestValueCoinSelector.isSelectable(t));
        t = new Transaction(UNITTEST);
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
        assertTrue(SmallestValueCoinSelector.isSelectable(t));
        t = new Transaction(REGTEST);
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        t.getConfidence().setSource(TransactionConfidence.Source.SELF);
        assertTrue(SmallestValueCoinSelector.isSelectable(t));
    }

    @Test
    public void smallestValueOrdering() throws Exception {
        // Send two transactions in two blocks on top of each other.
        Transaction t1 = checkNotNull(sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, COIN.divide(2)));
        Transaction t2 = checkNotNull(sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, COIN.divide(4)));
        Transaction t3 = checkNotNull(sendMoneyToWallet(AbstractBlockChain.NewBlockType.BEST_CHAIN, COIN.divide(5)));

        // Check we selected just the smallest one.
        SmallestValueCoinSelector selector = SmallestValueCoinSelector.get();
        CoinSelection selection = selector.select(COIN.divide(10).multiply(9), wallet.calculateAllSpendCandidates());
        assertTrue(selection.gathered.contains(t1.getOutputs().get(0)));
        assertEquals(COIN.divide(2).add(COIN.divide(4)).add(COIN.divide(5)), selection.valueGathered);

        // Check we ordered them correctly (by value).
        ArrayList<TransactionOutput> candidates = new ArrayList<>();
        candidates.add(t2.getOutput(0));
        candidates.add(t1.getOutput(0));
        candidates.add(t3.getOutput(0));
        SmallestValueCoinSelector.sortOutputs(candidates);

        assertEquals(t3.getOutput(0), candidates.get(0));
        assertEquals(t2.getOutput(0), candidates.get(1));
        assertEquals(t1.getOutput(0), candidates.get(2));
    }

    @Test
    public void identicalInputs() throws Exception {
        // Add four outputs to a transaction with same value and destination. Select them all.
        Transaction t = new Transaction(UNITTEST);
        java.util.List<TransactionOutput> outputs = Arrays.asList(
                new TransactionOutput(UNITTEST, t, Coin.valueOf(30302787), myAddress),
                new TransactionOutput(UNITTEST, t, Coin.valueOf(30302787), myAddress),
                new TransactionOutput(UNITTEST, t, Coin.valueOf(30302787), myAddress),
                new TransactionOutput(UNITTEST, t, Coin.valueOf(30302787), myAddress)
        );
        t.getConfidence().setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);

        SmallestValueCoinSelector selector = SmallestValueCoinSelector.get();
        CoinSelection selection = selector.select(COIN.multiply(2), outputs);

        assertTrue(selection.gathered.size() == 4);
    }
}
