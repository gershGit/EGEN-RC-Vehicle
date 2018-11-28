/*
* Noah Gershmel
* Runnable class to send commands over bluetooth
*/

package com.example.gersh.egencontrol;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

//Implements Runnable to allow for threading
public class Bluetooth_Sender implements Runnable {
    //Requires a socket and a message to send
    private BluetoothSocket btSocket;
    private String message;

    //Constructor takes in the socket it will use for sending
    Bluetooth_Sender(BluetoothSocket new_btSocket){
        btSocket = new_btSocket;
    }

    //Sets the message variable to prep for sending
    public void setMessage(String new_message){
        message = new_message;
    }

    //Sets a new socket if the original had an error
    public void setBtSocket(BluetoothSocket new_btSocket){
        btSocket = new_btSocket;
    }

    //Override the run function to spawn a thread in parrallel
    @Override
    public void run() {
        //Log what is attempting to be sent
        Log.d("SendThread", "Sending: " + message);
        if (btSocket!=null){
            try {
                //Translate the message to bytes and write to the sockets output stream
                btSocket.getOutputStream().write(message.getBytes());
            } catch (Exception e){
                //Log a failure in sending if one occurs
                Log.d("BT", "Bluetooth Sending Failed");
                e.printStackTrace();
            }
        }
    }
}
