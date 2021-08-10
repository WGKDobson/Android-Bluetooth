package com.example.bluetooth2;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;

import static com.example.bluetooth2.ConnectionThread.MESSAGE_READ;

// Example code from https://www.youtube.com/watch?v=TLXpDY1pItQ
// connects to BT IO card with fixed known UUID
public class MainActivity extends AppCompatActivity {
    private TextView btStatusTV, btreadMsgTV;
    private Button  btOnBtn, btdiscoverBtn, btlistPairedBtn, rpTempBtn;
    private CheckBox greenLED, yellowLED, redLED;
    private ListView btdevListView;

    private Handler btHandler;
    private BluetoothAdapter btAdapter;
    private Set<BluetoothDevice> btpairedDevices;
    private ArrayAdapter<String> btArrayAdapter;

    private ConnectionThread btConnectionThread;
    private BluetoothSocket btSocket = null;

    private final static int CONNECTING_STATUS = 3;
    private final static int REQUEST_ENABLE_BT = 1;

    private UUID BTUUID;
    private BluetoothDevice rpi4;

    private final String TAG = "Create Socket:";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btArrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1);
        // get default adapter handle
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        // DEBUG print paired devices
        System.out.println(btAdapter.getBondedDevices());

        //BluetoothDevice rpi4 = btAdapter.getRemoteDevice("DC:A6:32:65:FC:63");
        rpi4 = btAdapter.getRemoteDevice("DC:A6:32:65:FC:63");
        System.out.println(rpi4.getName());

        // setup UI
        btStatusTV = (TextView)findViewById(R.id.btstatusTV);
        btreadMsgTV = (TextView) findViewById(R.id.btreadMsgTV);
        btOnBtn = (Button)findViewById(R.id.btonbutton);
        btdiscoverBtn = (Button)findViewById(R.id.btdiscoverbutton);
        btlistPairedBtn = (Button)findViewById(R.id.btpairedbutton);
        greenLED = (CheckBox)findViewById(R.id.chk_greenLED);
        yellowLED = (CheckBox)findViewById(R.id.chk_yellowLED);
        redLED = (CheckBox)findViewById(R.id.chk_redLED);
        rpTempBtn = (Button) findViewById(R.id.rpTempButton);

        // ListView for paired or discovered device selection
        btdevListView = (ListView)findViewById(R.id.lv_devices);
        btdevListView.setAdapter(btArrayAdapter); // assign model to view
        btdevListView.setOnItemClickListener(btDeviceClickListener);

        //BTUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");   // USE THIS! hard coded serial port uuid top talk to rpi
        // BTUUID = rpi4.getUuids()[0].getUuid();  // Gets UUID from remote device but does not work with RPi code I have


        // setup message handler that is called by the receive data thread and displays it in a textview
        btHandler = new Handler(Looper.getMainLooper()){
            // handleMessage called when thread returns received data from go() in ConnectionThread
            @Override
            public void handleMessage(Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    try {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    // update display and add any message processing here
                    btreadMsgTV.setText(readMessage);
                }

                // update status textview on connection status
                if(msg.what == CONNECTING_STATUS){
                    if(msg.arg1 == 1)
                        btStatusTV.setText("Connected to Device: " + msg.obj);
                    else
                        btStatusTV.setText("Connection Failed");
                }
            }
        };

        // check that bt device exists then start LED control and other button click listeners
        if (btArrayAdapter == null) {
            // Device does not support Bluetooth
            btStatusTV.setText("Status: Bluetooth not found");
            Toast.makeText(getApplicationContext(),"Bluetooth device not found!",Toast.LENGTH_SHORT).show();
        }
        else {

            greenLED.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (btConnectionThread != null) //First check to make sure thread created
                        btConnectionThread.write("1");
                        System.out.println("Green CheckBox");
                }
            });

            yellowLED.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (btConnectionThread != null) //First check to make sure thread created
                        btConnectionThread.write("2");
                        System.out.println("Yellow CheckBox");
                }
            });

            redLED.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (btConnectionThread != null) //First check to make sure thread created
                        btConnectionThread.write("3");
                        System.out.println("Red CheckBox");
                }
            });

            rpTempBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (btConnectionThread != null) //First check to make sure thread created
                        btConnectionThread.write("4");
                    System.out.println("rpTemp Button");
                }
            });

            btOnBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    btEnable();
                }
            });

            btlistPairedBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    listPairedDevices();
                    listBtUUIDs();  // printed in log.d
                }
            });

            btdiscoverBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    discoverBtDevices();
                    //listBtUUIDs(); // printed in log
                }
            });
        }
    }   // OnCreate

    // create socket helper
    private BluetoothSocket createBTSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BTUUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BTUUID);
    }

    // Method to enable bluetooth in case it is disabled in the system
    private void btEnable(){
        if (!btAdapter.isEnabled()) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(btIntent, REQUEST_ENABLE_BT);
            btStatusTV.setText("Bluetooth Enabled");
            Toast.makeText(getApplicationContext(),"Bluetooth Enabled",Toast.LENGTH_SHORT).show();

        }
        else{
            Toast.makeText(getApplicationContext(),"Bluetooth already enabled!", Toast.LENGTH_SHORT).show();
        }
    }

    // Check bluetooth connection results
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data) {
        // Check which request we're responding to
        super.onActivityResult(requestCode, resultCode, Data);
        if (requestCode == REQUEST_ENABLE_BT) {
            // Check request was successful
            if (resultCode == RESULT_OK) {
                btStatusTV.setText("Bluetooth Enabled");
            } else
                btStatusTV.setText("Bluetooth Disabled");
        }
    }

    //Broadcast receiver object used with the discovery process to add found devices to the list
    final BroadcastReceiver btReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                btArrayAdapter.add(device.getName() + "\n" + device.getAddress());  // add device
                btArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    // Method to Discover BT devices in range and add them to arraylist
    private void discoverBtDevices(){
        // Check if the device is already discovering
        if(btAdapter.isDiscovering()){
            btAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"Discovery stopped",Toast.LENGTH_SHORT).show();
        }
        else{
            if(btAdapter.isEnabled()) {
                btArrayAdapter.clear(); // clear items
                btAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
                registerReceiver(btReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            }
            else{
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // DEBUG code to list UUIDs for paired devices
    void listBtUUIDs() {
        for (BluetoothDevice device : btpairedDevices) {
            for (ParcelUuid uuid : device.getUuids()) {
                String uuid_string = uuid.toString();
                Log.d(TAG, "uuid : " + uuid_string);
            }
        }
    }

    // Method to list paired devices in a ListView to connect upon selection
    private void listPairedDevices(){
        btArrayAdapter.clear();
        btpairedDevices = btAdapter.getBondedDevices();
        if(btAdapter.isEnabled()) {
            // put it's one to the adapter
            for (BluetoothDevice device : btpairedDevices)
                btArrayAdapter.add(device.getName() + "\n" + device.getAddress());
            Toast.makeText(getApplicationContext(), "Display Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth Disabled!", Toast.LENGTH_SHORT).show();
    }

    // Method handles ListView selection click and tries to connect to the device selected
    private AdapterView.OnItemClickListener btDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                BTUUID = rpi4.getUuids()[7].getUuid();  // <---<<< Preferred way to get remote UUID must use index=7 to get 1101 for RPi4
                                                        // WARNING This index will likely need to be changed for other devices
            // BTUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");   // USE THIS! hard coded serial port uuid top talk to RPi4 RFCOMM ONLY
            //     //other default RPi UUIDs reported from RPi4 bluetooth manager
            // BTUUID = UUID.fromString("00001132-0000-1000-8000-00805F9B34FB");   // using hard coded message server uuid
            // BTUUID = UUID.fromString("00001133-0000-1000-8000-00805F9B34FB");  // using hard coded message notification server uuid
            // BTUUID = UUID.fromString("00001108-0000-1000-8000-00805F9B34FB");  // using hard coded headset uuid
            // BTUUID = UUID.fromString("00001112-0000-1000-8000-00805F9B34FB");  // using hard coded headset ag  uuid
            // BTUUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB");  // using hard coded obex obj push uuid
            // BTUUID = UUID.fromString("00001104-0000-1000-8000-00805F9B34FB");  // using hard coded imrc sync uuid
            // BTUUID = UUID.fromString("0000112f-0000-1000-8000-00805F9B34FB");  // using hard coded phonebook access server uuid

            if(!btAdapter.isEnabled()) {
                Toast.makeText(getBaseContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
                return;
            }

            btStatusTV.setText("Connecting...");
            String info = ((TextView) view).getText().toString();
            final String address = info.substring(info.length() - 17);  // MAC addr is last 17 characters
            final String name = info.substring(0,info.length() - 17);

            // creates and spawns a new thread to connect for selected device
            new Thread()
            {
                @Override
                public void run() {
                    boolean sockfail = false;

                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    // create socket
                    try {
                        btSocket = createBTSocket(device);
                    } catch (IOException e) {
                        sockfail = true;
                        Toast.makeText(getBaseContext(), "Socket creation failed", Toast.LENGTH_SHORT).show();
                    }

                    // make bluetooth socket connection.
                    try {
                        Thread.sleep(100);  // short delay may help connection
                        btSocket.connect();
                    } catch (IOException | InterruptedException e) {
                        try {
                            sockfail = true;
                            btSocket.close();
                            btHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e1) {
                            Toast.makeText(getBaseContext(), "Create Socket Failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(!sockfail) {  // if the socket connected OK, spawn and .start() new ConnectionThread instance to receive data
                        btConnectionThread = new ConnectionThread(btSocket, btHandler);
                        btConnectionThread.start();

                        btHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();  // start thread
        }
    };

}