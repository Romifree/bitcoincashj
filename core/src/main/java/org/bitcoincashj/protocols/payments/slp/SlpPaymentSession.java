/*
 * Copyright by the original author or authors.
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

package org.bitcoincashj.protocols.payments.slp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoincash.protocols.payments.Protos;
import org.bitcoincashj.core.*;
import org.bitcoincashj.core.slp.SlpAddress;
import org.bitcoincashj.crypto.TrustStoreLoader;
import org.bitcoincashj.params.MainNetParams;
import org.bitcoincashj.protocols.payments.PaymentProtocolException;
import org.bitcoincashj.protocols.payments.slp.SlpPaymentProtocol.PkiVerificationData;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.ScriptChunk;
import org.bitcoincashj.script.ScriptPattern;
import org.bitcoincashj.uri.BitcoinURI;
import org.bitcoincashj.utils.Threading;
import org.bitcoincashj.wallet.SendRequest;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * <p>Provides a standard implementation of the Payment Protocol (BIP 0070)</p>
 *
 * <p>A PaymentSession can be initialized from one of the following:</p>
 *
 * <ul>
 * <li>A {@link BitcoinURI} object that conforms to BIP 0072</li>
 * <li>A url where the {@link Protos.PaymentRequest} can be fetched</li>
 * <li>Directly with a {@link Protos.PaymentRequest} object</li>
 * </ul>
 *
 * <p>If initialized with a BitcoinURI or a url, a network request is made for the payment request object and a
 * ListenableFuture is returned that will be notified with the PaymentSession object after it is downloaded.</p>
 *
 * <p>Once the PaymentSession is initialized, typically a wallet application will prompt the user to confirm that the
 * amount and recipient are correct, perform any additional steps, and then construct a list of transactions to pass to
 * the sendPayment method.</p>
 *
 * <p>Call sendPayment with a list of transactions that will be broadcast. A {@link Protos.Payment} message will be sent
 * to the merchant if a payment url is provided in the PaymentRequest. NOTE: sendPayment does NOT broadcast the
 * transactions to the bitcoin network. Instead it returns a ListenableFuture that will be notified when a
 * {@link Protos.PaymentACK} is received from the merchant. Typically a wallet will show the message to the user
 * as a confirmation message that the payment is now "processing" or that an error occurred, and then broadcast the
 * tx itself later if needed.</p>
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0070.mediawiki">BIP 0070</a>
 */
public class SlpPaymentSession {
    private static ListeningExecutorService executor = Threading.THREAD_POOL;
    private NetworkParameters params;
    private Protos.PaymentRequest paymentRequest;
    private Protos.PaymentDetails paymentDetails;
    private Coin totalValue = Coin.ZERO;

    /**
     * Stores the calculated PKI verification data, or null if none is available.
     * Only valid after the session is created with the verifyPki parameter set to true.
     */
    @Nullable
    public final PkiVerificationData pkiVerificationData;

    /**
     * <p>Returns a future that will be notified with a PaymentSession object after it is fetched using the provided uri.
     * uri is a BIP-72-style BitcoinURI object that specifies where the {@link Protos.PaymentRequest} object may
     * be fetched in the r= parameter.</p>
     *
     * <p>If the payment request object specifies a PKI method, then the system trust store will be used to verify
     * the signature provided by the payment request. An exception is thrown by the future if the signature cannot
     * be verified.</p>
     */
    public static ListenableFuture<SlpPaymentSession> createFromBitcoinUri(final BitcoinURI uri) throws PaymentProtocolException {
        return createFromBitcoinUri(uri, true, null);
    }

    /**
     * Returns a future that will be notified with a PaymentSession object after it is fetched using the provided uri.
     * uri is a BIP-72-style BitcoinURI object that specifies where the {@link Protos.PaymentRequest} object may
     * be fetched in the r= parameter.
     * If verifyPki is specified and the payment request object specifies a PKI method, then the system trust store will
     * be used to verify the signature provided by the payment request. An exception is thrown by the future if the
     * signature cannot be verified.
     */
    public static ListenableFuture<SlpPaymentSession> createFromBitcoinUri(final BitcoinURI uri, final boolean verifyPki)
            throws PaymentProtocolException {
        return createFromBitcoinUri(uri, verifyPki, null);
    }

