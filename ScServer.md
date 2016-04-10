# ScServer
A useful interface between your application and a web-service on server.
This class permit you essentially to create a two type of commands, **one-shot** command and a **schedulable / persistent** type command.

In many applications we need to execute a command periodically or we need to manage the command fail case.
For example you may need to have a periodical request of data to your server or you may try to resend a command before to decide to throw an error.
More simply, only send a one-time command to the server and wait for the result. 
In all this case this class is right for you.

Also this class let you use a two different kind of server: **Test** or **Production** server. 
And let you decide witch use and when use it.
You can decide to use the Test server **inDebug**, **onEmulator** or in some combination of both. 
In all other cases the class automatically will use the Production server.

This class improve an **auto-save** method for save permanently the commands queue. 
If your commands queue is not empty the class restart the queue at next time you will use the application.
<br />

> **DEPENDENCE:**
> This class use **ksoap2** to comunicate with the server. 

> **PERMISSION:**
> This class read over the network and need the INTERNET permission inside your manifest
> <uses-permission android:name="android.permission.INTERNET"/>

# Documentation
**First step** is configuring the servers and the web-service "page" name.
For make this follow the example below.
In this example used an emulator and a test web-server on the same machine instead the production server is remote.

```java
ScServer server = new ScServer(this.getContext());
server.setTestServerAddress("http://10.0.2.2:56338/web-application-name");
server.setProductionServerAddress("http://www.domain-name.org");
server.setWebServiceName("/directory/service-page.asmx");
```

**Now** we can create a command, add the parameters needed and execute it. 
For example we want to send "my name" and "my age" to the server calling the <code>RegisterMe</code> method.

```java
ScServer.Command command = ScServer.createCommand("RegisterMe");
command.addParam("Name", "John");
command.addParam("Age", 40);
command.execute();
```

Note that <code>command.execute();</code> is **synchronous** and the application will waiting for the result.
For the **asynchronous** command implementation we will take a look after in this page.
This is a very basic usage of this class and I will try to explain a more complex usage of this with some examples reported below.


## ScServer class details
This class extend the <code>ScChecker</code> class.
By default the <code>ScChecker</code> will check the commands queue (only for <code>SchedulableCommand</code> case) every 1 second (fixed delay every check) and with a start delay of 100 milliseconds.
Note that the <code>ScServer</code> start to check the command queue only after a explicit method calling.
So if you want **start** or **stop** the commands queue you must call the inherited methods <code>start()</code> and <code>stop()</code>.
If you want change the <code>ScChecker</code> settings please take a look of the class documentation.

#### Methods

- **String getServerAddress()**<br />
Get the server address.
The address can be the Test server or the Production server. 
By default the class get back the Test server if is filled and if the application is in debug mode.
A custom selection can be implement using the <code>OnServerSelectionListener</code> listener.
- **Command newCommand(String methodName)**<br />
Create a new command specify the service method name.
This command is synchronous and cannot be saved or schedulate.
For a complete documentation of the <code>Command</code> class please see below.
- **SchedulableCommand newSchedulableCommand(String methodName)**<br />
Create a new schedulable command specify the service method name.
This command is asynchronous and can be saved or schedulate.
Also this kind of command can be manage a retry in fail case.
Note that when you create the command it will NOT be add to the queue commands automatically.
You must add the command calling the specific command (<code>addCommand</code>) listed below.
For a complete documentation of the <code>SchedulableCommand</code> class please see below.
- **void addCommand(SchedulableCommand command)**<br />
**void addCommand(SchedulableCommand command, boolean startToResolve)**<br />
Add a new <code>SchedulableCommand</code> to the queue command list.
If <code>startToResolve</code> if <code>true</code> the server will start automatically to check the commands queue.
- **SchedulableCommand removeCommand(String methodName)**<br />
**SchedulableCommand removeCommand(SchedulableCommand command)**<br />
Remove a command found by method name or directly by the command object from the commands queue list and return the removed command.
- **ArrayList<SchedulableCommand> removeGroup(String groupName)**<br />
Remove all commands belonging to a group from the commands queue list and return the list of removed commands.
- **SchedulableCommand findCommand(String methodName)**<br />
Search a method by name inside the commands queue and return the related (first) command found.
If find it get back the command else <code>null</code> will be returned.
- **boolean hasMethod(String methodName)**<br />
Determinate if queue commands collection contains a methods.
- **void setOnCommandListener(OnCommandListener listener)**<br />
Every time a command will be executed.
- **void setOnServerSelectionListener(OnServerSelectionListener listener)**<br />
Can be used for choice if the Test server will be used.
 
