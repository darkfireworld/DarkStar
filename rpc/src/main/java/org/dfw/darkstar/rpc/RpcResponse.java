package org.dfw.darkstar.rpc;

import java.io.Serializable;

/**
 * RpcResponse
 */
public class RpcResponse implements Serializable {
    static final long serialVersionUID = 1L;
    static final int UNKNOWN = -1;
    static final int PONG = 1;
    static final int RPC_RESPONSE = 2;
    // 如果requestId=-1，则表示为heartbeat
    long requestId;
    // -1 -> unknown
    // 1 -> pong
    // 2 -> rpc
    int type;
    Object ret;
    Throwable exp;

    public RpcResponse() {
    }

    public RpcResponse(long requestId, int type, Object ret, Throwable exp) {
        this.requestId = requestId;
        this.type = type;
        this.ret = ret;
        this.exp = exp;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public Object getRet() {
        return ret;
    }

    public void setRet(Object ret) {
        this.ret = ret;
    }

    public Throwable getExp() {
        return exp;
    }

    public void setExp(Throwable exp) {
        this.exp = exp;
    }
}
