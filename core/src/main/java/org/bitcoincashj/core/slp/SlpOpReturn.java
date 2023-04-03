package org.bitcoincashj.core.slp;

import org.bitcoincashj.core.Transaction;
import org.bitcoincashj.core.TransactionOutput;
import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.ScriptChunk;
import org.bitcoincashj.script.ScriptPattern;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class SlpOpReturn {
    public static final String slpProtocolId = "534c5000";
    public static final String tokenType1Id = "01";
    public static final String nft1ParentTypeId = "81";
    public static final String nft1ChildTypeId = "41";
    public static final String genesisTxTypeId = "47454e45534953";
    public static final String mintTxTypeId = "4d494e54";
    public static final String sendTxTypeId = "53454e44";
    public static final int opReturnLocation = 0;
    public static final int protocolChunkLocation = 1;
    public static final int slpTokenTypeChunkLocation = 2;
    public static final int slpTxTypeChunkLocation = 3;
    public static final int tokenIdChunkLocation = 4;
    public static final int mintBatonVoutGenesisChunkLocation = 9;
    public static final int mintBatonVoutMintChunkLocation = 5;
    public static final int tokenOutputsStartLocation = 5;

    public enum SlpTokenType {
        SLP,
        NFT
    }

    public enum SlpTxType {
        GENESIS,
        MINT,
        SEND,
        NFT_PARENT_GENESIS,
        NFT_PARENT_MINT,
        NFT_PARENT_SEND,
        NFT_CHILD_GENESIS,
        NFT_CHILD_SEND
    }

    private Script opReturn;
    private String tokenId;
    private Transaction tx;
    private SlpTxType slpTxType;
    private int slpUtxos;
    private boolean hasMintingBaton = false;
    private TransactionOutput mintingBatonUtxo = null;
    private int mintingBatonVout = 0;

    public SlpOpReturn(Transaction tx) {
        this.tx = tx;
        opReturn = tx.getOutput(opReturnLocation).getScriptPubKey();

        if (ScriptPattern.isOpReturn(opReturn)) {
            this.setSlpTxType(opReturn);
            this.setTokenId(opReturn);
            this.collectSlpUtxos(opReturn);
            this.findMintingBaton(opReturn);
        } else {
            throw new NullPointerException("Not an SLP transaction.");
        }
    }

    private void setTokenId(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            if (this.getSlpTxType() == SlpTxType.SEND || this.getSlpTxType() == SlpTxType.MINT || this.getSlpTxType() == SlpTxType.NFT_PARENT_SEND
                    || this.getSlpTxType() == SlpTxType.NFT_CHILD_SEND || this.getSlpTxType() == SlpTxType.NFT_PARENT_MINT) {
                ScriptChunk tokenIdChunk = opReturn.getChunks().get(tokenIdChunkLocation);
                assert tokenIdChunk.data != null;
                this.tokenId = new String(Hex.encode(tokenIdChunk.data));
            } else if (this.getSlpTxType() == SlpTxType.GENESIS || this.getSlpTxType() == SlpTxType.NFT_PARENT_GENESIS
                    || this.getSlpTxType() == SlpTxType.NFT_CHILD_GENESIS) {
                this.tokenId = this.tx.getTxId().toString();
            }
        }
    }

    public static boolean isSlpTx(Transaction tx) {
        List<TransactionOutput> outputs = tx.getOutputs();
        TransactionOutput opReturnUtxo = outputs.get(0);
        Script opReturn = opReturnUtxo.getScriptPubKey();
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals(slpProtocolId)) {
                    ScriptChunk tokenTypeChunk = opReturn.getChunks().get(slpTokenTypeChunkLocation);
                    if (tokenTypeChunk != null && tokenTypeChunk.data != null) {
                        String tokenType = new String(Hex.encode(tokenTypeChunk.data), StandardCharsets.UTF_8);
                        return tokenType.equals(tokenType1Id) || tokenType.equals(nft1ParentTypeId);
                    }
                }
            }
        }

        return false;
    }

    public static boolean isNftChildTx(Transaction tx) {
        List<TransactionOutput> outputs = tx.getOutputs();
        TransactionOutput opReturnUtxo = outputs.get(0);
        Script opReturn = opReturnUtxo.getScriptPubKey();
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals(slpProtocolId)) {
                    ScriptChunk tokenTypeChunk = opReturn.getChunks().get(slpTokenTypeChunkLocation);
                    if (tokenTypeChunk != null && tokenTypeChunk.data != null) {
                        String tokenType = new String(Hex.encode(tokenTypeChunk.data), StandardCharsets.UTF_8);
                        return tokenType.equals(nft1ChildTypeId);
                    }
                }
            }
        }

        return false;
    }

    private void setSlpTxType(Script opReturn) {
        if (ScriptPattern.isOpReturn(opReturn)) {
            ScriptChunk protocolChunk = opReturn.getChunks().get(protocolChunkLocation);
            if (protocolChunk != null && protocolChunk.data != null) {
                String protocolId = new String(Hex.encode(protocolChunk.data), StandardCharsets.UTF_8);
                if (protocolId.equals(slpProtocolId)) {
                    ScriptChunk tokenTypeChunk = opReturn.getChunks().get(slpTokenTypeChunkLocation);
                    if (tokenTypeChunk != null && tokenTypeChunk.data != null) {
                        String tokenType = new String(Hex.encode(tokenTypeChunk.data), StandardCharsets.UTF_8);

                        ScriptChunk slpTxTypeChunk = opReturn.getChunks().get(slpTxTypeChunkLocation);
                        if (slpTxTypeChunk != null && slpTxTypeChunk.data != null) {
                            String txType = new String(Hex.encode(slpTxTypeChunk.data), StandardCharsets.UTF_8);
                            switch (txType) {
                                case genesisTxTypeId:
                                    switch (tokenType) {
                                        case tokenType1Id:
                                            this.slpTxType = SlpTxType.GENESIS;
                                            break;
                                        case nft1ParentTypeId:
                                            this.slpTxType = SlpTxType.NFT_PARENT_GENESIS;
                                            break;
                                        case nft1ChildTypeId:
                                            this.slpTxType = SlpTxType.NFT_CHILD_GENESIS;
                                            break;
                                    }
                                    break;
                                case mintTxTypeId:
                                    switch (tokenType) {
                                        case tokenType1Id:
                                            this.slpTxType = SlpTxType.MINT;
                                            break;
                                        case nft1ParentTypeId:
                                            this.slpTxType = SlpTxType.NFT_PARENT_MINT;
                                            break;
                                    }
                                    break;
                                case sendTxTypeId:
                                    switch (tokenType) {
                                        case tokenType1Id:
                                            this.slpTxType = SlpTxType.SEND;
                                            break;
                                        case nft1ParentTypeId:
                                            this.slpTxType = SlpTxType.NFT_PARENT_SEND;
                                            break;
                                        case nft1ChildTypeId:
                                            this.slpTxType = SlpTxType.NFT_CHILD_SEND;
                                            break;
                                    }
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void collectSlpUtxos(Script opReturn) {
        int chunkOffset = 0;
        switch (this.slpTxType) {
            case GENESIS:
            case NFT_PARENT_GENESIS:
            case NFT_CHILD_GENESIS:
                chunkOffset = 10;
                break;
            case MINT:
            case NFT_PARENT_MINT:
                chunkOffset = 6;
                break;
            case SEND:
            case NFT_PARENT_SEND:
            case NFT_CHILD_SEND:
                chunkOffset = 5;
                break;
        }

        slpUtxos = opReturn.getChunks().size() - chunkOffset;
    }

    private void findMintingBaton(Script opReturn) {
        byte[] mintingBatonVoutData = null;
        if (this.getSlpTxType() == SlpTxType.GENESIS) {
            mintingBatonVoutData = opReturn.getChunks().get(mintBatonVoutGenesisChunkLocation).data;
        } else if (this.getSlpTxType() == SlpTxType.MINT) {
            mintingBatonVoutData = opReturn.getChunks().get(mintBatonVoutMintChunkLocation).data;
        }

        if (mintingBatonVoutData != null) {
            String voutHex = new String(Hex.encode(mintingBatonVoutData), StandardCharsets.UTF_8);
            if (!voutHex.equals("")) {
                int vout = Integer.parseInt(voutHex, 16);
                this.hasMintingBaton = true;
                this.mintingBatonUtxo = this.getTx().getOutput(vout);
                this.mintingBatonVout = vout;
            }
        }
    }

    public long getRawAmountOfUtxo(int slpUtxoIndex) {
        int chunkOffset = 0;
        switch (this.slpTxType) {
            case GENESIS:
            case NFT_PARENT_GENESIS:
            case NFT_CHILD_GENESIS:
                chunkOffset = 10;
                break;
            case MINT:
            case NFT_PARENT_MINT:
                chunkOffset = 6;
                break;
            case SEND:
            case NFT_PARENT_SEND:
            case NFT_CHILD_SEND:
                chunkOffset = 5;
                break;
        }

        int utxoChunkLocation = slpUtxoIndex + chunkOffset;
        return Long.parseLong(new String(Hex.encode(Objects.requireNonNull(opReturn.getChunks().get(utxoChunkLocation).data))), 16);
    }

    public String getTokenId() {
        return this.tokenId;
    }

    public int getSlpUtxos() {
        return this.slpUtxos;
    }

    public boolean hasMintingBaton() {
        return this.hasMintingBaton;
    }

    public SlpTxType getSlpTxType() {
        return this.slpTxType;
    }

    public int getMintingBatonVout() {
        return this.mintingBatonVout;
    }

    public Transaction getTx() {
        return this.tx;
    }

    public Script getOpReturn() {
        return this.opReturn;
    }

    public TransactionOutput getMintingBatonUtxo() {
        return this.mintingBatonUtxo;
    }
}
