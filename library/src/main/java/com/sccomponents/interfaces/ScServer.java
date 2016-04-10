package com.sccomponents.interfaces;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.preference.PreferenceManager;

import com.sccomponents.utils.ScChecker;

import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.Marshal;
import org.ksoap2.serialization.PropertyInfo;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Hashtable;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class for manage the remote server request
 */
@SuppressWarnings("unused")
public class ScServer extends ScChecker {

    /**
     * Static and constant
     */

    // Preferences key where save the commands queue
    private static final String PREFERENCES_KEY = "COMMANDS_QUEUE_PREFERENCES_KEY";
    // Emulator trigger
    private static Boolean mEmulator = null;
    // Debug trigger
    private static Boolean mDebug = null;


    /**
     * Private variables
     */

    private Context mContext = null;                    // The caller context

    private String mProductionServerAddress = null;    // The production server address
    private String mTestServerAddress = null;          // The test server address

    private String mWebServiceNameSpace = null;         // Default web service namespace
    private String mWebServiceName = null;              // Web service name

    private boolean mDotNet = true;                     // If the server is .NET
    private boolean mSaveQueue = false;                 // If the queue is saved automatically
    private boolean mSavePersistentCommand = false;     // Save the persistent command type

    // The listeners
    private OnCommandListener mOnCommandListener = null;
    private OnServerSelectionListener mOnServerSelectionListener = null;

    // The commands queue can be asynchronous so I need a structure can be manage read and write
    // without throw exceptions.
    private CopyOnWriteArrayList<SchedulableCommand> mCommandQueue = null;


    /**
     * Private methods
     */

    // Get/Set the preferences
    @SuppressLint("CommitPrefEdits")
    private void setSharedPreferences(String key, String value) {
        // Get the default preferences
        SharedPreferences manager = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        SharedPreferences.Editor editor = manager.edit();
        // Save the string inside the shared preferences
        editor.putString(key, value);
        editor.commit();
    }

    private String getSharedPreferences(String key) {
        // Get the default preferences
        SharedPreferences manager = PreferenceManager.getDefaultSharedPreferences(this.mContext);
        return manager.getString(key, null);
    }

