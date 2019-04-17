package com.coolioasjulio.rpc;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RPCRequest {
    private long id = 0;
    private boolean instantiate = false;
    private String className = "";
    private String objectName = "";
    private String methodName = "";
    private List<String> argClassNames = new ArrayList<>();
    private List<Object> args = new ArrayList<>();

    public RPCRequest() {
        // Empty constructor
    }

    public RPCRequest(long id, boolean instantiate, String className, String objectName, String methodName, String[] argClassNames, Object[] args) {
        this.id = id;
        this.instantiate = instantiate;
        this.className = className == null ? "" : className;
        this.objectName = objectName == null ? "" : objectName;
        this.methodName = methodName == null ? "" : methodName;
        this.argClassNames = Arrays.asList(argClassNames == null ? new String[0] : argClassNames);
        this.args = Arrays.asList(args == null ? new Object[0] : args);
    }

    public long getId() {
        return id;
    }

    public boolean isInstantiate() {
        return instantiate;
    }

    public String getClassName() {
        return className;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getMethodName() {
        return methodName;
    }

    private boolean isRemoteObject(String className) {
        return className.startsWith("REMOTE:");
    }

    /**
     * Get the argument classes.
     *
     * @return An ArrayList of classes for each argument in the RPCRequest.
     * @throws ClassNotFoundException If one of the classes specified in the array doesn't exist.
     */
    public List<Class<?>> getClasses() throws ClassNotFoundException {
        return getClasses(new HashMap<>());
    }

    /**
     * Get the unboxed argument classes.
     *
     * @param unboxMap The map to map boxed to unboxed classes.
     * @return An ArrayList of classes for each argument in the RPCRequest, unboxed if possible.
     * @throws ClassNotFoundException If one of the classes specified in the array doesn't exist.
     */
    public List<Class<?>> getClasses(Map<Class<?>, Class<?>> unboxMap) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        for (String className : argClassNames) {
            if (isRemoteObject(className)) {
                className = className.split(":", 2)[1];
            }
            Class<?> clazz = Class.forName(className);
            classes.add(unboxMap.getOrDefault(clazz, clazz));
        }
        return classes;
    }

    /**
     * Get the arguments of the RPCRequest casted to the correct type/class.
     *
     * @param sessionVariables The remote objects present in the current RPC session. Used for remote objects.
     * @return An array of objects representing the parameters.
     * @throws ClassNotFoundException If one of the classes specified in the array doesn't exist.
     */
    public Object[] getTypedArgs(Map<String, Object> sessionVariables) throws ClassNotFoundException {
        List<Class<?>> classes = getClasses();
        List<Object> typedArgs = new ArrayList<>();
        Gson gson = new Gson();

        for (int i = 0; i < classes.size(); i++) {
            Object o = args.get(i);
            Object typedArg;
            Class<?> clazz = classes.get(i);
            if (isRemoteObject(argClassNames.get(i))) {
                typedArg = sessionVariables.get(gson.fromJson(gson.toJson(o), String.class));
            } else {
                typedArg = gson.fromJson(gson.toJson(o), clazz);
            }
            typedArgs.add(typedArg);
            assert clazz.isInstance(typedArg);
        }

        return typedArgs.toArray();
    }
}
