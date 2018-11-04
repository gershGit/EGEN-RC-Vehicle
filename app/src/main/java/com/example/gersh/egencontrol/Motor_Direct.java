package com.example.gersh.egencontrol;

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

import java.text.DecimalFormat;
import java.util.Set;
import java.util.UUID;

public class Motor_Direct extends AppCompatActivity {
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private Bluetooth_Listener bluetooth_listener;
    private Bluetooth_Sender bluetooth_sender;
    private BluetoothAdapter myBluetooth = null;
    private BluetoothSocket btSocket = null;
    private boolean bluetoothConnected = false;
    private boolean safeMode = false;

    public String TAG = "UI_ACTIVITY";
    private DecimalFormat decimalFormat = new DecimalFormat("+#;-#");
    int leftMotor = 0, rightMotor = 0;
    int leftPercent= 0, rightPercent = 0;
    boolean braked = false;

    TextView rightText, leftText;
    boolean LED_STATE = false;

    long timeSinceLastSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motor__direct);
        rightText = findViewById(R.id.rightMotorText);
        leftText = findViewById(R.id.leftMotorText);

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

        for (int i=0; i<touchCount; i++) {
            if (event.getY(i) > 650 && event.getY(i) < 1650){
                if (event.getX(i) > 675 && event.getX(i) < 875) {
                    controlRight(event.getY(i));
                } else if (event.getX(i) > 250 && event.getX(i) < 400) {
                    controlLeft(event.getY(i));
                }
            }
        }


        sendAndDisplay();

        return super.onTouchEvent(event);
    }

    private void controlRight(float position){
        float max = 1650, min = 650;
        float power = ((max-position) / (max-min)) * 200 - 100;
        if (power>100){
            power=100;
        } else if (power<-100){
            power = -100;
        }
        if (rightPercent!=0 && braked) {
            braked = false;
            LED_STATE = false;
        }
        rightPercent = (int) power;
    }

    private void controlLeft(float position){
        float max = 1650, min = 650;
        float power = ((max-position) / (max-min)) * 200 - 100;
        if (power>100){
            power=100;
        } else if (power<-100){
            power = -100;
        }
        if (leftPercent!=0 && braked) {
            braked = false;
            LED_STATE = false;
        }
        leftPercent = (int) power;
    }

    private void sendAndDisplay() {
        if (SystemClock.uptimeMillis()-timeSinceLastSend > 100) {
            timeSinceLastSend = SystemClock.uptimeMillis();

            leftMotor = leftPercent/10;
            if (leftMotor>9){
                leftMotor = 9;
            } else if (leftMotor<-9){
                leftMotor = -9;
            }
            rightMotor = rightPercent/10;
            if (rightMotor>9){
                rightMotor = 9;
            } else if (rightMotor<-9){
                rightMotor = -9;
            }

            sendAll(leftMotor, rightMotor, LED_STATE);
        }

        rightText.setText(rightPercent + "%");
        leftText.setText(leftPercent + "%");
    }

    public void brakeVehicle(View view) {
        rightPercent = 0;
        leftPercent = 0;
        braked = true;
        LED_STATE = true;
        sendAndDisplay();
    }

    public void toggleLED(View view){
        LED_STATE = !LED_STATE;
        sendAndDisplay();
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

    private boolean connectBluetooth(String address, String name)                           {
        try {
            BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
            btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            btSocket.connect();
            bluetoothConnected = true;
            //bluetooth_listener = new Bluetooth_Listener(btSocket);
            bluetooth_sender = new Bluetooth_Sender(btSocket);
            //bluetooth_listener.start();
            return true;
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
    }

    private void sendAll(int leftStrength, int rightStrength, boolean led_state){
        String message = decimalFormat.format(leftStrength) + "" + decimalFormat.format(rightStrength);
        if(led_state){
            message+="1>";
        } else {
            message+="0>";
        }
        sendBT(message);
    }

    private void sendBT(String message){
        if (bluetooth_sender != null) {
            bluetooth_sender.setMessage(message);
            bluetooth_sender.run();
        } else {
            Log.d("FAIL", "No bluetooth");
        }
    }
}
