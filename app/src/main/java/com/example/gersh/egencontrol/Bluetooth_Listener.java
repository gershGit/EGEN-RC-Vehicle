/*
* Noah Gershmel
* Class to receive bluetooth messages and report to the main activity
*/

package com.example.gersh.egencontrol;

import android.bluetooth.BluetoothSocket;
import android.support.annotation.MainThread;
import android.util.Log;

import java.io.*;
import java.net.*;

//Extends thread to allow it to run indefinetely in the background
public class Bluetooth_Listener extends Thread {
    //The main thread and the socket it is using
    MainThread parent;
    BluetoothSocket importedSocket;

    //A buffer to store the incoming message into
    private byte[] readBuffer;
    int readBufferPosition;

    //Constructor requires a socket
    public Bluetooth_Listener(BluetoothSocket socket){
        importedSocket = socket;
    }

    //Sets the parent to the main thread passed in
    public void setParent(MainThread c){
        parent = c;
    }

    //Sets the socket to a new socket
    public void setImportedSocket(BluetoothSocket socket){
        importedSocket = socket;
    }

    //Function that runs on a new thread
    public void run(){
        //ASCII value for the delimiter character
        final byte delimiter = 10;

        try{
            //Stores strings from the bluetooth module
            String fromClient;
            String toClient;
            InputStream m_stream = importedSocket.getInputStream();

            boolean run = true;
            //Run indefinetely in the background
            while (run) {
                int bytesAvailable = m_stream.available();
                //As soon as bytes become available read them in
                if (bytesAvailable >0) {
                    byte[] packetBytes = new byte[bytesAvailable];
                    m_stream.read(packetBytes); //Stores from the input to the stream
                    for (int i = 0; i < bytesAvailable; i++) {
                        //Continue reading in bytes until delimiter is found
                        byte b = packetBytes[i];
                        if (b == delimiter){
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                            String data = new String(encodedBytes, "US-ASCII");
                            readBufferPosition = 0;
                            handleMessage(data);    //Call the function to handle a new message
                        } else {
                            readBuffer[readBufferPosition++] = b;
                        }
                    }
                }
            }
            System.out.println("\nServer loop ended\n");
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    //Logs a new message so the user knows what information is being passed
    private void handleMessage(String message){
        Log.i("BT", "\n\n------------ " + message + " ------------\n\n");
    }
}
