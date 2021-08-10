package com.example.bluetooth2;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectionThread extends Thread{
    public final static int MESSAGE_READ = 2;
    private final BluetoothSocket btSocket;
    private final InputStream btInStream;
    private final OutputStream btOutStream;
    private final Handler btHandler;
    private final int RCV_LATENCY = 100;  // rcv delay set to 100 ms gives time for message to complete
    private final int RCV_BUFFERSIZE = 1024;  // number of bytes for rcv buffer

    // constructor sets up local socket streams
    public ConnectionThread(BluetoothSocket socket, Handler handler) {
        btSocket = socket;
        btHandler = handler;
        // use temporary stream objects to get them from socket before first assignment to  real streams
        InputStream tempIn = null;
        OutputStream tempOut = null;

        try {
            tempIn = socket.getInputStream();
            tempOut = socket.getOutputStream();
        } catch (IOException e) { }

        btInStream = tempIn;
        btOutStream = tempOut;
    }

    // run thread to receive data and pass to UI
    @Override
    public void run() {
        byte[] buffer = new byte[RCV_BUFFERSIZE];  // buffer store rcv data
        int bytecnt; // bytes returned from read()
        // Listen to btInStream until break on ioexception
        while (true) {
            try {
                // Check for data in InStream
                bytecnt = btInStream.available();
                if(bytecnt != 0) {
                    buffer = new byte[RCV_BUFFERSIZE];
                    SystemClock.sleep(RCV_LATENCY); // wait for rest of data.
                    bytecnt = btInStream.available(); // get bytes available to read
                    bytecnt = btInStream.read(buffer, 0, bytecnt); // rcv data and update number of bytes actually read
                    // pass rcv data to message handler for processing and display
                    btHandler.obtainMessage(MESSAGE_READ, bytecnt, -1, buffer)
                            .sendToTarget();
                }
            } catch (IOException e) {
                e.printStackTrace();
                break;  // breaks out of rcv loop
            }
        }
    }

    // Method to transmit data
    public void write(String input) {
        byte[] bytes = input.getBytes();           //converts entered String into bytes
        try {
            btOutStream.write(bytes);
        } catch (IOException e) { }
    }

    /* Call this from the main activity to shutdown the connection */
    public void cancel() {
        try {
            btSocket.close();
        } catch (IOException e) { }
    }

}
