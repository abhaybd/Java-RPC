package com.coolioasjulio.rpc.client;

import com.coolioasjulio.rpc.RPCException;
import com.coolioasjulio.rpc.RPCRequest;
import com.coolioasjulio.rpc.RPCResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

public class RPCClient {
    private Gson gson;
    private BufferedReader in;
    private PrintStream out;
    private long id;

    public RPCClient(InputStream in, OutputStream out) {
        gson = new Gson();
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = new PrintStream(out);
    }

    private <T> T sendRPCRequest(boolean instantiate, String className, String objectName, String methodName, String[] argClassNames, Object[] args) {
        RPCRequest request = new RPCRequest(id++, instantiate, className, objectName, methodName, argClassNames, args);
        String jsonRequest = gson.toJson(request);
        out.println(jsonRequest);
        out.flush();

        String jsonResponse;
        try {
            jsonResponse = in.readLine();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        RPCResponse tempResponse = gson.fromJson(jsonResponse, RPCResponse.class);

        if (request.getId() != tempResponse.getId()) {
            throw new RPCException("Somehow the calls are out of sync! Are you using multithreading?");
        }

        if (tempResponse.isException()) {
            RPCResponse<String> exception = gson.fromJson(jsonResponse, (new TypeToken<RPCResponse<String>>() {}).getType());
            throw new RuntimeException(exception.getValue());
        }

        RPCResponse<T> response = gson.fromJson(jsonResponse, (new TypeToken<RPCResponse<T>>() {}).getType());
        return response.getValue();
    }

    public <T> T executeStaticMethod(String className, String methodName) {
        return executeStaticMethod(className, methodName, new String[0], new Object[0]);
    }

    public <T> T executeStaticMethod(String className, String methodName, String[] argClassNames, Object[] args) {
        return sendRPCRequest(false, className, "", methodName, argClassNames, args);
    }

    public <T> T executeMethod(String objectName, String methodName) {
        return executeMethod(objectName, methodName, new String[0], new Object[0]);
    }

    public <T> T executeMethod(String objectName, String methodName, String[] argClassNames, Object[] args) {
        return sendRPCRequest(false, "", objectName, methodName, argClassNames, args);
    }

    public <T> T instantiateObject(String className, String objectName) {
        return instantiateObject(className, objectName, new String[0], new Object[0]);
    }

    public <T> T instantiateObject(String className, String objectName, String[] argClassNames, Object[] args) {
        return sendRPCRequest(true, className, objectName, "", argClassNames, args);
    }
}
