# Java-RPC
RPC server for Java that uses reflection for fast (and sketchy) integration into your code.

This project is a dead-simple RPC server that allows other programs/platforms to use RPC to access your Java code. In order to simplify the integration process, it's written entirely with reflection, which makes it easy to use, but also not very performant. This could shouldn't be used in production, only for testing or for quick/hacky tasks. Additionally, this is NOT thread safe. This means that all RPC responses and requests must be handled by a single thread. This applies to both the client and the server.

For the RPC Server, you can use whatever transport you want between your RPC server and client, as long as you have an `InputStream` and `OutputStream`.

Creating an RPC session using `createRPCSession()` will return an `RPCServer.RPCSession` object, which has a `close()` method, allowing you to close only that RPC session.

### Examples

[An example of a C#/Unity RPC client.](https://github.com/coolioasjulio/FrcDrive/blob/master/Assets/Scripts/RPC.cs) This doesn't support many features yet.

[An up to date example of a Java RPC client.](src/main/java/com/coolioasjulio/rpc/client/RPCClient.java)

To create an RPC session between a client and the server using the input and output streams between the client and server:

    RPCServer.getInstance().createRPCSession(inputStream, outputStream)

To create an RPC session while specifying whether or not to make it a daemon:

    RPCServer.getInstance().createRPCSession(inputStream, outputStream, daemon)

To kill the server, close all connections, and wait for all threads to stop:

    RPCServer.getInstance().close()

To kill the server, close all connections, and return immediately without waiting for the threads:

    RPCServer.getInstance().close(returnImmediately)

To determine if the RPC server is running using any transport layer:

    RPCServer.getInstance().isActive()


To kill a specific RPC instance:

    RPCServer.RPCSession session = RPCServer.getInstance().createRPCSession(inputStream, outputStream) // Create the session
    session.close() // Kill the session

## [RPC Request](src/main/java/com/coolioasjulio/rpc/RPCRequest.java)
Properties:
* **long id** - The id of the RPC request. Should be 1 more than the id of the last RPC request. Ids start at 0. This is not enforced. The response id WILL be the same.
* **boolean instantiate** - If true, this is an instantiation request. If false, this is a method invocation request. See the rules governing the request types and the corresponding values for `className`, `objectName`, and `methodName` below.
* **List\<String> argClassNames** The canonical Java class names of the objects in `args`. (Includes $ signs and the like)
* **List\<Object> args** The arguments to be passed to the constructor or method. The types of these objects MUST match the class names in `argClassNames`

### RPC Request types
* **Instantiation request** - This is a request to instantiate a remote object. `className` should be the canonical Java class name of the object to be instantiated. `objectName` is essentially the variable name. Two remote objects cannot share the same name, and the one that was instantiated last will persist. `methodName` will be ignored.
* **Method invocation request** - This is a request to invoke a method on a remote object. Alternatively, static methods can also be invoked.
    * To invoke a method on a remote object, `objectName` should be the name of the remote object. This is the same name that it was instantiated with. `methodName` is the name of the method to be invoked. `className` must be an empty string. (`""`)
    * To invoke a static method, `className` should be the canonical Java name of the class which holds the static method. `methodName` should be the name of the method to be invoked. `objectName` must be an empty string. (`""`)
    * To invoke a method on a static object, `className` should be the canonical Java name of the class defining the static object, `objectName` should be the name of the static object that defines the method, and `methodName` should be the method to invoke.

## [RPC Response](https://github.com/coolioasjulio/Java-RPC/blob/master/src/main/java/com/coolioasjulio/rpc/RPCResponse.java)
Properties:
* **long id** - The id of the RPC response. This will be the same as it's corresponding RPC request.
* **boolean isException** - If true, the RPC request failed with an exception. `value` will be a string representation of the exception thrown. If false, `value` will be the JSON-encoded result returned by constructor/method invocation.
* **Object value** - The result of the RPC request. If `isException` is true, the RPC request failed, and this will be the exception message, represented as a String.

## RPC Client
This library contains a Java client. No other clients are implemented, as those could take many forms. Therefore, the rough structure of how the client should operate will be outlined below.

The RPC server will communicate with the RPC client using the input and output streams supplied. The client-server communication takes the form of call and response. Every message to the server must be followed by a reply. Once the connection is established, the client initiates a request by sending a newline delimited JSON-encoded RPC request, taking the form below. The server will evaluate the request, and send back a newline delimited JSON-encoded RPC response, whose form is also shown below.

## [Java RPC Client](src/main/java/com/coolioasjulio/rpc/client/RPCClient.java)
This class is pretty straightforward, the heavy lifting happens in `sendRPCRequest()`. It's essentially just serializing the request, sending it, deserializing the response and validating it, and then returns it.

### Passing remote objects as parameters
This library supports passing a remote object as a parameter to an RPC request. Prepend `REMOTE:` exactly to the corresponding value in `argClassNames`, and the corresponding value in `args` must be a string representing the name of the remote object.

### Examples

To create an RPC client:

    RPCClient client = new RPCClient(inputStream, outputStream);

To instantiate a remote object:

    client.instantiateObject("java.lang.Object", "obj");

To invoke a method on a static object:

    client.executeMethodOnStaticObject("java.lang.System", "out", "println", new String[]{"java.lang.String"}, new Object[]{"Hello World!"});

To invoke a static method:

    client.executeStaticMethod("java.lang.Math", "random");

To pass a remote object as a parameter:

    client.instantiateObject("java.lang.Object", "obj");
    client.executeMethodOnStaticObject("java.lang.System", "out", "println", new String[]{"REMOTE:java.lang.Object"}, new Object[]{"obj"});

To close an RPC client:

    client.close();