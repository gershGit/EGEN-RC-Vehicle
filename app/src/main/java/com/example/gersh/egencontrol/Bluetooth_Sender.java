package com.example.gersh.egencontrol;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class Bluetooth_Sender implements Runnable {
    private BluetoothSocket btSocket;
    private String message;

    Bluetooth_Sender(BluetoothSocket new_btSocket){
        btSocket = new_btSocket;
    }

    public void setMessage(String new_message){
        message = new_message;
    }

    public void setBtSocket(BluetoothSocket new_btSocket){
        btSocket = new_btSocket;
    }

    @Override
    public void run() {
        Log.d("SendThread", "Sending: " + message);
        if (btSocket!=null){
            try {
                btSocket.getOutputStream().write(message.getBytes());
            } catch (Exception e){
                Log.d("BT", "Bluetooth Sending Failed");
                e.printStackTrace();
            }
        }
    }
}
