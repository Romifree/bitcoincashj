package org.bitcoincashj.core.slp.opreturn;

import org.bitcoincashj.script.Script;
import org.bitcoincashj.script.ScriptBuilder;
import org.bitcoincashj.script.ScriptChunk;
import org.bitcoincashj.script.ScriptOpCodes;
import org.bouncycastle.util.encoders.Hex;

import java.nio.ByteBuffer;

public class NftOpReturnOutputSend {
    private Script script;
    private byte[] lokad = new byte[]{83, 76, 80, 0};
    private int PUSHDATA_BYTES = 8;

    public NftOpReturnOutputSend(String tokenId, long tokenAmount, long changeAmount) {
        ScriptBuilder scriptBuilder = new ScriptBuilder()
                .op(ScriptOpCodes.OP_RETURN)
                .data(lokad)
                .data(Hex.decode("41"))
                .data("SEND".getBytes())
                .data(Hex.decode(tokenId))
                .data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(tokenAmount).array());
        if (changeAmount > 0) {
            scriptBuilder = scriptBuilder.data(ByteBuffer.allocate(PUSHDATA_BYTES).putLong(changeAmount).array());
        }
        this.script = scriptBuilder.build();
    }

    public Script getScript() {
        return this.script;
    }
}
