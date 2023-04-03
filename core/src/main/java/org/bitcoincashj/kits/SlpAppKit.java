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

package org.bitcoincashj.kits;

import org.bitcoincashj.core.*;
import org.bitcoincashj.core.slp.*;
import org.bitcoincashj.core.slp.nft.NonFungibleSlpToken;
import org.bitcoincashj.core.slp.opreturn.SlpOpReturnOutputGenesis;
import org.bitcoincashj.net.SlpDbNftDetails;
import org.bitcoincashj.net.SlpDbProcessor;
import org.bitcoincashj.net.SlpDbTokenDetails;
import org.bitcoincashj.net.SlpDbValidTransaction;
import org.bitcoincashj.protocols.payments.slp.SlpPaymentSession;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.store.SPVBlockStore;
import org.bitcoincashj.wallet.KeyChainGroupStructure;
import org.bitcoincashj.wallet.SendRequest;
import org.bitcoincashj.wallet.Wallet;
import org.bouncycastle.crypto.params.KeyParameter;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * <p>Utility class that wraps the boilerplate needed to set up a new SPV bitcoincashj app. Instantiate it with a directory
 * and file prefix, optionally configure a few things, then use startAsync and optionally awaitRunning. The object will
 * construct and configure a {@link BlockChain}, {@link SPVBlockStore}, {@link Wallet} and {@link PeerGroup}. Depending
 * on the value of the blockingStartup property, startup will be considered complete once the block chain has fully
 * synchronized, so it can take a while.</p>
 *
 * <p>To add listeners and modify the objects that are constructed, you can either do that by overriding the
 * {@link #onSetupCompleted()} method (which will run on a background thread) and make your changes there,
 * or by waiting for the service to start and then accessing the objects from wherever you want. However, you cannot
 * access the objects this class creates until startup is complete.</p>
 *
 * <p>The asynchronous design of this class may seem puzzling (just use {@link #awaitRunning()} if you don't want that).
 * It is to make it easier to fit bitcoincashj into GUI apps, which require a high degree of responsiveness on their main
 * thread which handles all the animation and user interaction. Even when blockingStart is false, initializing bitcoincashj
 * means doing potentially blocking file IO, generating keys and other potentially intensive operations. By running it
 * on a background thread, there's no risk of accidentally causing UI lag.</p>
 *
 * <p>Note that {@link #awaitRunning()} can throw an unchecked {@link IllegalStateException}
 * if anything goes wrong during startup - you should probably handle it and use {@link Exception#getCause()} to figure
 * out what went wrong more precisely. Same thing if you just use the {@link #startAsync()} method.</p>
 */
public class SlpAppKit extends WalletKitCore {
    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public SlpAppKit(NetworkParameters params, File directory, String filePrefix) {
        this(new Context(params), Script.ScriptType.P2PKH, null, directory, filePrefix);
    }

    /**
     * Creates a new WalletAppKit, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public SlpAppKit(NetworkParameters params, Script.ScriptType preferredOutputScriptType,
                     @Nullable KeyChainGroupStructure structure, File directory, String filePrefix) {
        this(new Context(params), preferredOutputScriptType, structure, directory, filePrefix);
    }

    /**
     * Creates a new WalletAppKit, with the given {@link Context}. Files will be stored in the given directory.
     */
    public SlpAppKit(Context context, Script.ScriptType preferredOutputScriptType,
                     @Nullable KeyChainGroupStructure structure, File directory, String filePrefix) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.preferredOutputScriptType = checkNotNull(preferredOutputScriptType);
        this.structure = structure != null ? structure : KeyChainGroupStructure.SLP;
        this.directory = checkNotNull(directory);
        this.filePrefix = checkNotNull(filePrefix);
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        File txsDataFile = new File(this.directory(), this.filePrefix + ".txs");
        if (txsDataFile.exists()) {
            this.loadRecordedTxs();
        }
        File tokenDataFile = new File(this.directory(), this.filePrefix + ".tokens");
        this.tokensFile = tokenDataFile;
        if (tokenDataFile.exists()) {
            this.loadTokens();
        }
        File nftDataFile = new File(this.directory(), this.filePrefix + ".nfts");
        this.nftsFile = nftDataFile;
        if (nftDataFile.exists()) {
            this.loadNfts();
        }

        this.slpDbProcessor = new SlpDbProcessor();
    }
}
