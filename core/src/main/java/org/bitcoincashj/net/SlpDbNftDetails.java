package org.bitcoincashj.net;

import org.bouncycastle.util.encoders.Base64;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class SlpDbNftDetails {
    private String json;

    public SlpDbNftDetails(String tokenId) {
        JSONObject json = new JSONObject();
        json.put("v", 3);
        JSONObject q = new JSONObject();
        q.put("db", new JSONArray().put("t"));
        JSONObject findJson = new JSONObject();
        JSONObject $queryJson = new JSONObject();
        $queryJson.put("tokenDetails.tokenIdHex", tokenId);
        findJson.put("$query", $queryJson);
        q.put("find", findJson);
        q.put("limit", 1);
        json.put("q", q);
        JSONObject r = new JSONObject();
        r.put("f", "[.[] | {decimals: .tokenDetails.decimals, ticker: .tokenDetails.symbol, name: .tokenDetails.name, nftParentId: .nftParentId}]");
        json.put("r", r);

        this.json = json.toString();
    }

    public String getJson() {
        return this.json;
    }

    public String getEncoded() {
        return new String(Base64.encode(this.json.getBytes()), StandardCharsets.UTF_8);
    }
}