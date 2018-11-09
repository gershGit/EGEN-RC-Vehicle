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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.Set;
import java.util.UUID;

public class UI_Activity extends AppCompatActivity {
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Bluetooth_Sender bluetooth_sender;
    private BluetoothAdapter myBluetooth = null;
    private BluetoothSocket btSocket = null;
    private boolean bluetoothConnected = false;
    private boolean safeMode = false;

    private DecimalFormat decimalFormat = new DecimalFormat("+#;-#");
    int global_power, global_turn;
    int leftMotor = 0, rightMotor = 0;
    boolean braked = false;

    TextView powerText, turnText;
    ImageView brakeView, safeView, bluetoothView, turnView, speedView;
    boolean LED_STATE = false;

    long timeSinceLastSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ui_);
        powerText = findViewById(R.id.powerText);
        turnText = findViewById(R.id.turnText);
        brakeView = findViewById(R.id.brake_button);
        bluetoothView = findViewById(R.id.bluetoothButton);
        safeView = findViewById(R.id.safeButtonNew);
        turnView = findViewById(R.id.turnImage);
        speedView = findViewById(R.id.speedImage);

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
        int actionIndex = event.getActionIndex();
        int action = event.getActionMasked();
        int touchCount = event.getPointerCount();
        //Log.i(TAG, "Pos: " + event.getX() + ", " + event.getY());
        boolean speedTouched = false;
        boolean turnTouched = false;


        for (int i=0; i<touchCount; i++){
            if (event.getX(i) > 1100 && event.getX(i) < 1600 && event.getY(i) > 250 && event.getY(i) < 875){
                speedTouched = true;
                if (actionIndex==i && action == MotionEvent.ACTION_UP){
                    speedView.setImageResource(R.mipmap.speed_normal);
                } else {
                    speedView.setImageResource(R.mipmap.speed_pressed);
                }
                if (!turnTouched) {
                    turnView.setImageResource(R.mipmap.turning_normal);
                }
                controlSpeed(event.getY(i));
            } else if (event.getX(i) > 300 && event.getX(i) < 750 && event.getY(i) > 400 && event.getY(i) < 650) {
                turnTouched = true;
                if (actionIndex==i && action == MotionEvent.ACTION_UP){
                    turnView.setImageResource(R.mipmap.turning_normal);
                } else {
                    turnView.setImageResource(R.mipmap.turning_pressed);
                }
                if (!speedTouched){
                    speedView.setImageResource(R.mipmap.speed_normal);
                }
                controlTurn(event.getX(i));
            } else {
                if (!speedTouched) {
                    speedView.setImageResource(R.mipmap.speed_normal);
                }
                if (!turnTouched) {
                    turnView.setImageResource(R.mipmap.turning_normal);
                }
            }
        }
        if (!safeMode) {
            sendAndDisplay(false);
        }
        return super.onTouchEvent(event);
    }

    private void sendAndDisplay(boolean force) {
        //Log.d("UI", "Time since last send: " + (SystemClock.uptimeMillis() - timeSinceLastSend));

        float rightTurnMultiplier;
        if (global_turn <= 0) {
            rightTurnMultiplier = 100.0f;
        } else {
            rightTurnMultiplier = (-global_turn * 2) + 100;
        }
        rightMotor = (int) ((global_power / 100.0f) * (rightTurnMultiplier / 100.0f) * 9);

        float leftTurnMultiplier;
        if (global_turn >= 0) {
            leftTurnMultiplier = 100.0f;
        } else {
            leftTurnMultiplier = global_turn * 2 + 100;
        }
        leftMotor = (int) ((global_power / 100.0f) * (leftTurnMultiplier / 100.0f) * 9);

        if (leftMotor != 0 || rightMotor != 0) {
            braked = false;
            brakeView.setImageResource(R.mipmap.brake_button);
        }

        if (((SystemClock.uptimeMillis() - timeSinceLastSend) > 200) || force) {
            timeSinceLastSend = SystemClock.uptimeMillis();
            sendAll(leftMotor, rightMotor);
        }

        turnText.setText(global_turn + "%");
        powerText.setText(global_power + "%");
    }

    public void brakeVehicle(View view) {
        if (safeMode){
            sendBT("B");
        } else {
            global_turn = 0;
            global_power = 0;
            braked = true;
            brakeView.setImageResource(R.mipmap.brake_pressed);
            sendAndDisplay(true);
        }
    }

    public void decrementTurn(View view) {
        if (safeMode){
            sendBT("<");
        } else {
            global_turn -= 2;
            if (global_turn < -100) {
                global_turn = -100;
            }
            sendAndDisplay(true);
        }
    }

    public void incrementTurn(View view) {
        if (safeMode){
            sendBT(">");
        } else {
            global_turn += 2;
            if (global_turn > 100) {
                global_turn = 100;
            }
            sendAndDisplay(true);
        }
    }

    public void decrementSpeed(View view) {
        if (safeMode){
            sendBT("G");
        } else {
            global_power -= 2;
            if (global_power < -100) {
                global_power = -100;
            }
            sendAndDisplay(true);
        }
    }

    public void incrementSpeed(View view) {
        if (safeMode){
            sendBT("F");
        } else {
            global_power += 2;
            if (global_power > 100) {
                global_power = 100;
            }
            sendAndDisplay(true);
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
                        bluetoothView.setImageResource(R.mipmap.bluetooth_on);
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

    private boolean connectBluetooth(String address, String name){
        try {
            BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
            btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            btSocket.connect();
            bluetoothConnected = true;
            bluetooth_sender = new Bluetooth_Sender(btSocket);
            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    private void sendAll(int leftStrength, int rightStrength){
        String message = decimalFormat.format(leftStrength) + "" + decimalFormat.format(rightStrength);
        if (LED_STATE) {
                message += "1";
        } else {
                message += "0";
        }
        if (braked) {
            message += "1>";
        } else {
            message += "0>";
        }
        sendBT(message);
    }

    private void sendBT(String message){
        if (bluetooth_sender != null) {
            bluetooth_sender.setMessage(message);
            bluetooth_sender.run();
        } else {
            Log.d("BT", "No connection");
        }
    }

    public void switchSafeMode(View view){
        safeMode = !safeMode;
        if (safeMode){
            safeView.setImageResource(R.mipmap.safe_on);
            sendBT("_");
        } else {
            safeView.setImageResource(R.mipmap.safe_off);
            sendBT("=");
        }
    }

    public void cycleGUI(View view){
        Intent intent = new Intent(this, Motor_Direct.class);
        startActivity(intent);
    }

    public void toggleLED(View view){
        ImageView thisView = (ImageView) view;
        LED_STATE = !LED_STATE;
        if (LED_STATE){
            if (safeMode){
                sendBT("O");
            }
            thisView.setImageResource(R.mipmap.headlights_pressed);
        } else {
            if (safeMode){
                sendBT("P");
            }
            thisView.setImageResource(R.mipmap.headlights_normal);
        }
        if (!safeMode) {
            sendAndDisplay(true);
        }
    }
}
