package org.bitcoincashj.core.slp.nft;

import org.bitcoincashj.core.slp.SlpToken;

public class NonFungibleSlpToken extends SlpToken {
    private String nftParentId;
    private String name;

    public NonFungibleSlpToken(String tokenId, String nftParentId, String name, String ticker, int decimals) {
        super(tokenId, ticker, decimals);
        this.name = name;
        this.nftParentId = nftParentId;
    }

    public String getNftParentId() {
        return this.nftParentId;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String toString() {
        return "SLP TOKEN [" +
                this.getTokenId() + ", " +
                this.getTicker() + ", " +
                this.getDecimals() + ", " +
                this.getNftParentId() + ", " +
                this.getName() +
                "]";
    }
}