    // Save the command queue data persistent
    private void saveCommandsQueue() {
        // Exclude the persistent commands
        CopyOnWriteArrayList<SchedulableCommand> filtered = new CopyOnWriteArrayList<>();

        // Cycle all commands in queue
        for (SchedulableCommand command : this.mCommandQueue) {
            // Filter the command to save
            if (command.willTry() && command.getToSave() &&
                    (this.mSavePersistentCommand || !command.getPersistent())) {
                // Add to collection
                filtered.add(command);
            }
        }

        // If the queue is empty reset the shared preference
        if (filtered.isEmpty()) {
            // Saving empty value is same to delete the key
            this.setSharedPreferences(ScServer.PREFERENCES_KEY, null);
        }
        // Try to save the collection structure
        else
            try {
                // Create the output stream
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                // Create the writer, write the object inside the output stream and close
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(filtered);
                oos.close();

                // Convert the output stream in a string
                String value = new String(baos.toByteArray(), "UTF-8");
                // Save the string inside the shared preferences
                this.setSharedPreferences(ScServer.PREFERENCES_KEY, value);
                // Close all
                baos.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
    }

    // Load the commands queue if persistent
    @SuppressWarnings("unchecked")
    private void loadCommandsQueue() {
        try {
            // Get the saved commands queue from the shared preferences and check if is null
            String value = this.getSharedPreferences(ScServer.PREFERENCES_KEY);
            if (value == null || value.isEmpty()) return;

            // Crea stream di input
            ByteArrayInputStream bais = new ByteArrayInputStream(value.getBytes("UTF-8"));
            ObjectInputStream ois = new ObjectInputStream(bais);

            // Create the structure
            this.mCommandQueue = (CopyOnWriteArrayList<SchedulableCommand>) ois.readObject();
            // Close all
            ois.close();
            bais.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Private methods
     */

    // Check if the application running on emulator.
    // The used trigger is a static variable so this check will do only one time.
    private boolean onEmulator() {
        // Check is already did it
        if (ScServer.mEmulator == null) {
            // Set the trigger
            ScServer.mEmulator = false;
            // Get the product info and check for null value
            if (Build.PRODUCT != null) {
                // Search for some occurrences.
                // NB: The searching include the VirtualBox tag.
                ScServer.mEmulator = Build.PRODUCT.equals("sdk") ||
                        Build.PRODUCT.contains("_sdk") ||
                        Build.PRODUCT.contains("sdk_") ||
                        Build.PRODUCT.contains("vbox");
            }
        }
        // Return the settle trigger
        return ScServer.mEmulator;
    }

    // Check if the application is in debug mode
    // The used trigger is a static variable so this check will do only one time.
    private boolean inDebug() {
        // Check if already have it
        if (ScServer.mDebug == null) {
            // Retrieve the mode
            ScServer.mDebug =
                    (0 != (this.mContext.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
        }
        // Return the settle trigger
        return ScServer.mDebug;
    }

    // Call a remote web service methods through SOAP
    private String callServerMethod(String methodName, Hashtable<String, Object> params)
            throws Exception {
        // Init the request
        SoapObject request = new SoapObject(this.mWebServiceNameSpace, methodName);
        // Check if exists some parameters
        if (params != null)
            // Cycle all parameters to create a correct property info structure
            for (String key : params.keySet()) {
                // Get the value
                Object value = params.get(key);

                // If the value is a callable function try to execute the function and
                // store the result inside the same variable < value >
                if (value instanceof Callable) {
                    value = ((Callable) value).call();
                }

                // Create the structure and set all values
                PropertyInfo property = new PropertyInfo();     // Create
                property.setName(key);                          // Name
                property.setValue(value);                       // Value
                property.setType(value.getClass());             // Type

                // Add the parameter info to the request
                request.addProperty(property);
            }

        // Serialize the request
        SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
        // The server is .NET
        envelope.dotNet = this.mDotNet;
        // Implicit type
        envelope.implicitTypes = true;
        // Attach the serializer to the request
        envelope.setOutputSoapObject(request);

        // Fix a bug with the "double" type parameters
        MarshalDouble md = new MarshalDouble();
        md.register(envelope);

        // Create the absolute address of the server
        String url = this.getServerAddress() + this.mWebServiceName;
        // Create the transport
        HttpTransportSE androidHttpTransport = new HttpTransportSE(url);

        // Action and call the transport
        String soapAction = this.mWebServiceNameSpace + methodName;
        androidHttpTransport.call(soapAction, envelope);
        // Wait for a response from the server
        Object response = envelope.getResponse();

        // Check if the returned value is not null
        if (response != null) {
            // Analyze the returned value
            String value = response.toString();
            // If the value is "anyType" mean that the server have executed the command
            // but no returned value. So I reset the the < value > variable.
            if (value.equals("anyType{}")) value = "";
            // Return the elaborated value
            return value;

        } else {
            // Return null value
            return null;
        }
    }

    // Solve the commands queue list.
    // NB: this method is called from an asynchronous timer.
    private void solveQueue() {
        // Spent commands holder
        ArrayList<SchedulableCommand> toRemove = new ArrayList<>();

        // Cycle all commands in queue
        for (SchedulableCommand command : this.mCommandQueue) {
            // Check if the command is already spent
            if (!command.willTry()) {
                // Add this command to spent list to be removed forward but only if the
                // auto-delete trigger is true
                if (command.mAutoDelete) {
                    toRemove.add(command);
                }

            } else
                // If not spent check id need to be executed
                if (command.needToExecute()) {
                    // Execute the command and wait for answer.
                    command.execute();
                }
        }

        // Remove all spent command from the queue
        this.mCommandQueue.removeAll(toRemove);

        // Save if needed
        if (this.mSaveQueue)
            this.saveCommandsQueue();
    }


    /**
     * Public methods
     */

    // Constructor
    @SuppressWarnings("unused")
    public ScServer(Context context) {
        // Init
        this.mContext = context;
        this.mCommandQueue = new CopyOnWriteArrayList<>();
        this.mWebServiceNameSpace = "http://tempuri.org/";

        // Load the commands queue is have one
        this.loadCommandsQueue();
    }

    // Check continuously
    @Override
    @SuppressWarnings("unused")
    public boolean check() {
        // Solve the command queue
        this.solveQueue();
        // Always true
        return true;
    }

    // Get the server address.
    // By default the test server is called when the application is in debug.
    @SuppressWarnings("unused")
    public String getServerAddress() {
        // Trigger
        boolean useTestServer = false;

        // Check if the test server is defined
        if (this.mTestServerAddress != null && !this.mTestServerAddress.isEmpty()) {
            // Check for the listener
            if (this.mOnServerSelectionListener != null) {
                // Call the listener methods for the result
                useTestServer = this.mOnServerSelectionListener
                        .onServerSelection(this.onEmulator(), this.inDebug());
            } else {
                // Default check if in debug
                useTestServer = this.inDebug();
            }
        }

        // Check for witch server to use
        if (useTestServer) {
            // Test server address
            return this.mTestServerAddress;
        } else {
            // Production server address
            return this.mProductionServerAddress;
        }
    }

    // Create the command
    @SuppressWarnings("unused")
    public Command newCommand(String methodName) {
        return new Command(methodName);
    }

    @SuppressWarnings("unused")
    public SchedulableCommand newSchedulableCommand(String methodName) {
        return new SchedulableCommand(methodName);
    }

    // Add a command to the queue
    @SuppressWarnings("unused")
    public void addCommand(SchedulableCommand command) {
        // Check if not null
        if (command != null) {
            // Add it
            this.mCommandQueue.add(command);
        }
    }

    @SuppressWarnings("unused")
    public void addCommand(SchedulableCommand command, boolean startToResolve) {
        // Add it
        this.addCommand(command);
        // Start to resolve the queue is need
        if (startToResolve) this.start();
    }

    // Remove a command from the queue
    @SuppressWarnings("unused")
    public SchedulableCommand removeCommand(SchedulableCommand command) {
        // Check for null value
        if (command != null) {
            // Find the command index in the queue
            int index = this.mCommandQueue.indexOf(command);
            // Check if exists
            if (index != -1) {
                // Remove the command and return it
                return this.mCommandQueue.remove(index);
            }
        }
        // Else
        return null;
    }

    @SuppressWarnings("unused")
    public SchedulableCommand removeCommand(String methodName) {
        // Find the command
        SchedulableCommand command = this.findCommand(methodName);
        // Try to remove it
        return this.removeCommand(command);
    }

    // Remove all commands belonging to this group
    @SuppressWarnings("unused")
    public ArrayList<SchedulableCommand> removeGroup(String groupName) {
        // To remove
        ArrayList<SchedulableCommand> toRemove = new ArrayList<>();

        // Cycle all commands in queue
        for (SchedulableCommand command : this.mCommandQueue) {
            // Check the group
            if (command.getGroup() != null && command.getGroup().equals(groupName)) {
                // Add this command to the list to remove
                toRemove.add(command);
            }
        }

        // Remove all found commands
        this.mCommandQueue.removeAll(toRemove);
        // Return the list of removed commands
        return toRemove;
    }

    // Find a command by method name in the commands queue
    @SuppressWarnings("unused")
    public SchedulableCommand findCommand(String methodName) {
        // Cycle all commands in queue
        for (SchedulableCommand command : this.mCommandQueue) {
            // If the method name is the same return found
            if (methodName.equals(command.getMethodName())) return command;
        }
        // Else not found
        return null;
    }

    // Check if has a command with passed method name in the queue
    @SuppressWarnings("unused")
    public boolean hasMethod(String methodName) {
        return this.findCommand(methodName) != null;
    }


    /**
     * Listeners
     */

    // Set the on command listener
    @SuppressWarnings("unused")
    public void setOnCommandListener(OnCommandListener listener) {
        this.mOnCommandListener = listener;
    }

    // Set the on server selection listener
    @SuppressWarnings("unused")
    public void setOnServerSelectionListener(OnServerSelectionListener listener) {
        this.mOnServerSelectionListener = listener;
    }


    /**
     * Public properties
     */

    // Get/Set the local server address
    @SuppressWarnings("unused")
    public void setTestServerAddress(String value) {
        this.mTestServerAddress = value;
    }

    @SuppressWarnings("unused")
    public String getTestServerAddress() {
        return this.mTestServerAddress;
    }

    // Get/Set the remote server address
    @SuppressWarnings("unused")
    public void setProductionServerAddress(String value) {
        this.mProductionServerAddress = value;
    }

    @SuppressWarnings("unused")
    public String getProductionServerAddress() {
        return this.mProductionServerAddress;
    }

    // Get/Set the web service name
    @SuppressWarnings("unused")
    public void setWebServiceName(String value) {
        this.mWebServiceName = value;
    }

    @SuppressWarnings("unused")
    public String getWebServiceName() {
        return this.mWebServiceName;
    }

    // Get/Set the web service name space
    @SuppressWarnings("unused")
    public void setWebServiceNameSpace(String value) {
        this.mWebServiceNameSpace = value;
    }

    @SuppressWarnings("unused")
    public String getWebServiceNameSpace() {
        return this.mWebServiceNameSpace;
    }

    // Get/Set if the server type is .NET
    // Default: true
    @SuppressWarnings("unused")
    public void setDotNet(boolean value) {
        this.mDotNet = value;
    }

    @SuppressWarnings("unused")
    public boolean getDotNet() {
        return this.mDotNet;
    }

    // Get/Set if the queue is persistent.
    // If true the commands queue will be saved ad every check cycle and loaded when the class
    // is created. The persistent commands by default will not saved.
    // Default: false
    @SuppressWarnings("unused")
    public void setSaveQueue(boolean value) {
        // if false clean the old saved commands queue
        if (!value) this.setSharedPreferences(ScServer.PREFERENCES_KEY, null);
        // Hold the new value
        this.mSaveQueue = value;
    }

    @SuppressWarnings("unused")
    public boolean getSaveQueue() {
        return this.mSaveQueue;
    }

    // Get/Set if save or not the persistent command
    // Default: false
    @SuppressWarnings("unused")
    public void setSavePersistentCommand(boolean value) {
        this.mSavePersistentCommand = value;
    }

    @SuppressWarnings("unused")
    public boolean getSavePersistentCommand() {
        return this.mSavePersistentCommand;
    }


    /**
     * Server listener
     */

    public interface OnCommandListener {

        void onCommand(Command command);

    }

    public interface OnServerSelectionListener {

        boolean onServerSelection(boolean onEmulator, boolean inDebug);

    }


    /******************************************************************************************
     * COMMAND CLASS
     *****************************************************************************************/

    /**
     * Define the server command structure.
     */
    public class Command {

        /**
         * Private variables
         */

        protected String mMethodName = null;                // The called method name
        protected Hashtable<String, Object> mParams = null; // Tha list of parameters
        protected CommandListener mCommandListener = null;  // Listener linked to the command
        protected int mTryCount = 0;                        // The number of current try
        protected boolean mSuccess = false;                 // If the execution finish successfully
        protected Exception mLastError = null;              // Holde the last error raised


        // Constructor
        public Command(String methodName) {
            this.mMethodName = methodName;          // Hold the command name
            this.mParams = new Hashtable<>();       // Create an empty parameters list
        }


        /**
         * Protected methods
         */

        // Execute a command internally
        protected String internalExecute() {
            // Holders
            String value = null;
            try {
                // Execute the command calling the class container < callServerMethod > method
                // and determine is finish proper or with an server error.
                value = ScServer.this.callServerMethod(this.mMethodName, this.mParams);
                // Hold the success
                this.mSuccess = true;
                this.mLastError = null;

            } catch (Exception e) {
                // Write the error inside the stack but not throw any exception
                e.printStackTrace();

                // Hold the error
                this.mSuccess = false;
                this.mLastError = e;
            }

            // Increase the tries trigger
            this.mTryCount++;

            // Return the value
            return value;
        }

        // Call all the listeners linked
        protected void callBeforeExecuteListeners() {
            // If exists a linked listener throw the onRequest method
            if (this.mCommandListener != null)
                this.mCommandListener.onRequest();
        }

        // Call all the listeners linked
        protected void callAfterExecuteListeners(String value) {
            // No error
            if (this.isSuccess()) {
                // If have a linked listener throw the onComplete method
                if (this.mCommandListener != null) this.mCommandListener.onComplete(value);

            } else
            // With error
            {
                // If have a linked listener throw the onError method.
                // Pass false because this command is not schedulable so cannot will retry
                // automatically.
                if (this.mCommandListener != null) this.mCommandListener.onError();
            }

            // If have a server linked listener
            if (ScServer.this.mOnCommandListener != null) {
                // Call the server listener method onCommand
                ScServer.this.mOnCommandListener.onCommand(this);
            }
        }


        /**
         * Public method
         */

        // Execute a command
        @SuppressWarnings("unused")
        public String execute() {
            // Call the listener
            this.callBeforeExecuteListeners();

            // Execute the command and old the result value
            String value = this.internalExecute();

            // Call the listener
            this.callAfterExecuteListeners(value);

            // Return the result
            return value;
        }

        // Add a parameter to the list
        @SuppressWarnings("unused")
        public void addParam(String name, Object value) {
            this.mParams.put(name, value);
        }

        // Remove a parameter to the list
        @SuppressWarnings("unused")
        public void removeParam(String name) {
            this.mParams.remove(name);
        }

        // Reset the command counters
        @SuppressWarnings("unused")
        public void reset() {
            this.mTryCount = 0;
            this.mSuccess = false;
            this.mLastError = null;
        }

        // Check if will try to execute the command.
        @SuppressWarnings("unused")
        public boolean willTry() {
            return !this.isExecuted();
        }

        // If already executed
        @SuppressWarnings("unused")
        public boolean isExecuted() {
            return this.mTryCount > 0;
        }

        // The current success command status.
        public boolean isSuccess() {
            return this.isExecuted() && this.mSuccess;
        }

        // The current error command status
        public boolean isError() {
            return this.isExecuted() && !this.mSuccess;
        }

        // Get the last error
        public Exception getLastError() {
            return this.mLastError;
        }

        // Set the listener
        @SuppressWarnings("unused")
        public void setCommandListener(CommandListener listener) {
            this.mCommandListener = listener;
        }


        /**
         * Public properties
         */

        // Get/Set the method name
        @SuppressWarnings("unused")
        public void setMethodName(String value) {
            this.mMethodName = value;
        }

        @SuppressWarnings("unused")
        public String getMethodName() {
            return this.mMethodName;
        }

    }


    /**
     * Server command listener
     */
    public interface CommandListener {

        void onRequest();                   // On request

        void onComplete(String value);      // On finish

        void onError();                     // On error

    }


    /******************************************************************************************
     * SCHEDULABLE COMMAND CLASS
     *****************************************************************************************/

    /**
     * Define the server schedulable command structure.
     */
    public class SchedulableCommand
            extends Command implements Serializable {

        /**
         * Private variables
         */

        protected boolean mPersistent = false;  // Persistent status
        protected int mMaxRetry = 0;            // Max number of retry
        protected int mRetryDelay = 0;          // Delay in milliseconds between tries
        protected long mNextExecution = 0;      // The date of the next execution
        protected String mGroup = null;         // The command group
        protected boolean mToSave = true;       // If the command is to save
        protected boolean mAutoDelete = true;   // Deleted when it is spent


        // Constructor
        public SchedulableCommand(String methodName) {
            super(methodName);                  // Call the super method
            this.mNextExecution = this.now();   // Set the next execution to now
        }


        /**
         * Private methods
         */

        // Get the current time
        protected long now() {
            Calendar c = Calendar.getInstance();
            return c.getTimeInMillis();
        }


        /**
         * Public method
         */

        // Force the command execution to the passed date.
        // If < moveAllGroup > is true the function will filter by group name and move all the
        // commands after this by a delta time calculated.
        @SuppressWarnings("unused")
        public void forceNextExecutionAtDate(long dateInMillisecond, boolean moveAllGroup) {
            // Check if below to a group and will must be move all commands group
            if (moveAllGroup && this.mGroup != null) {
                // Calc the delta milliseconds and set the found trigger
                long delta = dateInMillisecond - this.mNextExecution;
                boolean found = false;

                // Cycle all commands in the queue searching for the group belonging
                for (SchedulableCommand command : ScServer.this.mCommandQueue) {
                    // Check if the cycle reach the command to move
                    if (command == this) found = true;
                    // If reach the command and if the current command belong to the same group
                    if (found && command.getGroup().equals(this.getGroup())) {
                        // Move the command next execution by the delta time offset
                        command.mNextExecution += delta;
                    }
                }
            }
            // Else do a simple assignment
            else {
                // Get the next execution in milliseconds
                this.mNextExecution = dateInMillisecond;
            }
        }

        @SuppressWarnings("unused")
        public void forceNextExecutionAtDate(Date date, boolean moveAllGroup) {
            // Call the overload function
            this.forceNextExecutionAtDate(date.getTime(), moveAllGroup);
        }

        // Check if a command must be to execute.
        @SuppressWarnings("unused")
        public boolean needToExecute() {
            return this.willTry() && this.mNextExecution <= this.now();
        }


        /**
         * Overrides
         */

        // Check if will try to execute the command
        @Override
        @SuppressWarnings("unused")
        public boolean willTry() {
            return this.mPersistent || !this.isExecuted() ||
                    (this.isError() && (this.mMaxRetry == 0 || this.mTryCount < this.mMaxRetry));
        }

        // Custom execute
        @Override
        @SuppressWarnings("unused")
        public String execute() {
            // Call the listener
            this.callBeforeExecuteListeners();

            // Super execute
            String result = this.internalExecute();

            // If the command is persistent or have an error it must be rescheduled.
            // Do it only if the retry delay is more than zero.
            if (this.mRetryDelay > 0 && this.willTry()) {
                // Calc the next execution
                long nextExecution = this.now() + this.mRetryDelay;
                // Apply to all group
                this.forceNextExecutionAtDate(nextExecution, true);
            }

            // Super call listener
            this.callAfterExecuteListeners(result);

            // Return
            return result;
        }

        // Reset the command counters
        @Override
        @SuppressWarnings("unused")
        public void reset() {
            // Call the super class method
            super.reset();
            // Reset
            this.mAutoDelete = true;
        }


        /**
         * Custom serialize
         */

        // Serialize this instance
        // IMPORTANT! all parameters not serializable will be excluded.
        private void writeObject(final ObjectOutputStream out)
                throws IOException {
            // Select only the serializable parameters
            Hashtable<String, Object> paramsHolder = new Hashtable<>();
            for (String key : this.mParams.keySet()) {
                // Get the current object
                Object object = this.mParams.get(key);
                // Check if the parameter is serializable
                if (object instanceof Serializable) {
                    // Add the current object to the list
                    paramsHolder.put(key, object);
                }
            }

            // Write inside the parcel destination
            out.writeUTF(this.mMethodName == null ? "" : this.mMethodName);
            out.writeObject(paramsHolder);
            out.writeInt(this.mTryCount);
            out.writeBoolean(this.mPersistent);
            out.writeBoolean(this.mSuccess);
            out.writeInt(this.mMaxRetry);
            out.writeInt(this.mRetryDelay);
            out.writeLong(this.mNextExecution);
            out.writeUTF(this.mGroup == null ? "" : this.mGroup);
            out.writeBoolean(this.mToSave);
            out.writeBoolean(this.mAutoDelete);
        }

        // Deserialize this instance
        @SuppressWarnings("unchecked")
        private void readObject(final ObjectInputStream in)
                throws IOException, ClassNotFoundException {
            // Read all values
            this.mMethodName = in.readUTF();
            this.mParams = (Hashtable<String, Object>) in.readObject();
            this.mTryCount = in.readInt();
            this.mPersistent = in.readBoolean();
            this.mSuccess = in.readBoolean();
            this.mMaxRetry = in.readInt();
            this.mRetryDelay = in.readInt();
            this.mNextExecution = in.readLong();
            this.mGroup = in.readUTF();
            this.mToSave = in.readBoolean();
            this.mAutoDelete = in.readBoolean();

            // Validate
            if (this.mMethodName.isEmpty()) this.mMethodName = null;
            if (this.mGroup.isEmpty()) this.mGroup = null;
        }


        /**
         * Public properties
         */

        // Get/Set the persistent status
        // Default value: false
        @SuppressWarnings("unused")
        public void setPersistent(boolean value) {
            this.mPersistent = value;
        }

        @SuppressWarnings("unused")
        public Boolean getPersistent() {
            return this.mPersistent;
        }

        // Get/Set the max number of retries in error case.
        // Default value: 0 (infinite tries)
        @SuppressWarnings("unused")
        public void setMaxRetry(int value) {
            this.mMaxRetry = value;
        }

        @SuppressWarnings("unused")
        public int getMaxRetry() {
            return this.mMaxRetry;
        }

        // Get/Set the delay between tries.
        // Default value: 0 (soon as possibile)
        @SuppressWarnings("unused")
        public void setRetryDelay(int value) {
            this.mRetryDelay = value;
        }

        @SuppressWarnings("unused")
        public int getRetryDelay() {
            return this.mRetryDelay;
        }

        // Get/Set the command group.
        // Default value: null
        @SuppressWarnings("unused")
        public void setGroup(String value) {
            if (value != null && value.trim().isEmpty()) value = null;
            this.mGroup = value;
        }

        @SuppressWarnings("unused")
        public String getGroup() {
            return this.mGroup;
        }

        // Get/Set the savable status
        // Default value: true
        @SuppressWarnings("unused")
        public void setToSave(Boolean value) {
            this.mToSave = value;
        }

        @SuppressWarnings("unused")
        public boolean getToSave() {
            return this.mToSave;
        }

        // Get/Set the auto-delete trigger.
        // If true the command will be delete at the next check once spent.
        // Default value: true
        @SuppressWarnings("unused")
        public void setAutoDelete(Boolean value) {
            this.mAutoDelete = value;
        }

        @SuppressWarnings("unused")
        public boolean getAutoDelete() {
            return this.mAutoDelete;
        }

    }


    /******************************************************************************************
     * MARSHAL DOUBLE CLASS
     * Internal use only
     *****************************************************************************************/

    /**
     * Fix the double type marshalling
     */
    private class MarshalDouble implements Marshal {

        public Object readInstance(XmlPullParser parser, String namespace, String name,
                                   PropertyInfo expected) throws IOException, XmlPullParserException {

            return Double.parseDouble(parser.nextText());
        }

        public void register(SoapSerializationEnvelope cm) {
            cm.addMapping(cm.xsd, "double", Double.class, this);
        }

        public void writeInstance(XmlSerializer writer, Object obj) throws IOException {
            writer.text(obj.toString());
        }

    }

}