    /**
     * Returns a future that will be notified with a PaymentSession object after it is fetched using the provided uri.
     * uri is a BIP-72-style BitcoinURI object that specifies where the {@link Protos.PaymentRequest} object may
     * be fetched in the r= parameter.
     * If verifyPki is specified and the payment request object specifies a PKI method, then the system trust store will
     * be used to verify the signature provided by the payment request. An exception is thrown by the future if the
     * signature cannot be verified.
     * If trustStoreLoader is null, the system default trust store is used.
     */
    public static ListenableFuture<SlpPaymentSession> createFromBitcoinUri(final BitcoinURI uri, final boolean verifyPki, @Nullable final TrustStoreLoader trustStoreLoader)
            throws PaymentProtocolException {
        String url = uri.getPaymentRequestUrl();
        if (url == null)
            throw new PaymentProtocolException.InvalidPaymentRequestURL("No payment request URL (r= parameter) in BitcoinURI " + uri);
        try {
            return fetchPaymentRequest(new URI(url), verifyPki, trustStoreLoader);
        } catch (URISyntaxException e) {
            throw new PaymentProtocolException.InvalidPaymentRequestURL(e);
        }
    }

    /**
     * Returns a future that will be notified with a PaymentSession object after it is fetched using the provided url.
     * url is an address where the {@link Protos.PaymentRequest} object may be fetched.
     * If verifyPki is specified and the payment request object specifies a PKI method, then the system trust store will
     * be used to verify the signature provided by the payment request. An exception is thrown by the future if the
     * signature cannot be verified.
     */
    public static ListenableFuture<SlpPaymentSession> createFromUrl(final String url) throws PaymentProtocolException {
        return createFromUrl(url, true, null);
    }

    /**
     * Returns a future that will be notified with a PaymentSession object after it is fetched using the provided url.
     * url is an address where the {@link Protos.PaymentRequest} object may be fetched.
     * If the payment request object specifies a PKI method, then the system trust store will
     * be used to verify the signature provided by the payment request. An exception is thrown by the future if the
     * signature cannot be verified.
     */
    public static ListenableFuture<SlpPaymentSession> createFromUrl(final String url, final boolean verifyPki)
            throws PaymentProtocolException {
        return createFromUrl(url, verifyPki, null);
    }

    /**
     * Returns a future that will be notified with a PaymentSession object after it is fetched using the provided url.
     * url is an address where the {@link Protos.PaymentRequest} object may be fetched.
     * If the payment request object specifies a PKI method, then the system trust store will
     * be used to verify the signature provided by the payment request. An exception is thrown by the future if the
     * signature cannot be verified.
     * If trustStoreLoader is null, the system default trust store is used.
     */
    public static ListenableFuture<SlpPaymentSession> createFromUrl(final String url, final boolean verifyPki, @Nullable final TrustStoreLoader trustStoreLoader)
            throws PaymentProtocolException {
        if (url == null)
            throw new PaymentProtocolException.InvalidPaymentRequestURL("null paymentRequestUrl");
        try {
            return fetchPaymentRequest(new URI(url), verifyPki, trustStoreLoader);
        } catch (URISyntaxException e) {
            throw new PaymentProtocolException.InvalidPaymentRequestURL(e);
        }
    }

    private static ListenableFuture<SlpPaymentSession> fetchPaymentRequest(final URI uri, final boolean verifyPki, @Nullable final TrustStoreLoader trustStoreLoader) {
        return executor.submit(new Callable<SlpPaymentSession>() {
            @Override
            public SlpPaymentSession call() throws Exception {
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setRequestProperty("Accept", SlpPaymentProtocol.MIMETYPE_PAYMENTREQUEST);
                connection.setUseCaches(false);
                Protos.PaymentRequest paymentRequest = Protos.PaymentRequest.parseFrom(connection.getInputStream());
                return new SlpPaymentSession(paymentRequest, verifyPki, trustStoreLoader);
            }
        });
    }

    /**
     * Creates a PaymentSession from the provided {@link Protos.PaymentRequest}.
     * Verifies PKI by default.
     */
    public SlpPaymentSession(Protos.PaymentRequest request) throws PaymentProtocolException {
        this(request, true, null);
    }

    /**
     * Creates a PaymentSession from the provided {@link Protos.PaymentRequest}.
     * If verifyPki is true, also validates the signature and throws an exception if it fails.
     */
    public SlpPaymentSession(Protos.PaymentRequest request, boolean verifyPki) throws PaymentProtocolException {
        this(request, verifyPki, null);
    }