#### Getter and Setter
- **get/setTestServerAddress**  -> String value
- **get/setProductionServerAddress**  -> String value
- **get/setWebServiceName**  -> String value
- **get/setWebServiceNameSpace**  -> String value, Default: <code>http://tempuri.org/</code>
- **get/setDotNet** -> boolean value, Default: <code>true</code><br />
Specify if the server used is a DotNet server type
- **get/setSaveQueue** -> boolean value, Default: <code>false</code><br />
If true the commands queue will be saved ad every check cycle and loaded when the class is created at the first time.
The persistent commands and NOT serializable commands will not saved.
- **get/setSavePersistentCommand** -> boolean value, Default: <code>false</code><br />
If true the persistent command will be saved too.


## Command class details
This is inner class of <code>ScServer</code> therefore cannot be free instanced.<br />
For instance a <code>Command</code> class see the <code>ScServer.newCommand(...);</code> method.

#### Methods

- **String execute()**<br />
Execute the command and wait for a serialized result.<br />
**Note** that this class use <code>kSoap2</code> as interface with the server and all the results is back serialized inside a string.
- **void addParam(String name, Object value)**<br />
Add the parameters to the command.
Note that the passed value can be a <code>Callable</code> method and will write a demonstration example below.
- **void removeParam(String name)**<br />
Remove a parameter from the list searching the method name.
- **void reset()**<br />
Reset the command status.
After this the command seems never executed.
- **boolean willTry()**<br />
Mostly for internal use this method return if the command will try to execute.
- **boolean isExecuted()**<br />
<code>true</code> is the command is already executed.
- **boolean isSuccess()**<br />
<code>true</code> if the command executed with success.
- **boolean isError()**<br />
<code>true</code> if the command executed with error.
- **Exception getLastError()**<br />
In error case the class store the last exception.
The error will be also written inside the stack trace.
- **void setCommandListener(CommandListener listener)**<br />
Apply the listener reference to the command.

#### Getter and Setter

- **get/setMethodName**  -> String value


## SchedulableCommand class details
Extends the <code>Command</code> class seen above.<br />
This is inner class of <code>ScServer</code> therefore cannot be free instanced.
For instance a <code>Command</code> class see the <code>ScServer.newSchedulableCommand(...);</code> method.


#### Methods

- **void forceNextExecutionAtDate(long dateInMillisecond, boolean moveAllGroup)**<br />
**void forceNextExecutionAtDate(Date date, boolean moveAllGroup)**<br />
Force the next command execution on a date expressed in milliseconds or a <code>Date</code> object.
The <code>moveAllGroup</code> force to move, by the a calculated offset, the commands of this command group membership, but only the commands listed after this command. 
- **boolean needToExecute()**<br />
<code>true</code> if the command must be executed now.

#### Getter and Setter

- **get/setPersistent**  -> boolean value, Default <code>false</code><br />
If <code>true</code> the command is never spent.
- **get/setMaxRetry**  -> int value, Default <code>0</code><br />
How many time the command will retry in error case.
Please note that <code>0</code> equal to infinity. 
- **get/setRetryDelay**  -> int value, Default <code>0</code> milliseconds<br />
The delay in milliseconds between a execution and the next try.
- **get/setGroup**  -> String value<br />
The membership group name.
- **get/setToSave**  -> String value, Default <code>true</code><br />
If the command must be saved. 
Please look <code>ScServer.setPersistentQueue</code> above.
- **get/setAutoDelete**  -> String value, Default <code>true</code><br />
If <code>true</code> the command will be deleted at the next check if finished its life cycle. 
If <code>false</code> the command must be deleted manually.

## EXAMPLES
For the initialization/configuration please see the **Documentation** section above.


- **Scheduling n.1**<br />
In this case we want check periodically the list of users registered to our server calling the web-service method named "UsersListRequest".

