/*
 * Copyright 2017 Anton Kumaigorodski
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

import java.util.Arrays;

/**
 * <p>
 * A new message, "sendheaders", which indicates that a node prefers to receive new block announcements via a "headers"
 * message rather than an "inv".
 * </p>
 *
 * <p>
 * See <a href="https://github.com/bitcoin/bips/blob/master/bip-0130.mediawiki">BIP 130</a>.
 * </p>
 */
public class DSProofMessage extends EmptyMessage {
    private Sha256Hash id;
    private byte[] txPrevHash;
    private byte[] txPrevIndex;

    public DSProofMessage() {
    }

    // this is needed by the BitcoinSerializer
    public DSProofMessage(NetworkParameters params, byte[] payload) {
        this.id = Sha256Hash.twiceOf(payload);
        this.txPrevHash = Arrays.copyOfRange(payload, 0, 32);
        this.txPrevIndex = Arrays.copyOfRange(payload, 32, 36);
    }

    public Sha256Hash getId() {
        return id;
    }
}
