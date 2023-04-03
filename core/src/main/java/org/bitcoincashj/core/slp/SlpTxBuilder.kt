package org.bitcoincashj.core.slp

import io.reactivex.Single
import org.bitcoincashj.core.*
import org.bitcoincashj.core.slp.nft.NonFungibleSlpToken
import org.bitcoincashj.core.slp.opreturn.NftOpReturnOutputGenesis
import org.bitcoincashj.core.slp.opreturn.NftOpReturnOutputSend
import org.bitcoincashj.core.slp.opreturn.SlpOpReturnOutputSend
import org.bitcoincashj.kits.WalletKitCore
import org.bitcoincashj.protocols.payments.slp.SlpPaymentSession
import org.bitcoincashj.script.Script
import org.bitcoincashj.script.ScriptBuilder
import org.bitcoincashj.script.ScriptChunk
import org.bitcoincashj.script.ScriptOpCodes
import org.bitcoincashj.wallet.SendRequest
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.util.encoders.Hex
import java.math.BigDecimal
import java.nio.ByteBuffer

class SlpTxBuilder {
    companion object {
        @JvmStatic
        fun buildTx(tokenId: String, amount: Double, toAddress: String, walletKit: WalletKitCore, aesKey: KeyParameter?, allowUnconfirmed: Boolean): Single<Transaction> {
            return sendTokenUtxoSelection(tokenId, amount, walletKit)
                    .map {
                        val addrTo = SlpAddressFactory.create().getFromFormattedAddress(walletKit.wallet().params, toAddress).toCash()
                        // Add OP RETURN and receiver output
                        val req = SendRequest.createSlpTransaction(walletKit.wallet().params)

                        if (allowUnconfirmed)
                            req.allowUnconfirmed()

                        req.aesKey = aesKey
                        req.shuffleOutputs = false
                        req.utxos = null
                        req.feePerKb = Coin.valueOf(1000L)

                        val opReturn = if (it.quantities.size == 1) {
                            SlpOpReturnOutputSend(
                                it.tokenId,
                                it.quantities[0].toLong(),
                                0
                            )
                        } else {
                            SlpOpReturnOutputSend(
                                it.tokenId,
                                it.quantities[0].toLong(),
                                it.quantities[1].toLong()
                            )
                        }

                        req.tx.addOutput(Coin.ZERO, opReturn.script)
                        req.tx.addOutput(walletKit.wallet().params.minNonDustOutput, addrTo)

                        // Send our token change back to our SLP address
                        if (it.quantities.size == 2) {
                            req.tx.addOutput(walletKit.wallet().params.minNonDustOutput, CashAddressFactory.create().getFromFormattedAddress(walletKit.wallet().params, walletKit.freshSlpChangeAddress().toCash().toString()))
                        }

                        // Send our BCH change back to our BCH address
                        if (it.changeSatoshi >= DUST_LIMIT) {
                            req.tx.addOutput(Coin.valueOf(it.changeSatoshi), walletKit.wallet().freshChangeAddress())
                        }

                        it.selectedUtxos.forEach { req.tx.addInput(it) }
                        walletKit.wallet().signTransaction(req)
                        walletKit.wallet().commitTx(req.tx)
                        val tx = req.tx
                        tx
                    }
        }

        @JvmStatic
        fun buildNftChildSendTx(nftTokenId: String, amount: Double, toAddress: String, walletKit: WalletKitCore, aesKey: KeyParameter?, allowUnconfirmed: Boolean): Single<Transaction> {
            return sendNftUtxoSelection(nftTokenId, amount, walletKit)
                .map {
                    val addrTo = SlpAddressFactory.create().getFromFormattedAddress(walletKit.wallet().params, toAddress).toCash()
                    // Add OP RETURN and receiver output
                    val req = SendRequest.createSlpTransaction(walletKit.wallet().params)

                    if (allowUnconfirmed)
                        req.allowUnconfirmed()

                    req.aesKey = aesKey
                    req.shuffleOutputs = false
                    req.utxos = null
                    req.feePerKb = Coin.valueOf(1000L)

                    val opReturn = if (it.quantities.size == 1) {
                        NftOpReturnOutputSend(
                            it.tokenId,
                            it.quantities[0].toLong(),
                            0
                        )
                    } else {
                        NftOpReturnOutputSend(
                            it.tokenId,
                            it.quantities[0].toLong(),
                            it.quantities[1].toLong()
                        )
                    }

                    req.tx.addOutput(Coin.ZERO, opReturn.script)
                    req.tx.addOutput(walletKit.wallet().params.minNonDustOutput, addrTo)

                    // Send our token change back to our SLP address
                    if (it.quantities.size == 2) {
                        req.tx.addOutput(walletKit.wallet().params.minNonDustOutput, CashAddressFactory.create().getFromFormattedAddress(walletKit.wallet().params, walletKit.freshSlpChangeAddress().toCash().toString()))
                    }

                    // Send our BCH change back to our BCH address
                    if (it.changeSatoshi >= DUST_LIMIT) {
                        req.tx.addOutput(Coin.valueOf(it.changeSatoshi), walletKit.wallet().freshChangeAddress())
                    }

                    it.selectedUtxos.forEach { req.tx.addInput(it) }
                    walletKit.wallet().signTransaction(req)
                    walletKit.wallet().commitTx(req.tx)
                    val tx = req.tx
                    tx
                }
        }

        @JvmStatic
        fun buildNftChildGenesisTx(nftParentId: String, ticker: String, name: String, url: String, walletKit: WalletKitCore, aesKey: KeyParameter?, allowUnconfirmed: Boolean): SendRequest {
            val filteredUtxos = walletKit.nftParentUtxos.filter { it.tokenId == nftParentId && it.tokenAmountRaw == 1L }
            val doesValidParentUtxoCandidateExist = filteredUtxos.any()
            if(doesValidParentUtxoCandidateExist) {
                val selectedNftParentUtxo = filteredUtxos.first()
                // Add OP RETURN and receiver output
                val req = SendRequest.createSlpTransaction(walletKit.params())
                if (allowUnconfirmed) {
                    req.allowUnconfirmed()
                }
                req.aesKey = aesKey
                req.shuffleOutputs = false
                req.feePerKb = Coin.valueOf(1000L)
                req.ensureMinRequiredFee = true
                val slpOpReturn = NftOpReturnOutputGenesis(ticker, name, url, 0, 1)
                req.tx.addInput(selectedNftParentUtxo.txUtxo)
                req.tx.addOutput(Coin.ZERO, slpOpReturn.script)
                req.tx.addOutput(walletKit.params().minNonDustOutput, walletKit.wallet().currentChangeAddress())
                return req
            } else {
                throw IllegalArgumentException("Please setup proper NFT1-Parent UTXOs for NFT1-Child generation.")
            }
        }

        @JvmStatic
        fun buildTxBip70(tokenId: String, walletKit: WalletKitCore, aesKey: KeyParameter?, rawTokens: List<Long>, addresses: List<String>, paymentSession: SlpPaymentSession, allowUnconfirmed: Boolean): Single<Transaction> {
            return sendTokenUtxoSelectionBip70(tokenId, rawTokens, walletKit)
                    .map {
                        // Add OP RETURN and receiver output
                        val req = SendRequest.createSlpTransaction(walletKit.wallet().params)

                        if (allowUnconfirmed)
                            req.allowUnconfirmed()

                        req.aesKey = aesKey
                        req.shuffleOutputs = false
                        req.utxos = null
                        req.feePerKb = Coin.valueOf(1000L)

                        var opReturnScript: Script = paymentSession.slpOpReturn
                        val lokad = byteArrayOf(83, 76, 80, 0)
                        val type = byteArrayOf(1)
                        val PUSHDATA_BYTES = 8
                        var scriptBuilder = ScriptBuilder()
                                .op(ScriptOpCodes.OP_RETURN)
                                .data(lokad)
                                .addChunk(ScriptChunk(type.size, type))
                                .data("SEND".toByteArray())
                                .data(Hex.decode(tokenId))
                        for (x in rawTokens.indices) {
                            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(rawTokens[x]).array())
                        }

                        if (it.quantities.size == 2) {
                            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(it.quantities[1].toLong()).array())
                        }

                        opReturnScript = scriptBuilder.build()

                        req.tx.addOutput(Coin.ZERO, opReturnScript)

                        for (x in addresses.indices) {
                            val addrTo = SlpAddressFactory.create().getFromFormattedAddress(walletKit.wallet().params, addresses[x]).toCash()
                            req.tx.addOutput(walletKit.wallet().params.minNonDustOutput, addrTo)
                        }

                        // Send our token change back to our SLP address
                        if (it.quantities.size == 2) {
                            req.tx.addOutput(walletKit.wallet().params.minNonDustOutput, walletKit.currentSlpChangeAddress().toCash())
                        }

                        // Send our BCH change back to our BCH address
                        if (it.changeSatoshi >= DUST_LIMIT) {
                            req.tx.addOutput(Coin.valueOf(it.changeSatoshi), walletKit.wallet().freshChangeAddress())
                        }

                        it.selectedUtxos.forEach { req.tx.addInput(it) }
                        walletKit.wallet().signTransaction(req)
                        walletKit.wallet().commitTx(req.tx)
                        val tx = req.tx
                        tx
                    }
        }

        private val OP_RETURN_NUM_BYTES_BASE: Int = 55
        private val QUANTITY_NUM_BYTES: Int = 9
        val maxRawAmount = BigDecimal(ULong.MAX_VALUE.toString())
        const val DUST_LIMIT: Long = 546

        private fun sendTokenUtxoSelection(tokenId: String, numTokens: Double, walletKit: WalletKitCore): Single<SendTokenUtxoSelection> {
            val tokenDetails: SlpToken = walletKit.getSlpToken(tokenId)
            val sendTokensRaw = toRawAmount(numTokens.toBigDecimal(), tokenDetails).toLong()
            return slpTokenUtxoSelection(tokenId, listOf(sendTokensRaw), walletKit.slpUtxos, walletKit)
        }

        private fun sendNftUtxoSelection(nftTokenId: String, numTokens: Double, walletKit: WalletKitCore): Single<SendTokenUtxoSelection> {
            val tokenDetails: NonFungibleSlpToken = walletKit.getNft(nftTokenId)
            val sendTokensRaw = toRawAmount(numTokens.toBigDecimal(), tokenDetails).toLong()
            return slpTokenUtxoSelection(nftTokenId, listOf(sendTokensRaw), walletKit.nftUtxos, walletKit)
        }

        private fun sendTokenUtxoSelectionBip70(tokenId: String, tokensRaw: List<Long>, walletKit: WalletKitCore): Single<SendTokenUtxoSelection> {
            return slpTokenUtxoSelection(tokenId, tokensRaw, walletKit.slpUtxos, walletKit)
        }

        private fun slpTokenUtxoSelection(tokenId: String, tokensRaw: List<Long>, slpUtxos: ArrayList<SlpUTXO>, walletKit: WalletKitCore): Single<SendTokenUtxoSelection> {
            return Single.fromCallable {
                var numTokens: Long = 0
                var sendSatoshi: Long = 0
                for (x in tokensRaw.indices) {
                    numTokens += tokensRaw[x]
                    sendSatoshi += DUST_LIMIT

                }
                val sendTokensRaw = numTokens.toULong()
                val utxos = walletKit.wallet().utxos

                // First select enough token utxo's and just take what we get in terms of BCH
                var inputTokensRaw = ULong.MIN_VALUE
                var inputSatoshi = 0L
                val selectedUtxos = slpUtxos
                    .asSequence()
                    .filter { it.tokenId == tokenId }
                    .sortedBy { it.tokenAmountRaw }
                    .takeWhile {
                        val amountTooLow = inputTokensRaw < sendTokensRaw
                        if (amountTooLow) {
                            inputTokensRaw += it.tokenAmountRaw.toULong()
                            inputSatoshi += (it.txUtxo.value.value - 148) // Deduct input fee
                        }
                        amountTooLow
                    }
                    .map { it.txUtxo }
                    .toMutableList()
                if (inputTokensRaw < sendTokensRaw) {
                    throw RuntimeException("insufficient token balance=$inputTokensRaw")
                } else if (inputTokensRaw > sendTokensRaw) {
                    // If there's token change we need at least another dust limit worth of BCH
                    sendSatoshi += DUST_LIMIT
                }

                val propagationExtraFee = (tokensRaw.size + 1) * 50 // When too close 1sat/byte tx's don't propagate well
                val numOutputs = tokensRaw.size + 1 // Assume outputs = tokens raw array + change, in addition to the OP_RETURN
                val numQuanitites = tokensRaw.size // Assume tokens amount = tokens raw array
                val fee = outputFee(numOutputs) + sizeInBytes(numQuanitites) + propagationExtraFee

                // If we can not yet afford the fee + dust limit to send, use pure BCH utxo's
                selectedUtxos.addAll(utxos
                    .sortedBy { it.value.value }
                    .takeWhile {
                        val amountTooLow = inputSatoshi <= (sendSatoshi + fee)
                        if (amountTooLow) {
                            inputSatoshi += (it.value.value - 148) // Deduct input fee
                        }
                        amountTooLow
                    })

                val changeSatoshi = inputSatoshi - sendSatoshi - fee
                if (changeSatoshi < 0) {
                    throw IllegalArgumentException("Insufficient BCH balance=$inputSatoshi required $sendSatoshi + fees")
                }

                // We have enough tokens and BCH. Create the transaction
                val quantities = mutableListOf(sendTokensRaw)
                val changeTokens = inputTokensRaw - sendTokensRaw
                if (changeTokens > 0u) {
                    quantities.add(changeTokens)
                }

                SendTokenUtxoSelection(tokenId, quantities, changeSatoshi, selectedUtxos)
            }
        }

        fun toRawAmount(amount: BigDecimal, slpToken: SlpToken): ULong {
            var amt = amount
            if (amt > maxRawAmount) {
                throw IllegalArgumentException("amount larger than 8 unsigned bytes")
            } else if (slpToken.decimals == 0) {
                amt = amount.toInt().toBigDecimal()
            } else if (amt.scale() - 1 > slpToken.decimals) {
                throw IllegalArgumentException("${slpToken.ticker} supports maximum ${slpToken.decimals} decimals but amount is $amount")
            }
            return amt.scaleByPowerOfTen(slpToken.decimals).toLong().toULong()
        }

        fun outputFee(numOutputs: Int): Long {
            return numOutputs.toLong() * 34
        }

        fun sizeInBytes(numQuantities: Int): Int {
            return OP_RETURN_NUM_BYTES_BASE + numQuantities * QUANTITY_NUM_BYTES
        }

        data class SendTokenUtxoSelection(
                val tokenId: String, val quantities: List<ULong>, val changeSatoshi: Long,
                val selectedUtxos: List<TransactionOutput>
        )
    }
}