    /**
     * Creates a PaymentSession from the provided {@link Protos.PaymentRequest}.
     * If verifyPki is true, also validates the signature and throws an exception if it fails.
     * If trustStoreLoader is null, the system default trust store is used.
     */
    public SlpPaymentSession(Protos.PaymentRequest request, boolean verifyPki, @Nullable final TrustStoreLoader trustStoreLoader) throws PaymentProtocolException {
        TrustStoreLoader nonNullTrustStoreLoader = trustStoreLoader != null ? trustStoreLoader : new TrustStoreLoader.DefaultTrustStoreLoader();
        parsePaymentRequest(request);
        if (verifyPki) {
            try {
                pkiVerificationData = SlpPaymentProtocol.verifyPaymentRequestPki(request, nonNullTrustStoreLoader.getKeyStore());
            } catch (IOException x) {
                throw new PaymentProtocolException(x);
            } catch (KeyStoreException x) {
                throw new PaymentProtocolException(x);
            }
        } else {
            pkiVerificationData = null;
        }
    }

    /**
     * Returns the outputs of the payment request.
     */
    public List<SlpPaymentProtocol.Output> getOutputs() {
        List<SlpPaymentProtocol.Output> outputs = new ArrayList<>(paymentDetails.getOutputsCount());
        for (Protos.Output output : paymentDetails.getOutputsList()) {
            Coin amount = output.hasAmount() ? Coin.valueOf(output.getAmount()) : null;
            outputs.add(new SlpPaymentProtocol.Output(amount, output.getScript().toByteArray()));
        }
        return outputs;
    }

    /**
     * Returns the memo included by the merchant in the payment request, or null if not found.
     */
    @Nullable
    public String getMemo() {
        if (paymentDetails.hasMemo())
            return paymentDetails.getMemo();
        else
            return null;
    }

    /**
     * Returns the total amount of bitcoins requested.
     */
    public Coin getValue() {
        return totalValue;
    }

    /**
     * Returns the date that the payment request was generated.
     */
    public Date getDate() {
        return new Date(paymentDetails.getTime() * 1000);
    }

    /**
     * Returns the expires time of the payment request, or null if none.
     */
    @Nullable
    public Date getExpires() {
        if (paymentDetails.hasExpires())
            return new Date(paymentDetails.getExpires() * 1000);
        else
            return null;
    }

    /**
     * This should always be called before attempting to call sendPayment.
     */
    public boolean isExpired() {
        return paymentDetails.hasExpires() && Utils.currentTimeSeconds() > paymentDetails.getExpires();
    }

    /**
     * Returns the payment url where the Payment message should be sent.
     * Returns null if no payment url was provided in the PaymentRequest.
     */
    @Nullable
    public String getPaymentUrl() {
        if (paymentDetails.hasPaymentUrl())
            return paymentDetails.getPaymentUrl();
        return null;
    }

    /**
     * Returns the merchant data included by the merchant in the payment request, or null if none.
     */
    @Nullable
    public byte[] getMerchantData() {
        if (paymentDetails.hasMerchantData())
            return paymentDetails.getMerchantData().toByteArray();
        else
            return null;
    }

    /**
     * Returns a {@link SendRequest} suitable for broadcasting to the network.
     */
    public SendRequest getSendRequest() {
        Transaction tx = new Transaction(params);
        for (Protos.Output output : paymentDetails.getOutputsList())
            tx.addOutput(new TransactionOutput(params, tx, Coin.valueOf(output.getAmount()), output.getScript().toByteArray()));
        return SendRequest.forTx(tx).fromPaymentDetails(paymentDetails);
    }

    public String getTokenId() {
        String tokenId = "";
        Script opReturn = this.getSlpOpReturn();
        ScriptChunk tokenIdChunk = opReturn.getChunks().get(4);
        if (tokenIdChunk != null) {
            byte[] chunkData = tokenIdChunk.data;
            if (chunkData != null) {
                tokenId = new String(Hex.encode(tokenIdChunk.data), StandardCharsets.UTF_8);
            }
        }

        return tokenId;
    }

