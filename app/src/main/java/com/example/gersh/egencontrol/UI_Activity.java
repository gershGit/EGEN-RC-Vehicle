package com.example.gersh.egencontrol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;
import java.util.UUID;

public class UI_Activity extends AppCompatActivity {
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Bluetooth_Listener bluetooth_listener;
    private BluetoothAdapter myBluetooth = null;
    private BluetoothSocket btSocket = null;
    private boolean bluetoothConnected = false;

    public String TAG = "UI_ACTIVITY";
    int global_power, global_turn;
    int temperature;

    TextView powerText, turnText;
    boolean LED_STATE = false;
    boolean LED_STATE_CHANGE = false;
    boolean MOTOR_STATE_CHANGE = false;

    long timeSinceLastSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ui_);
        powerText = findViewById(R.id.powerText);
        turnText = findViewById(R.id.turnText);

        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        if  (myBluetooth == null){
            Toast.makeText(getApplicationContext(), "Bluetooth not working", Toast.LENGTH_LONG).show();
        } else {
            if (!myBluetooth.isEnabled()){
                Intent turnBluetoothOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnBluetoothOn,1);
            }
        }
        timeSinceLastSend = SystemClock.uptimeMillis();
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (btSocket!=null){
            try {
                btSocket.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int touchCount = event.getPointerCount();
        //Log.i(TAG, "Pos: " + event.getX() + ", " + event.getY());

        for (int i=0; i<touchCount; i++){
            if (event.getX(i) > 1100 && event.getX(i) < 1600){
                MOTOR_STATE_CHANGE = true;
                if (event.getY(i) < 300 && event.getY(i) > 250){
                    if (action == MotionEvent.ACTION_UP) {
                        incrementSpeed();
                    }
                } else if (event.getY(i) > 800 && event.getY(i) < 875) {
                    if (action == MotionEvent.ACTION_UP) {
                        decrementSpeed();
                    }
                } else if (event.getY(i) > 250 && event.getY(i) < 875) {
                    controlSpeed(event.getY(i));
                }
            } else if (event.getX(i) > 225 && event.getX(i) < 825){
                MOTOR_STATE_CHANGE = true;
                if (event.getX(i) < 850 && event.getX(i) > 750){
                    if (event.getY(i) > 400 && event.getY(i) < 650 && action == MotionEvent.ACTION_UP) {
                        incrementTurn();
                    }
                } else if (event.getX(i) > 200 && event.getX(i) < 300) {
                    if (event.getY(i) > 400 && event.getY(i) < 650 && action == MotionEvent.ACTION_UP) {
                        decrementTurn();
                    }
                } else if (event.getX(i) > 300 && event.getX(i) < 750 && event.getY(i) > 400 && event.getY(i) < 650) {
                    controlTurn(event.getX(i));
                }
            } else if (event.getX(i) < 1150 && event.getX(i)> 775 && event.getY(i) > 875 && event.getY(i) < 1000){
                MOTOR_STATE_CHANGE = true;
                brakeVehicle();
            } else if (event.getX(i) < 1125 && event.getX(i) > 760 && event.getY(i) > 110 && event.getY(i) < 210){
                LED_STATE = !LED_STATE;
                LED_STATE_CHANGE = true;
            }
        }

        sendAndDisplay();

        return super.onTouchEvent(event);
    }

    private void sendAndDisplay() {
        Log.d(TAG, "Time since last send: " + timeSinceLastSend);
        if (SystemClock.uptimeMillis()-timeSinceLastSend > 150) {
            timeSinceLastSend = SystemClock.uptimeMillis();
            if (MOTOR_STATE_CHANGE) {
                float rightTurnMultiplier;
                if (global_turn <= 0) {
                    rightTurnMultiplier = 100.0f;
                } else {
                    rightTurnMultiplier = -global_turn;
                }
                float rightMotor = (global_power / 100.0f) * (rightTurnMultiplier / 100.0f);
                sendMotor("r", rightMotor);

                float leftTurnMultiplier;
                if (global_turn >= 0) {
                    leftTurnMultiplier = 100.0f;
                } else {
                    leftTurnMultiplier = global_turn;
                }
                float leftMotor = (global_power / 100.0f) * (leftTurnMultiplier / 100.0f);
                sendMotor("l", leftMotor);
            }

            if (LED_STATE && LED_STATE_CHANGE) {
                sendBT("L1>");
            } else if (LED_STATE_CHANGE) {
                sendBT("L0>");
            }
        }

        LED_STATE_CHANGE = false;
        MOTOR_STATE_CHANGE = false;

        turnText.setText(global_turn + "%");
        powerText.setText(global_power + "%");
    }

    private void brakeVehicle() {
        global_turn = 0;
        global_power = 0;
    }

    private void decrementTurn() {
        global_turn-=2;
        if (global_turn < -100){
            global_turn = -100;
        }
    }

    private void incrementTurn() {
        global_turn+=2;
        if (global_turn > 100){
            global_turn = 100;
        }
    }

    private void decrementSpeed() {
        global_power-=2;
        if (global_power<-100){
            global_power = 100;
        }
    }

    private void incrementSpeed() {
        global_power+=2;
        if (global_power>100){
            global_power=100;
        }
    }

    private void controlSpeed(float position){
        float max = 300, min = 800;
        float power = ((min-position) / (min-max)) * 200 - 100;
        if (power>100){
            power=100;
        } else if (power<-100){
            power = -100;
        }
        global_power = (int) power;
    }

    private void controlTurn(float position){
        int max = 750, min = 300;
        float turnAmount = ((position-min) / (max-min)) * 200 - 100;
        if (turnAmount>100){
            turnAmount=100;
        } else if (turnAmount<-100){
            turnAmount = -100;
        }
        global_turn = (int) turnAmount;
    }

    public void attemptBluetooth(View view){
        Set<BluetoothDevice> pairedDevices = myBluetooth.getBondedDevices();

        if (pairedDevices.size()>0){
            boolean found = false;
            for (BluetoothDevice bt : pairedDevices){
                if (bt.getName().contains("HC-05")) {
                    found = true;
                    boolean success = connectBluetooth(bt.getAddress(), bt.getName());
                    if(success) {
                        Toast.makeText(getApplicationContext(), "Connected to Arduino", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), "Arduino found, Connection failed", Toast.LENGTH_LONG).show();
                    }
                }
            }
            if (!found){
                Toast.makeText(getApplicationContext(), "No arduino paired", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Could not find any paired devices", Toast.LENGTH_LONG).show();
        }
    }

    public void setTemperature(int temp){
        temperature = temp;
    }

    private boolean connectBluetooth(String address, String name){
        try {
            BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
            btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            btSocket.connect();
            bluetoothConnected = true;
            bluetooth_listener = new Bluetooth_Listener(btSocket);
            bluetooth_listener.start();
            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    private void sendMotor(String name, float strength){
        int strength_int = (int) (strength*10);
        sendBT("M"+name+strength_int+">");
    }

    private void sendBT(String message){
        Log.d(TAG, "Sending: " + message);
        if (btSocket!=null){
            try {
                btSocket.getOutputStream().write(message.getBytes());
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }
}
