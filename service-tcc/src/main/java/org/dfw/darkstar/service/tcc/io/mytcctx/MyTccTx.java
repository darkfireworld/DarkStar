package org.dfw.darkstar.service.tcc.io.mytcctx;

/**
 * MyTccTx
 */
public class MyTccTx {
    String id;
    String tccId;
    String txName;
    String txParam;
    String txRet;
    String txThrow;
    State state;
    long ct;

    public MyTccTx() {
    }

    public MyTccTx(String id, String tccId, String txName, String txParam, String txRet, String txThrow, State state, long ct) {
        this.id = id;
        this.tccId = tccId;
        this.txName = txName;
        this.txParam = txParam;
        this.txRet = txRet;
        this.txThrow = txThrow;
        this.state = state;
        this.ct = ct;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTccId() {
        return tccId;
    }

    public void setTccId(String tccId) {
        this.tccId = tccId;
    }

    public String getTxName() {
        return txName;
    }

    public void setTxName(String txName) {
        this.txName = txName;
    }

    public String getTxParam() {
        return txParam;
    }

    public void setTxParam(String txParam) {
        this.txParam = txParam;
    }

    public String getTxRet() {
        return txRet;
    }

    public void setTxRet(String txRet) {
        this.txRet = txRet;
    }

    public String getTxThrow() {
        return txThrow;
    }

    public void setTxThrow(String txThrow) {
        this.txThrow = txThrow;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public long getCt() {
        return ct;
    }

    public void setCt(long ct) {
        this.ct = ct;
    }

    public enum State {
        TRY_TODO,
        TRY_DONE,
        CONFIRM_TODO,
        CONFIRM_DONE,
        CANCEL_TODO,
        CANCEL_DONE
    }
}