    public List<ScriptChunk> getRequiredTokenAmounts() {
        ArrayList<ScriptChunk> tokenChunks = new ArrayList<>();
        Script opReturn = this.getSlpOpReturn();
        int tokenChunkStartIndex = 5;
        for (int x = tokenChunkStartIndex; x < opReturn.getChunks().size(); x++) {
            ScriptChunk tokenAmountChunk = opReturn.getChunks().get(x);
            if (tokenAmountChunk != null) {
                tokenChunks.add(tokenAmountChunk);
            }
        }
        return tokenChunks;
    }

    public long getTotalTokenAmount() {
        long total = 0;
        List<ScriptChunk> tokenAmountChunks = this.getRequiredTokenAmounts();
        for (ScriptChunk tokenAmountChunk : tokenAmountChunks) {
            byte[] chunkData = tokenAmountChunk.data;
            if (chunkData != null) {
                String tokenAmountHex = new String(Hex.encode(chunkData), StandardCharsets.UTF_8);
                long tokenAmountRaw = Long.parseLong(tokenAmountHex, 16);
                total += tokenAmountRaw;
            }
        }

        return total;
    }

    public List<Long> getRawTokenAmounts() {
        ArrayList<Long> rawAmounts = new ArrayList();
        List<ScriptChunk> tokenAmountChunks = this.getRequiredTokenAmounts();
        for (ScriptChunk tokenAmountChunk : tokenAmountChunks) {
            byte[] chunkData = tokenAmountChunk.data;
            if (chunkData != null) {
                String tokenAmountHex = new String(Hex.encode(chunkData), StandardCharsets.UTF_8);
                long tokenAmountRaw = Long.parseLong(tokenAmountHex, 16);
                rawAmounts.add(tokenAmountRaw);
            }
        }

        return rawAmounts;
    }

    public List<String> getSlpAddresses(NetworkParameters params) {
        ArrayList<String> slpAddresses = new ArrayList();
        List<TransactionOutput> txUtxos = this.getSendRequest().tx.getOutputs();
        for (TransactionOutput utxo : txUtxos) {
            if (!ScriptPattern.isOpReturn(utxo.getScriptPubKey())) {
                Address address;
                address = utxo.getAddressFromP2PKHScript(params);
                if (address == null) {
                    address = utxo.getAddressFromP2SH(params);
                }

                if (address != null) {
                    SlpAddress slpAddress = SlpAddressFactory.create().getFromBase58(params, address.toBase58());
                    slpAddresses.add(slpAddress.toString());
                }
            }
        }

        return slpAddresses;
    }

    public Script getSlpOpReturn() {
        Script opReturn = null;

        SendRequest sendRequest = this.getSendRequest();
        for (TransactionOutput utxo : sendRequest.tx.getOutputs()) {
            Script outputScript = utxo.getScriptPubKey();
            if (ScriptPattern.isOpReturn(outputScript)) {
                opReturn = outputScript;
            }
        }

        return opReturn;
    }

    /**
     * Generates a Payment message and sends the payment to the merchant who sent the PaymentRequest.
     * Provide transactions built by the wallet.
     * NOTE: This does not broadcast the transactions to the bitcoin network, it merely sends a Payment message to the
     * merchant confirming the payment.
     * Returns an object wrapping PaymentACK once received.
     * If the PaymentRequest did not specify a payment_url, returns null and does nothing.
     *
     * @param txns       list of transactions to be included with the Payment message.
     * @param refundAddr will be used by the merchant to send money back if there was a problem.
     * @param memo       is a message to include in the payment message sent to the merchant.
     */
    @Nullable
    public ListenableFuture<SlpPaymentProtocol.Ack> sendPayment(List<Transaction> txns, @Nullable Address refundAddr, @Nullable String memo)
            throws PaymentProtocolException, VerificationException, IOException {
        Protos.Payment payment = getPayment(txns, refundAddr, memo);
        if (payment == null)
            return null;
        if (isExpired())
            throw new PaymentProtocolException.Expired("PaymentRequest is expired");
        URL url;
        try {
            url = new URL(paymentDetails.getPaymentUrl());
        } catch (MalformedURLException e) {
            throw new PaymentProtocolException.InvalidPaymentURL(e);
        }
        return sendPayment(url, payment);
    }

