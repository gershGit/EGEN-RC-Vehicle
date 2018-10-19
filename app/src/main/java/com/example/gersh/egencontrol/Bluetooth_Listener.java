package com.example.gersh.egencontrol;

import android.bluetooth.BluetoothSocket;
import android.support.annotation.MainThread;
import android.util.Log;

import java.io.*;
import java.net.*;

public class Bluetooth_Listener extends Thread {
    MainThread parent;
    BluetoothSocket importedSocket;

    private byte[] readBuffer;
    int readBufferPosition;

    public Bluetooth_Listener(BluetoothSocket socket){
        importedSocket = socket;
    }

    public void setParent(MainThread c){
        parent = c;
    }

    public void setImportedSocket(BluetoothSocket socket){
        importedSocket = socket;
    }

    public void run(){
        final byte delimiter = 10;

        try{
            String fromClient;
            String toClient;
            InputStream m_stream = importedSocket.getInputStream();

            boolean run = true;
            while (run) {
                int bytesAvailable = m_stream.available();
                if (bytesAvailable >0) {
                    byte[] packetBytes = new byte[bytesAvailable];
                    m_stream.read(packetBytes);
                    for (int i = 0; i < bytesAvailable; i++) {
                        byte b = packetBytes[i];
                        if (b == delimiter){
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                            String data = new String(encodedBytes, "US-ASCII");
                            readBufferPosition = 0;
                            handleMessage(data);
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

    private void handleMessage(String message){
        Log.i("BT", message);
    }
}
