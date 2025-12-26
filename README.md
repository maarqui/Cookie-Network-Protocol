# Cookie-Network-Protocol
 
## **task 1**

**subtask 1.1** - Document protocol stack initialization and the cookie request method all the way until a cookie is available to the client or the request got rejected.

![v.2](diagrams/cookie-protocol-diagram-v2.png)

**subtask 1.2** - Completion of client methods:
- Completion of `send()` method in CPProtocol.
    - _create a command message object_
    - _send the message to the server and store it in CPProtocol_
- Implementation of receive method in CPProtocol.
    - _for each command sent the client waits 2s for the server response_
    - _call the message parser to reconstruct the command according to the protocol spec | discard if exception_
    - _check if the response matches the command comparing IDs_
    - _return appropriately_
- Creation and implementation of message classes to create and parse command and command response messages.
    - _calculate checksum using CRC32 library_ (https://docs.oracle.com/javase/9/docs/api/java/util/zip/CRC32.html)
 
## **task 2**

**subtask 2.1** - Cookie server implementation:
- enhancement of `receive()` (CPProtocol) to handle cookie requests.
    - _dedicated method to process cookie requests_
    - _hash-map with associative array to map the clients to their cookies_ (https://docs.oracle.com/javase/9/docs/api/java/util/HashMap.html)
        -  _phyConfiguration of client used as key to the map, value of the entries represent cookies_
        -  _never more than 20 entries in the hash-map | reject if more_
   - _implementation on processing of premature cookie renewal_
        - _document decision_
   - _implementation of apropriate response messages from server_
 
## **task 3**

**subtask 3.1** - Command server implementation:
- create command server app using cookie server as reference.
- enhancement of `receive()` (CPProtocol) to handle command requests.
    - _dedicated method to process command requests_
- command processing method that discards unvalid commands and return valid ones.
- finish command request and response classes.

**subtask 3.2** - Cookie validation: 
- extract cookie from the command message.
- add command message to `pendingCommands` ArrayList.
- send cookie validation request message.
- when response message is received, get respective command from `pendingCommands`.
- if validation failed, send message indicating error | if success, send success message and forward the command for execution.

**subtask 3.2** - Completion of cookie server:
- enhancement of `receive()` (CPProtocol) to process validation request messages.
- check if clients exist in the HashMap, if cookie value matches & lifetime is valid.
- send appropriate response to command server.

