package org.dfw.darkstar.rpc;

import java.io.Serializable;

/**
 * RpcResponse
 */
public class RpcResponse implements Serializable {
    static final long serialVersionUID = 1L;
    // 如果requestId=-1，则表示为heartbeat
    long requestId;
    Object ret;
    Throwable exp;

    public RpcResponse() {
    }

    public RpcResponse(long requestId, Object ret, Throwable exp) {
        this.requestId = requestId;
        this.ret = ret;
        this.exp = exp;
    }

    public long getRequestId() {
        return requestId;
    }

    public void setRequestId(long requestId) {
        this.requestId = requestId;
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
