# Java-RPC
RPC server for Java that uses reflection for fast (and sketchy) integration into your code.

This project is a dead-simple RPC server that allows other programs/platforms to use RPC to access your Java code. In order to simplify the integration process, it's written entirely with reflection, which makes it easy to use, but also not very performant. This could should NOT be used in production, only for testing or for quick/hacky tasks. Additionally, this is NOT thread safe. It may be in the future, but it isn't right now.

To use, simply grab an RPC instance using `RPC.getInstance()`. You can use whatever transport you want between your RPC server and client, as long as you have an `InputStream` and `OutputStream`. To use the RPC server, use `RPC.getInstance().createRPCSession(inputStream, outputStream)`. This will launch a thread dedicated to handling RPC using those streams. By default, the thread is not a daemon thread, so it will keep your program alive if it isn't closed. To create a daemon thread, use `RPC.getInstance().createRPCSession(inputStream, outputStream, daemon)`, where daemon is a boolean value indicating whether or not the thread should be daemon. To kill the server, use `RPC.getInstance().close()`. This will interrupt all threads. This method will by default wait for every thread to actually stop before returning. To return immediately, use `RPC.getInstance().close(returnImmediately)`, where `returnImmediately` is a boolean value specifying whether or not to return immediately or wait for the threads to close. There is no way to kill the connection with only one client, although that may be implemented in the future. To determine if the RPC server is running using any transport layer, use `RPC.getInstance().isActive()`.

This library contains no support for a client, since the client could be literally anything. Therefore, the rough structure of how the client should operate will be outlined below.

The RPC server will communicate with the RPC client using the input and output streams supplied. The client-server communication takes the form of call and response. Every message to the server must be followed by a reply. Once the connection is established, the client initiates a request by sending a newline delimited JSON-encoded RPC request, taking the form below. The server will evaluate the request, and send back a newline delimited JSON-encoded RPC response, whose form is also shown below.

## [RPC Request](https://github.com/coolioasjulio/Java-RPC/blob/master/src/main/java/com/coolioasjulio/rpc/RPCRequest.java)
Properties:
* **long id** - The id of the RPC request. Should be 1 more than the id of the last RPC request. Ids start at 0. This is not enforced. The response id WILL be the same.
* **boolean instantiate** - If true, this is an instantiation request. If false, this is a method invocation request. See the rules governing the request types and the corresponding values for `className`, `objectName`, and `methodName` below.
* **List\<String> argClassNames** The EXACT Java class names of the objects in `args`. (Includes $ signs and the like)
* **List\<Object> args** The arguments to be passed to the constructor or method. The types of these objects MUST match the class names in `argClassNames`

### RPC Request types
* **Instantiation request** - This is a request to instantiate a remote object. `className` should be the EXACT Java class name of the object to be instantiated. `objectName` is essentially the variable name. Two remote objects cannot share the same name, and the one that was instantiated last will persist. `methodName` will be ignored.
* **Method invocation request** - This is a request to invoke a method on a remote object. Alternatively, static methods can also be invoked.
    * To invoke a method on a remote object, `objectName` should be the name of the remote object. This is the same name that it was instantiated with. `methodName` is the name of the method to be invoked. `className` can be ignored.
    * To invoke a static method, `className` should be the EXACT Java name of the class which holds the static method. `methodName` should be the name of the method to be invoked. `objectName` MUST BE EXACTLY `"static"`. (without the quotes)

## [RPC Response](https://github.com/coolioasjulio/Java-RPC/blob/master/src/main/java/com/coolioasjulio/rpc/RPCResponse.java)
Properties:
* **long id** - The id of the RPC response. This will be the same as it's corresponding RPC request.
* **boolean isException** - If true, the RPC request failed with an exception. `value` will be a string representation of the exception thrown. If false, `value` will be the JSON-encoded result returned by constructor/method invocation.
* **Object value** - The result of the RPC request. If `isException` is true, the RPC request failed, and this will be the string representation of the exception.