    /**
     * Generates a Payment message based on the information in the PaymentRequest.
     * Provide transactions built by the wallet.
     * If the PaymentRequest did not specify a payment_url, returns null.
     *
     * @param txns       list of transactions to be included with the Payment message.
     * @param refundAddr will be used by the merchant to send money back if there was a problem.
     * @param memo       is a message to include in the payment message sent to the merchant.
     */
    @Nullable
    public Protos.Payment getPayment(List<Transaction> txns, @Nullable Address refundAddr, @Nullable String memo)
            throws IOException, PaymentProtocolException.InvalidNetwork {
        if (paymentDetails.hasPaymentUrl()) {
            for (Transaction tx : txns)
                if (!tx.getParams().equals(params))
                    throw new PaymentProtocolException.InvalidNetwork(params.getPaymentProtocolId());
            return SlpPaymentProtocol.createPaymentMessage(txns, totalValue, refundAddr, memo, getMerchantData());
        } else {
            return null;
        }
    }

    @VisibleForTesting
    protected ListenableFuture<SlpPaymentProtocol.Ack> sendPayment(final URL url, final Protos.Payment payment) {
        return executor.submit(new Callable<SlpPaymentProtocol.Ack>() {
            @Override
            public SlpPaymentProtocol.Ack call() throws Exception {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", SlpPaymentProtocol.MIMETYPE_PAYMENT);
                connection.setRequestProperty("Accept", SlpPaymentProtocol.MIMETYPE_PAYMENTACK);
                connection.setRequestProperty("Content-Length", Integer.toString(payment.getSerializedSize()));
                connection.setUseCaches(false);
                connection.setDoInput(true);
                connection.setDoOutput(true);

                // Send request.
                DataOutputStream outStream = new DataOutputStream(connection.getOutputStream());
                payment.writeTo(outStream);
                outStream.flush();
                outStream.close();

                // Get response.
                Protos.PaymentACK paymentAck = Protos.PaymentACK.parseFrom(connection.getInputStream());
                return SlpPaymentProtocol.parsePaymentAck(paymentAck);
            }
        });
    }

    private void parsePaymentRequest(Protos.PaymentRequest request) throws PaymentProtocolException {
        try {
            if (request == null)
                throw new PaymentProtocolException("request cannot be null");
            if (request.getPaymentDetailsVersion() != 1)
                throw new PaymentProtocolException.InvalidVersion("Version 1 required. Received version " + request.getPaymentDetailsVersion());
            paymentRequest = request;
            if (!request.hasSerializedPaymentDetails())
                throw new PaymentProtocolException("No PaymentDetails");
            paymentDetails = Protos.PaymentDetails.newBuilder().mergeFrom(request.getSerializedPaymentDetails()).build();
            if (paymentDetails == null)
                throw new PaymentProtocolException("Invalid PaymentDetails");
            if (!paymentDetails.hasNetwork())
                params = MainNetParams.get();
            else
                params = NetworkParameters.fromPmtProtocolID(paymentDetails.getNetwork());
            if (params == null)
                throw new PaymentProtocolException.InvalidNetwork("Invalid network " + paymentDetails.getNetwork());
            if (paymentDetails.getOutputsCount() < 1)
                throw new PaymentProtocolException.InvalidOutputs("No outputs");
            for (Protos.Output output : paymentDetails.getOutputsList()) {
                if (output.hasAmount())
                    totalValue = totalValue.add(Coin.valueOf(output.getAmount()));
            }
            // This won't ever happen in practice. It would only happen if the user provided outputs
            // that are obviously invalid. Still, we don't want to silently overflow.
            if (params.hasMaxMoney() && totalValue.compareTo(params.getMaxMoney()) > 0)
                throw new PaymentProtocolException.InvalidOutputs("The outputs are way too big.");
        } catch (InvalidProtocolBufferException e) {
            throw new PaymentProtocolException(e);
        }
    }

    /**
     * Returns the value of pkiVerificationData or null if it wasn't verified at construction time.
     */
    @Nullable
    public PkiVerificationData verifyPki() {
        return pkiVerificationData;
    }

    /**
     * Gets the params as read from the PaymentRequest.network field: main is the default if missing.
     */
    public NetworkParameters getNetworkParameters() {
        return params;
    }

    /**
     * Returns the protobuf that this object was instantiated with.
     */
    public Protos.PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    /**
     * Returns the protobuf that describes the payment to be made.
     */
    public Protos.PaymentDetails getPaymentDetails() {
        return paymentDetails;
    }
}
