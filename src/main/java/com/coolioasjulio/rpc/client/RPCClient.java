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

public class RPCClient implements AutoCloseable {
    private Gson gson;
    private BufferedReader in;
    private PrintStream out;
    private long id;

    /**
     * Create an RPC client.
     *
     * @param in  InputStream coming from the RPC server.
     * @param out OutputStream going to the RPC server.
     */
    public RPCClient(InputStream in, OutputStream out) {
        gson = new Gson();
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = new PrintStream(out);
    }

    private <T> T sendRPCRequest(boolean instantiate, String className, String objectName, String methodName, String[] argClassNames, Object[] args) {
        if (argClassNames.length != args.length) {
            throw new IllegalArgumentException("argClassNames and args must have same length!");
        }

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

    /**
     * Execute a static method.
     *
     * @param className  The canonical name of the class which defines the static method.
     * @param methodName The name of the static method to execute.
     * @param <T>        The return type.
     * @return The result of the static method.
     */
    public <T> T executeStaticMethod(String className, String methodName) {
        return executeStaticMethod(className, methodName, new String[0], new Object[0]);
    }

    /**
     * Execute a static method.
     *
     * @param className     The canonical name of the class which defines the static method.
     * @param methodName    The name of the static method to execute.
     * @param argClassNames The canonical names of the classes of the arguments to the method call. Corresponds to <code>args.</code>
     *                      Prepend with REMOTE: to pass a remote object as an argument. If remote object, the corresponding
     *                      value in <code>args</code> should a string representing the name of the remote object.
     * @param args          The objects to pass as arguments to the method. To pass a remote object, prepend the class
     *                      name with REMOTE: and the arg should be the remote object name.
     * @param <T>           The return type.
     * @return The result of the static method.
     */
    public <T> T executeStaticMethod(String className, String methodName, String[] argClassNames, Object[] args) {
        return sendRPCRequest(false, className, "", methodName, argClassNames, args);
    }

    /**
     * Execute a static method.
     *
     * @param objectName The name of the remote object which defines the method to execute.
     * @param methodName The name of the method to execute.
     * @param <T>        The return type.
     * @return The result of the method.
     */
    public <T> T executeMethod(String objectName, String methodName) {
        return executeMethod(objectName, methodName, new String[0], new Object[0]);
    }

    /**
     * Execute a method on a remote object.
     *
     * @param objectName    The name of the remote object which defines the method to execute.
     * @param methodName    The name of the method to execute.
     * @param argClassNames The canonical names of the classes of the arguments to the method call. Corresponds to <code>args.</code>
     *                      Prepend with REMOTE: to pass a remote object as an argument. If remote object, the corresponding
     *                      value in <code>args</code> should a string representing the name of the remote object.
     * @param args          The objects to pass as arguments to the method. To pass a remote object, prepend the class
     *                      name with REMOTE: and the arg should be the remote object name.
     * @param <T>           The return type.
     * @return The result of the method.
     */
    public <T> T executeMethod(String objectName, String methodName, String[] argClassNames, Object[] args) {
        return sendRPCRequest(false, "", objectName, methodName, argClassNames, args);
    }

    /**
     * Execute a method on a static object.
     *
     * @param className  The canonical name of the class which defines the static object.
     * @param objectName The name of the static object which defines the method to execute.
     * @param methodName The name of the method to execute.
     * @param <T>        The return type.
     * @return The result of the method.
     */
    public <T> T executeMethodOnStaticObject(String className, String objectName, String methodName) {
        return executeMethodOnStaticObject(className, objectName, methodName, new String[0], new Object[0]);
    }

    /**
     * Execute a method on a static object.
     *
     * @param className     The canonical name of the class which defines the static object.
     * @param objectName    The name of the static object which defines the method to execute.
     * @param methodName    The name of the method to execute.
     * @param argClassNames The canonical names of the classes of the arguments to the method call. Corresponds to <code>args.</code>
     *                      Prepend with REMOTE: to pass a remote object as an argument. If remote object, the corresponding
     *                      value in <code>args</code> should a string representing the name of the remote object.
     * @param args          The objects to pass as arguments to the method. To pass a remote object, prepend the class
     *                      name with REMOTE: and the arg should be the remote object name.
     * @param <T>           The return type.
     * @return The result of the method.
     */
    public <T> T executeMethodOnStaticObject(String className, String objectName, String methodName, String[] argClassNames, Object[] args) {
        return sendRPCRequest(false, className, objectName, methodName, argClassNames, args);
    }

    /**
     * Instantiate a remote object.
     *
     * @param className  The canonical name of the class to instantiate.
     * @param objectName The name of the remote object that's being instantiated.
     * @param <T>        The return type.
     * @return The instantiated object.
     */
    public <T> T instantiateObject(String className, String objectName) {
        return instantiateObject(className, objectName, new String[0], new Object[0]);
    }

    /**
     * Instantiate a remote object.
     *
     * @param className     The canonical name of the class to instantiate.
     * @param objectName    The name of the remote object that's being instantiated.
     * @param argClassNames The canonical names of the classes of the arguments to the method call. Corresponds to <code>args.</code>
     *                      Prepend with REMOTE: to pass a remote object as an argument. If remote object, the corresponding
     *                      value in <code>args</code> should a string representing the name of the remote object.
     * @param args          The objects to pass as arguments to the method. To pass a remote object, prepend the class
     *                      name with REMOTE: and the arg should be the remote object name.
     * @param <T>           The return type.
     * @return The instantiated object.
     */
    public <T> T instantiateObject(String className, String objectName, String[] argClassNames, Object[] args) {
        return sendRPCRequest(true, className, objectName, "", argClassNames, args);
    }

    /**
     * Close the RPC session. A closed RPC session cannot be used any more.
     *
     * @throws Exception If an error occurs while closing.
     */
    @Override
    public void close() throws Exception {
        in.close();
        out.close();
    }
}
