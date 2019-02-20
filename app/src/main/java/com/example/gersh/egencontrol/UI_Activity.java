/*
* Noah Gershmel
* Activity that acts as the main controller
*/

package com.example.gersh.egencontrol;

//Imports
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

//Class extends AppCompatActivity to support Android 4.4 
public class UI_Activity extends AppCompatActivity {
    //UUID for the bluetooth module
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    //Bluetooth related private instance variables
    private Bluetooth_Sender bluetooth_sender;
    private BluetoothAdapter myBluetooth = null;
    private BluetoothSocket btSocket = null;
    private boolean safeMode = false;

    //Instance variables for command calculation and storing
    private DecimalFormat decimalFormat = new DecimalFormat("+#;-#");
    int global_power, global_turn;
    int leftMotor = 0, rightMotor = 0;
    int reverseValue = 1;
    boolean braked = false;

    //Variables for updating the screen accurately
    TextView powerText, turnText;
    ImageView brakeView, safeView, bluetoothView, turnView, speedView;
    boolean LED_STATE = false;

    //Used to ensure commands aren't being sent at too high of a rate
    long timeSinceLastSend;

    //Function runs on app startup
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);     //Call the parents onCreate
        setContentView(R.layout.activity_ui_);  //Set the layout
        //Map the UI elements to variables
        powerText = findViewById(R.id.powerText);
        turnText = findViewById(R.id.turnText);
        brakeView = findViewById(R.id.brake_button);
        bluetoothView = findViewById(R.id.bluetoothButton);
        safeView = findViewById(R.id.safeButtonNew);
        turnView = findViewById(R.id.turnImage);
        speedView = findViewById(R.id.speedImage);

        //Find the bluetooth information and ensure it is activated
        myBluetooth = BluetoothAdapter.getDefaultAdapter();
        if  (myBluetooth == null){
            Toast.makeText(getApplicationContext(), "Bluetooth not working", Toast.LENGTH_LONG).show();
        } else {
            //Get permission and enable bluetooth if necessary
            if (!myBluetooth.isEnabled()){
                Intent turnBluetoothOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(turnBluetoothOn,1);
            }
        }

        //Reset the timeSinceLastSend variable
        timeSinceLastSend = SystemClock.uptimeMillis();
    }

    //Runs on app closing
    @Override
    protected void onDestroy(){
        super.onDestroy();
        //Destroy the bluetooth socket
        if (btSocket!=null){
            try {
                btSocket.close();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    //Runs when the screen is touched
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //Store touch information
        int actionIndex = event.getActionIndex();
        int action = event.getActionMasked();
        int touchCount = event.getPointerCount();
        //Variables used to determine if joystick should glow
        boolean speedTouched = false;
        boolean turnTouched = false;

        //Loop throught all touches and handle appropriately
        for (int i=0; i<touchCount; i++){
            //This is the range for the power joystick
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
                controlSpeed(event.getY(i)); //Call the function to set the new speed
            } else if (event.getX(i) > 300 && event.getX(i) < 750 && event.getY(i) > 400 && event.getY(i) < 650) {
                //This is the range for the turn joystick
                turnTouched = true;
                if (actionIndex==i && action == MotionEvent.ACTION_UP){
                    turnView.setImageResource(R.mipmap.turning_normal);
                } else {
                    turnView.setImageResource(R.mipmap.turning_pressed);
                }
                if (!speedTouched){
                    speedView.setImageResource(R.mipmap.speed_normal);
                }
                controlTurn(event.getX(i)); //Call the function to set the new power based on turn amount
            } else {
                //Turn off glow from untouched joysticks
                if (!speedTouched) {
                    speedView.setImageResource(R.mipmap.speed_normal);
                }
                if (!turnTouched) {
                    turnView.setImageResource(R.mipmap.turning_normal);
                }
            }
        }
        //Send and display the updates from the touches if not in safe mode
        if (!safeMode) {
            sendAndDisplay(false); //Don't force instant sending
        }
        return super.onTouchEvent(event);
    }

    //Function to send commands to the Arduino and display on the Android
    //Allows for a force input that ensures the command sends regardless of time
    private void sendAndDisplay(boolean force) {
        //Calculate the value for the left motor from -9 to 9
        float leftTurnMultiplier;
        if (global_turn <= 0) {
            leftTurnMultiplier = 100.0f;
        } else {
            leftTurnMultiplier = (-global_turn * 2) + 100;
        }
        leftMotor = (int) ((global_power / 100.0f) * (leftTurnMultiplier / 100.0f) * 9);

        //Calculate the value for the right motor from -9 to 9
        float rightTurnMultiplier;
        if (global_turn >= 0) {
            rightTurnMultiplier = 100.0f;
        } else {
            rightTurnMultiplier = global_turn * 2 + 100;
        }
        rightMotor = (int) ((global_power / 100.0f) * (rightTurnMultiplier / 100.0f) * 9);

        //Turn off the brake button if a motor is not off
        if (leftMotor != 0 || rightMotor != 0) {
            braked = false;
            brakeView.setImageResource(R.mipmap.brake_button);
        }

        //Send the calculated values if it has been 200ms
        if (((SystemClock.uptimeMillis() - timeSinceLastSend) > 200) || force) {
            timeSinceLastSend = SystemClock.uptimeMillis();
            leftMotor *= reverseValue;
            rightMotor *= reverseValue;
            //Flip the motors if they are in reverse mode
            if (reverseValue==-1){
                int temp = leftMotor;
                leftMotor = rightMotor;
                rightMotor = temp;
            }
            sendAll(leftMotor, rightMotor); //Call the function to actually send the information
        }

        //Set the text on the Android to the new values as percents
        String turn = global_turn + "%";
        String power = global_power + "%";
        turnText.setText(turn);
        powerText.setText(power);
    }

    //Function to immediately brake the vehicle
    public void brakeVehicle(View view) {
        //Send the safe mode brake signal if necessary
        if (safeMode){
            sendBT("B");
        } else {
            //Shut off all motors, turn on the brake button, force send
            global_turn = 0;
            global_power = 0;
            braked = true;
            brakeView.setImageResource(R.mipmap.brake_pressed);
            sendAndDisplay(true); //Force send
        }
    }

    //Decrements the turn amount by 2%
    public void decrementTurn(View view) {
        if (safeMode){
            sendBT("<");
        } else {
            global_turn -= 2;
            //Clamp to -100
            if (global_turn < -100) {
                global_turn = -100;
            }
            sendAndDisplay(true); //Force send
        }
    }

    //Increments the turn amount by 2%
    public void incrementTurn(View view) {
        if (safeMode){
            sendBT(">");
        } else {
            global_turn += 2;
            //Clamp to 100
            if (global_turn > 100) {
                global_turn = 100;
            }
            sendAndDisplay(true); //Force send
        }
    }

    //Decrements the speed by 2%
    public void decrementSpeed(View view) {
        if (safeMode){
            sendBT("G");
        } else {
            global_power -= 2;
            //Clamp to -100
            if (global_power < -100) {
                global_power = -100;
            }
            sendAndDisplay(true); //Force send
        }
    }

    //Increments speed by 2%
    public void incrementSpeed(View view) {
        if (safeMode){
            sendBT("F");
        } else {
            global_power += 2;
            //Clamp to 100
            if (global_power > 100) {
                global_power = 100;
            }
            sendAndDisplay(true); //Force send
        }
    }

    //Find the correct speed from joystick position
    private void controlSpeed(float position){
        float max = 300, min = 800;
        float power = ((min-position) / (min-max)) * 200 - 100;
        //Clamp to -100 to 100
        if (power>100){
            power=100;
        } else if (power<-100){
            power = -100;
        }
        global_power = (int) power;
    }

    //Find the correct turn amount from joystick position
    private void controlTurn(float position){
        int max = 750, min = 300;
        float turnAmount = ((position-min) / (max-min)) * 200 - 100;
        //Clamp from -100 to 100
        if (turnAmount>100){
            turnAmount=100;
        } else if (turnAmount<-100){
            turnAmount = -100;
        }
        global_turn = (int) turnAmount;
    }

    //Attempts to connect to the paired Arduino
    public void attemptBluetooth(View view){
        //Create a set from all paired bluetooth devices
        Set<BluetoothDevice> pairedDevices = myBluetooth.getBondedDevices();

        //If we have at least one bluetooth device paired, search for the Arduino module
        if (pairedDevices.size()>0){
            boolean found = false;
            for (BluetoothDevice bt : pairedDevices){
                //Compare the name against the model we are looking for
                if (bt.getName().contains("HC-05")) {
                    found = true;
                    boolean success = connectBluetooth(bt.getAddress()); //Call the connect function on that address
                    //Display whether or not the connection attempt was successful
                    if(success) {
                        Toast.makeText(getApplicationContext(), "Connected to Arduino", Toast.LENGTH_SHORT).show();
                        bluetoothView.setImageResource(R.mipmap.bluetooth_on);
                    } else {
                        Toast.makeText(getApplicationContext(), "Arduino found, Connection failed", Toast.LENGTH_LONG).show();
                    }
                }
            }
            //If none of the paired devices match the arduino display that info
            if (!found){
                Toast.makeText(getApplicationContext(), "No arduino paired", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Could not find any paired devices", Toast.LENGTH_LONG).show();
        }
    }

    //Attempts a connection to a paired bluetooth device
    private boolean connectBluetooth(String address){
        try {
            //Try connecting using the default adapter and UUID
            BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(address);
            btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
            btSocket.connect();
            bluetooth_sender = new Bluetooth_Sender(btSocket);
            return true;
        } catch (Exception e){
            //catch a failed attempt and return false
            e.printStackTrace();
            return false;
        }
    }

    //Formats a bluetooth message and passes it to the sending function
    private void sendAll(int leftStrength, int rightStrength){
        //Format motors as strings
        String message = decimalFormat.format(leftStrength) + "" + decimalFormat.format(rightStrength);
        //Append headlights setting
        if (LED_STATE) {
                message += "1";
        } else {
                message += "0";
        }
        //Append brakelight setting and end character
        if (braked) {
            message += "1>";
        } else {
            message += "0>";
        }
        //Call the function to send a message
        sendBT(message);
    }

    //Sends information over the connected bluetooth socket
    private void sendBT(String message){
        if (bluetooth_sender != null) {
            bluetooth_sender.setMessage(message); //Set the message instance variable of the bluetooth sender
            bluetooth_sender.run(); //Run the bluetooth sender on its own thread
        } else {
            //Report no connection if it attempting to send over a non existent connection
            Log.d("BT", "No connection");
        }
    }

    //Switches the command style to and from safe mode
    public void switchSafeMode(View view){
        safeMode = !safeMode; //Flip safe mode
        //Flip the image and send the safe/notsafe command to the Arduino
        if (safeMode){
            safeView.setImageResource(R.mipmap.safe_on);
            sendBT("_");
        } else {
            safeView.setImageResource(R.mipmap.safe_off);
            sendBT("=");
        }
    }

    //Launch the second GUI
    public void cycleGUI(View view){
        Intent intent = new Intent(this, Motor_Direct.class);
        startActivity(intent);
    }

    //Flip the state of the headlights
    public void toggleLED(View view){
        ImageView thisView = (ImageView) view;
        LED_STATE = !LED_STATE; //Flip the state
        //Flip the image and send if in safe mode
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
        //Force a command to send if not in safe mode
        if (!safeMode) {
            sendAndDisplay(true);
        }
    }

    //Flips the control scheme to run the car in the opposite direction
    public void reverseDirection(View view) {
        ImageView thisView = (ImageView) view;
        //Flip the reversValue to be multiplied by and switch the image
        if (reverseValue == 1){
            reverseValue = -1;
            thisView.setImageResource(R.mipmap.reverse_on);
        } else {
            reverseValue = 1;
            thisView.setImageResource(R.mipmap.reverse_off);
        }
    }
}
