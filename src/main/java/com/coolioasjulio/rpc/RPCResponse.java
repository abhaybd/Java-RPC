package com.coolioasjulio.rpc;

public class RPCResponse {
    private long id;
    private boolean isException;
    private Object value;

    public RPCResponse(long id, Object value) {
        this(id, value, false);
    }

    public RPCResponse(long id, Object value, boolean isException) {
        this.id = id;
        this.value = value;
        this.isException = isException;
    }

    public Object getValue() {
        return value;
    }

    public long getId() {
        return id;
    }

    public boolean isException() {
        return isException;
    }
}