```java
// Create the command
SchedulableCommand command = server.newSchedulableCommand("UsersListRequest");
// Set as persistent and the command will never spent.
command.setPersistent(true);
// Repeat the command every 10 seconds
command.setRetryDelay(10000);
// The command is asynchronous so I need to manage the result using the listener.
command.setCommandListener(new CommandListener() {
        @Override
        public void onRequest() {
               // When the request to server start.
               write("User list update in progress ...");    
        }

        @Override
        public void onComplete(String value) {
               // When the command is complete without errors.
               // In the < value > parameter we will find the result of the command.
               updateUserList(value);
        }

        @Override
        public void onError() {
               // The command finish with error.
               // Note that in exception case the error will written inside the stack
               // trace and you can access to this by the < getLastError()> method.
               write("An error occured!");
        }
});

// Add the command to the queue list and start to resolve the queue
server.addCommand(command, true);

// The code below has the same result:
// server.addCommand(command);
// server.start();
//
// Start to check if needed only one time and only in case of schedule commands.
// Note that is not important start the check after or before insert the commands in queue.

```


- **Scheduling n.2**<br />

In this case we must manage many of users registered on the server.
The better way is download the complete users list only at the first connection and after try to download only the updated or new users.
To do this we need to pass to server the date of my last update so it will get back only the updated users after the reference date.
If the request will finish proper we can save the request date as a reference for the next request.

```java
// Create the command
SchedulableCommand command = server.newSchedulableCommand("UsersListRequest");
// Set as persistent and the command will never spent.
command.setPersistent(true);
// Repeat the command every 10 seconds
command.setRetryDelay(10000);

// Add the date param to pass on server.
// At the first time the < refDate > will be the min value date so the server will return all the users stored.
// The next times < refDate > will be updated with the last date command execution.
final Date refDate = DateUtils.minValue();
command.addParam("DateReference", new Callable<String> () {
        @Override
        public String call() throws Exception {
            // Convert the reference date to string
            return DateFormat.format("yyyy/MM/dd hh:mm:ss", refDate).toString();
        }
});

// The command is asynchronous so I must manage the result using the listener.
command.setCommandListener(new CommandListener() {
        @Override
        public void onRequest() { ... }

        @Override
        public void onError() { ... }

        @Override
        public void onComplete(String value) {
               // When the command is complete without errors.
               // In the < value > parameter we will find the result of the command.
               updateUserList(value);
               // Save the new reference date
               refDate = DateUtils.now();
        }
});

// Add the command to the queue list and start to resolve the queue
server.addCommand(command, true);
```


- **Grouping**<br />

In this case we may suppose to manage a chat.
Suppose to send 3 messages A, B and C. 
The messages settings is, in fail case, to retry the command after 10 seconds.
Now suppose the first command (A) failed but the other command B and C will send with success.
By start settings the command A will be resend after 10 seconds and this time it will go proper.
In this case the final user see the message in a wrong sequence: B, C, A.
To resolve this issue we can use the "group".
When one command belonging to a group fail all the commands group (after the failed command) will be delayed.

```java
// Prepare the messages
String[] messages = { "A", "B", "C" };

// Send all message
for (String message : messages) {
    // Create the command
    SchedulableCommand command = server.newSchedulableCommand("SendMessage");
    
    // Retry delay
    command.setRetryDelay(10000);
    // In fail case the class retry to send the command only 3 times.
    // After I'll let to user to decide if retry again and when.
    command.setRetryCount(3);
    // Set the group
    command.setGroup(chat.getId());
    
    // Set the parameters
    command.addParam("from", sender.getId());
    command.addParam("to", destination.getId());
    command.addParam("message", message);
    
    // Add the command to the queue list
    server.addCommmand(command);
}

// I can manage the command result directly from the server class.
// In this case after 3 tries we'll decide to the user if want retry again to send the message.
server.setOnCommandListener(new ScServer.OnCommandListener() {
    @Override
    public void onCommand(ScServer.Command command) {
        // If not success and it will not retry
        if (!command.isSuccess() && !command.willTry()) {
            // Block the command auto-delete procedure
            command.setAutoDelete(false);
            
            // Here we show the message to the user for take a choice.
            if (show("Impossible to send.\nDo you want retry?") {
                // If "yes".. reset the command as a new command
                command.reset();
            } else {
                // If "no".. remove the command from the queue
                server.removeCommand(command);
                // OR
                // command.setAutoDelete(true);
            }
        } 
    }
});

// Start to check the commands queue.
server.start();
```

# License
<pre>
 Copyright 2015 Samuele Carassai

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in  writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,  either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
</pre>