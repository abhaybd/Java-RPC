package com.coolioasjulio.rpc.server;

import com.coolioasjulio.rpc.RPCRequest;
import com.coolioasjulio.rpc.RPCResponse;
import com.coolioasjulio.rpc.server.exclusionstrategies.SuperclassExclusionStrategy;
import com.coolioasjulio.rpc.server.exclusionstrategies.WhitelistExclusionStrategy;
import com.google.gson.ExclusionStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RPCServer {
    public enum StrategyType {
        SERIALIZATION, DESERIALIZATION, BOTH
    }

    private static RPCServer instance;

    /**
     * Get the instance of the RPC server. If an instance doesn't exist, create one.
     * This does NOT automatically start the TCP server.
     *
     * @return The RPC server instance
     */
    public static RPCServer getInstance() {
        if (instance == null) {
            instance = new RPCServer();
        }
        return instance;
    }

    private Map<Class<?>, Class<?>> unboxMap;
    private List<Thread> rpcSessions;
    private Gson gson;
    private List<ExclusionStrategy> serializationExclusionStrategies;
    private List<ExclusionStrategy> deserializationExclusionStrategies;

    private RPCServer() {
        Map<Class<?>, Class<?>> unboxMap = new HashMap<>();
        unboxMap.put(Double.class, double.class);
        unboxMap.put(Integer.class, int.class);
        unboxMap.put(Float.class, float.class);
        unboxMap.put(Long.class, long.class);
        unboxMap.put(Boolean.class, boolean.class);
        unboxMap.put(Character.class, char.class);
        unboxMap.put(Byte.class, byte.class);
        unboxMap.put(Short.class, short.class);
        this.unboxMap = Collections.unmodifiableMap(unboxMap);

        rpcSessions = new ArrayList<>();
        serializationExclusionStrategies = new ArrayList<>();
        deserializationExclusionStrategies = new ArrayList<>();

        resetExclusionStrategies();
        rebuildGson();
    }

    private void rebuildGson() {
        GsonBuilder builder = new GsonBuilder();
        serializationExclusionStrategies.forEach(builder::addSerializationExclusionStrategy);
        deserializationExclusionStrategies.forEach(builder::addDeserializationExclusionStrategy);
        this.gson = builder.create();
    }

    /**
     * Reset the JSON exclusion strategies to the default strategies.
     */
    public void resetExclusionStrategies() {
        Set<Class<?>> whiteList = new HashSet<>(unboxMap.keySet());
        whiteList.addAll(unboxMap.values());
        whiteList.add(RPCResponse.class);
        serializationExclusionStrategies.add(new SuperclassExclusionStrategy());
        serializationExclusionStrategies.add(new WhitelistExclusionStrategy(whiteList));

        deserializationExclusionStrategies.add(new SuperclassExclusionStrategy());
    }

    /**
     * This resets all JSON exclusion strategies, INCLUDING THE DEFAULT STRATEGIES. To reset only the user-added
     * strategies, use <code>resetExclusionStrategies</code> instead.
     *
     * @param strategyType The type of strategy to reset.
     */
    public void clearExclusionStrategies(StrategyType strategyType) {
        switch (strategyType) {
            case BOTH:
            case SERIALIZATION:
                this.serializationExclusionStrategies.clear();
                if (strategyType != StrategyType.BOTH) {
                    break;
                }

            case DESERIALIZATION:
                this.deserializationExclusionStrategies.clear();
                break;
        }
    }

    /**
     * Add JSON exclusion strategies. Exclusion strategies determine what gets serialized and deserialized to communicate
     * between the server and client. Some properties must be excluded in order for it to work. Only use this if you
     * know what you're doing.
     *
     * @param strategyType The type of strategy to apply the exclusion strategies to.
     * @param strategies   Exclusion strategies to apply.
     */
    public void addExclusionStrategies(StrategyType strategyType, ExclusionStrategy... strategies) {
        if (strategies.length == 0) return;

        Collection<ExclusionStrategy> collection = Arrays.asList(strategies);
        addExclusionStrategies(strategyType, collection);
    }

    /**
     * Add JSON exclusion strategies. Exclusion strategies determine what gets serialized and deserialized to communicate
     * between the server and client. Some properties must be excluded in order for it to work. Only use this if you
     * know what you're doing.
     *
     * @param strategyType The type of strategy to apply the exclusion strategies to.
     * @param strategies   Exclusion strategies to apply.
     */
    public void addExclusionStrategies(StrategyType strategyType, Collection<ExclusionStrategy> strategies) {
        switch (strategyType) {
            case BOTH:
            case SERIALIZATION:
                this.serializationExclusionStrategies.addAll(strategies);
                if (strategyType != StrategyType.BOTH) {
                    break;
                }

            case DESERIALIZATION:
                this.deserializationExclusionStrategies.addAll(strategies);
                break;
        }

        rebuildGson();
    }

    /**
     * Is the RPC server currently running?
     *
     * @return True if the RPC server is running, false otherwise.
     */
    public boolean isActive() {
        return rpcSessions.size() > 0;
    }

    /**
     * Interrupt all client sessions, and wait for them to exit before returning.
     */
    public void close() {
        close(false);
    }

    /**
     * Interrupt all client sessions, and optionally wait for them to exit before returning.
     *
     * @param returnImmediately If true, return without waiting for client sessions to end. Otherwise, wait for them.
     */
    public void close(boolean returnImmediately) {
        for (Thread t : rpcSessions) {
            t.interrupt();
        }

        if (!returnImmediately) {
            for (Thread t : rpcSessions) {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        rpcSessions.clear();
    }

    /**
     * Launch RPC server using the supplied streams. This allows you to use whatever transport you want, as long
     * as you can get an InputStream and OutputStream. This method launches the request handler thread as a non-daemon.
     *
     * @param inputStream  The input stream from the RPC client
     * @param outputStream The output stream to the RPC client
     */
    public RPCSession createRPCSession(InputStream inputStream, OutputStream outputStream) {
        return createRPCSession(inputStream, outputStream, false);
    }

    /**
     * Launch RPC server using the supplied streams. This allows you to use whatever transport you want, as long
     * as you can get an InputStream and OutputStream.
     *
     * @param inputStream  The input stream from the RPC client
     * @param outputStream The output stream to the RPC client
     * @param daemon       Should the request handler thread be a daemon thread?
     */
    public RPCSession createRPCSession(final InputStream inputStream, final OutputStream outputStream, boolean daemon) {
        Thread t = new Thread(() ->
        {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
                PrintStream out = new PrintStream(outputStream);

                Map<String, Object> variables = new HashMap<>();
                while (!Thread.interrupted()) {
                    String line = in.readLine();
                    if (line == null) break;
                    else if (line.length() == 0) continue;
                    System.out.println("Received request: " + line);
                    RPCRequest request = gson.fromJson(line, RPCRequest.class);
                    if (request.isInstantiate()) {
                        RPCResponse<?> response = instantiateObject(request);
                        if (!response.isException()) {
                            variables.put(request.getObjectName(), response.getValue());
                        }
                        sendRPCResponse(out, response);
                    } else {
                        RPCResponse response = invokeMethod(request, variables);
                        sendRPCResponse(out, response);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.setDaemon(daemon);
        t.start();
        rpcSessions.add(t);
        return new RPCSession(t);
    }

    private RPCResponse invokeMethod(RPCRequest request, Map<String, Object> sessionVariables) {
        if (request.isInstantiate())
            throw new IllegalArgumentException("RPCRequest cannot be an instantiation request!");

        Object result;
        boolean isException = false;
        try {
            Class<?> clazz;
            Object object = null;
            if (!request.getClassName().isEmpty() && !request.getObjectName().isEmpty()) {
                Class<?> staticClass = Class.forName(request.getClassName());
                object = staticClass.getField(request.getObjectName()).get(null);
                clazz = object.getClass();
            } else if (!request.getClassName().isEmpty()) {
                clazz = Class.forName(request.getClassName());
            } else if (!request.getObjectName().isEmpty()) {
                object = sessionVariables.get(request.getObjectName());
                clazz = object.getClass();
            } else {
                throw new Exception("Both className and objectName cannot be empty strings!");
            }
            Class<?>[] argClasses = request.getClasses(unboxMap).toArray(new Class<?>[0]);
            Method method = clazz.getMethod(request.getMethodName(), argClasses);
            result = method.invoke(object, request.getTypedArgs());
        } catch (NullPointerException | NoSuchMethodException |
                IllegalAccessException | InvocationTargetException |
                ClassNotFoundException e) {
            e.printStackTrace();
            result = e.toString();
            isException = true;
        } catch (Exception e) {
            result = e.toString();
            isException = true;
        }
        return new RPCResponse<>(request.getId(), result, isException);
    }

    private RPCResponse<?> instantiateObject(RPCRequest request) {
        if (!request.isInstantiate())
            throw new IllegalArgumentException("RPCRequest must be an instantiation request!");
        Object object;
        boolean isException = false;
        try {
            Class<?> clazz = Class.forName(request.getClassName());
            Class<?>[] argClasses = request.getClasses(unboxMap).toArray(new Class<?>[0]);
            Constructor<?> constructor = clazz.getConstructor(argClasses);
            object = constructor.newInstance(request.getTypedArgs());
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
            object = e.toString();
            isException = true;
        } catch (Exception e) {
            object = e.toString();
            isException = true;
        }
        return new RPCResponse<>(request.getId(), object, isException);
    }

    private void sendRPCResponse(PrintStream out, RPCResponse response) {
        String jsonResponse = gson.toJson(response);
        System.out.println("Sending response: " + jsonResponse);
        out.println(jsonResponse);
        out.flush();
    }

    public class RPCSession implements Closeable {
        private Thread session;

        private RPCSession(Thread session) {
            this.session = session;
        }

        /**
         * Close this specific RPC session. This will wait until the session fully closes before returning.
         */
        public void close() {
            close(false);
        }

        /**
         * Close this specific RPC session.
         *
         * @param returnImmediately If true, return immediately without waiting for session to close. If false,
         *                          This method will wait for the session to close.
         */
        public void close(boolean returnImmediately) {
            session.interrupt();

            if (!returnImmediately) {
                try {
                    session.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            rpcSessions.remove(session);
        }
    }
}
