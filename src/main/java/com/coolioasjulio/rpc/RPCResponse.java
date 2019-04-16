package com.coolioasjulio.rpc;

public class RPCResponse<T> {
    private long id;
    private boolean isException;
    private T value;

    public RPCResponse(long id, T value) {
        this(id, value, false);
    }

    public RPCResponse(long id, T value, boolean isException) {
        this.id = id;
        this.value = value;
        this.isException = isException;
    }

    public T getValue() {
        return value;
    }

    public long getId() {
        return id;
    }

    public boolean isException() {
        return isException;
    }
}
