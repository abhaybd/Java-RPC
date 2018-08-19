package com.coolioasjulio.rpc;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.coolioasjulio.rpc.exclusionstrategies.SuperclassExclusionStrategy;
import com.coolioasjulio.rpc.exclusionstrategies.WhitelistExclusionStrategy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RPC
{
    public static void main(String[] args)
    {
        RPC.getInstance().startTcpServer(4444);
    }

    private static RPC instance;

    /**
     * Get the instance of the RPC server. If an instance doesn't exist, create one.
     * This does NOT automatically start the TCP server.
     *
     * @return The RPC server instance
     */
    public static RPC getInstance()
    {
        if(instance == null)
        {
            instance = new RPC();
        }
        return instance;
    }

    private ServerSocket serverSocket = null;
    private Map<Class<?>,Class<?>> unboxMap;
    private Thread tcpConnectionHandlerThread;
    private List<Thread> requestHandlerThreads = new ArrayList<>();
    private Gson gson;

    private RPC()
    {
        Map<Class<?>,Class<?>> unboxMap = new HashMap<>();
        unboxMap.put(Double.class, double.class);
        unboxMap.put(Integer.class, int.class);
        unboxMap.put(Float.class, float.class);
        unboxMap.put(Long.class, long.class);
        unboxMap.put(Boolean.class, boolean.class);
        unboxMap.put(Character.class, char.class);
        unboxMap.put(Byte.class, byte.class);
        unboxMap.put(Short.class, short.class);
        this.unboxMap = Collections.unmodifiableMap(unboxMap);

        Set<Class<?>> whiteList = new HashSet<>(unboxMap.keySet());
        whiteList.addAll(unboxMap.values());
        whiteList.add(RPCResponse.class);
        this.gson = new Gson().newBuilder()
            .addSerializationExclusionStrategy(new SuperclassExclusionStrategy())
            .addDeserializationExclusionStrategy(new SuperclassExclusionStrategy())
            .addSerializationExclusionStrategy(new WhitelistExclusionStrategy(whiteList))
            .create();
    }

    /**
     * Is the RPC server currently running?
     *
     * @return True if the RPC server is running, false otherwise.
     */
    public boolean isActive()
    {
        return requestHandlerThreads.size() > 0;
    }

    /**
     * Initialize the RPC server using TCP at the supplied port. If it is not already running. If it is, do nothing.
     * This is by no means required. If you want to use something other than TCP, just use the
     * <code>launchRequestHandler</code> method. This is just a pre-made implementation of TCP.
     *
     * @param port The port to bind the TCP server socket to.
     */
    public void startTcpServer(int port)
    {
        if(serverSocket != null) return;
        try
        {
            serverSocket = new ServerSocket(port);

            tcpConnectionHandlerThread = new Thread(this::tcpConnectionHandlerThread);
            tcpConnectionHandlerThread.setDaemon(false);
            tcpConnectionHandlerThread.start();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Close the RPC server socket, interrupt all the threads, and wait for the threads to end.
     * This method does not return until all the threads have stopped.
     */
    public void close()
    {
        if(serverSocket != null)
        {
            try
            {
                serverSocket.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            if(tcpConnectionHandlerThread != null)
            {
                tcpConnectionHandlerThread.interrupt();
                tcpConnectionHandlerThread = null;
            }

            serverSocket = null;
        }

        for(Thread t : requestHandlerThreads)
        {
            t.interrupt();
        }

        for(Thread t : requestHandlerThreads)
        {
            try
            {
                t.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        requestHandlerThreads.clear();
    }

    private void tcpConnectionHandlerThread()
    {
        while(!Thread.interrupted())
        {
            try
            {
                System.out.println("Waiting for connection...");
                Socket socket = serverSocket.accept();
                System.out.println("Received connection from " + socket.getInetAddress().toString());

                launchRequestHandler(socket.getInputStream(), socket.getOutputStream());
            }
            catch(InterruptedIOException e)
            {
                break;
            }
            catch (IOException e)
            {
                e.printStackTrace();
                break;
            }
        }
    }

    /**
     * Launch RPC server using the supplied streams. This allows you to use whatever transport you want, as long
     * as you can get an InputStream and OutputStream. This method launches the request handler thread as a non-daemon.
     *
     * @param inputStream The input stream from the RPC client
     * @param outputStream The output stream to the RPC client
     */
    public void launchRequestHandler(InputStream inputStream, OutputStream outputStream)
    {
        launchRequestHandler(inputStream, outputStream, false);
    }

    /**
     * Launch RPC server using the supplied streams. This allows you to use whatever transport you want, as long
     * as you can get an InputStream and OutputStream.
     *
     * @param inputStream The input stream from the RPC client
     * @param outputStream The output stream to the RPC client
     * @param daemon Should the request handler thread be a daemon thread?
     */
    public void launchRequestHandler(final InputStream inputStream, final OutputStream outputStream, boolean daemon)
    {
        Thread t = new Thread(() ->
        {
            try
            {
                BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
                PrintStream out = new PrintStream(outputStream);

                Map<String,Object> variables = new HashMap<>();
                while(!Thread.interrupted())
                {
                    String line = in.readLine();
                    if(line == null) break;
                    else if(line.length() == 0) continue;
                    System.out.println("Received request: " + line);
                    RPCRequest request = gson.fromJson(line, new TypeToken<RPCRequest>(){}.getType());
                    if(request.isInstantiate())
                    {
                        Object object = instantiateObject(request);
                        variables.put(request.getObjectName(), object);
                        sendRPCResponse(out, request.getId(), object);
                    } else
                    {
                        String objectName = request.getObjectName();
                        Object object = variables.get(objectName);
                        if(object == null && !objectName.equals("static")) continue;

                        Object result = invokeMethod(request, object);

                        sendRPCResponse(out, request.getId(), result);
                    }
                }
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        });
        t.setDaemon(daemon);
        t.start();
        requestHandlerThreads.add(t);
    }

    private Object invokeMethod(RPCRequest request, Object object)
    {
        if(request.isInstantiate()) throw new IllegalArgumentException("RPCRequest cannot be an instantiation request!");

        Object result;
        try
        {
            Class<?> clazz = object == null ? Class.forName(request.getClassName()) : object.getClass();
            Class<?>[] argClasses = request.getClasses(unboxMap).toArray(new Class<?>[0]);
            Method method = clazz.getMethod(request.getMethodName(), argClasses);
            result = method.invoke(object, request.getTypedArgs());
        } catch(NullPointerException | NoSuchMethodException |
            IllegalAccessException | InvocationTargetException |
            ClassNotFoundException e)
        {
            e.printStackTrace();
            result = e.toString();
        } catch(Exception e)
        {
            result = e.toString();
        }
        return result;
    }

    private Object instantiateObject(RPCRequest request)
    {
        if(!request.isInstantiate()) throw new IllegalArgumentException("RPCRequest must be an instantiation request!");
        Object object;
        try
        {
            Class<?> clazz = Class.forName(request.getClassName());
            Class<?>[] argClasses = request.getClasses(unboxMap).toArray(new Class<?>[0]);
            Constructor<?> constructor = clazz.getConstructor(argClasses);
            object = constructor.newInstance(request.getTypedArgs());
        } catch(ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
            IllegalAccessException | InstantiationException e)
        {
            e.printStackTrace();
            object = e.toString();
        } catch(Exception e)
        {
            object = e.toString();
        }
        return object;
    }

    private void sendRPCResponse(PrintStream out, long id, Object value)
    {
        RPCResponse response = new RPCResponse(id, value);

        String jsonResponse = gson.toJson(response);
        System.out.println("Sending response: " + jsonResponse);
        out.println(jsonResponse);
        out.flush();
    }
}
