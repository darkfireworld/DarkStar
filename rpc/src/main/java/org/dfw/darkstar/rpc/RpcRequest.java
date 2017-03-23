package org.dfw.darkstar.rpc;

import java.io.Serializable;

/**
 * RpcRequest
 */
public class RpcRequest implements Serializable {
    static final long serialVersionUID = 1L;
    static final int UNKNOWN = -1;
    static final int PING = 1;
    static final int RPC_REQUEST = 2;
    // 如果requestId=-1，则表示为heartbeat
    long requestId;
    int type;
    String cls;
    String method;
    Object[] param;

    public RpcRequest() {
    }

    public RpcRequest(long requestId, int type, String cls, String method, Object[] param) {
        this.requestId = requestId;
        this.type = type;
        this.cls = cls;
        this.method = method;
        this.param = param;
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

    public String getCls() {
        return cls;
    }

    public void setCls(String cls) {
        this.cls = cls;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object[] getParam() {
        return param;
    }

    public void setParam(Object[] param) {
        this.param = param;
    }
}
