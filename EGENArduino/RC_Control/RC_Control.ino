#include <Servo.h>

#define HEADLIGHTS 8
#define BRAKELIGHTS 9
#define LEFT_BLINKER 6
#define RIGHT_BLINKER 7

Servo servoLeft;
Servo servoRight;

//Single character from the Serial and string to hold an entire command
char state = 0;
char commandString[15];
int i = 0;
int commandTypes = 0;

//Blinker globals
unsigned long lastRightOn = 0;
unsigned long lastLeftOn = 0;
unsigned long lastBackOn = 0;
int lastLeft = 0;
int lastRight = 0;

//Function prototypes
void handleCommand(char command[]);
void handleSafe(char command);
void printCommand(char command[]);
int getMotorValueLeft(int baseValue);
int getMotorValueRight(int baseValue);
void setBlinkers(int left, int right);

//Setup function to run at startup
void setup() {
  //Set default LED to off
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);

  //Begin reading serial (bluetooth incoming)
  Serial.begin(9600);

  //Attach servos to pins
  servoLeft.attach(11);
  servoRight.attach(12);

  //Set digital IO as outputs for LEDS
  pinMode(HEADLIGHTS, OUTPUT); 
  pinMode(BRAKELIGHTS, OUTPUT); 
  pinMode(LEFT_BLINKER, OUTPUT); 
  pinMode(RIGHT_BLINKER, OUTPUT);
  //Set all lights to off by default 
  digitalWrite(HEADLIGHTS, LOW);
  digitalWrite(BRAKELIGHTS, LOW);
  digitalWrite(LEFT_BLINKER, LOW);
  digitalWrite(RIGHT_BLINKER, LOW);
}

//Function that loops during the entirety of the control
//Blocks at Serial.available to wait for a new command
void loop() {
  // Check if there is anything available, block thread until there is
  if (Serial.available() > 0){
    //Continuosly read data while available   
    while (Serial.available()>0){
      state = Serial.read();        //Read what comes in through the bluetooth module
      commandString[i] = state;

      //Change to safe driving mode if a _ command is received
      if (state == '_'){
        commandTypes = 1;
        i=0;
        break;
      } else if (state == '='){ //Switches out of safe driving mode and resets the command buffer
        commandTypes = 0;
        i=0;
        break;
      }
      //Handles commands when safe mode is on
      if (commandTypes == 1){
        handleSafe(state);
        break;
      }
      
      //End of command
      if (state == '>'){
        commandString[i+1] = '\0'; //null terminate the string
        handleCommand(commandString); //decode the command
        i=0;
        break; //jump to handling of command
      }
      i++;
    }
  } 
  setBlinkers(lastLeft, lastRight); 
}

//Safeguard mode where all commands are a single character
void handleSafe(char command){
  //  F --> Forward
  //  G --> Backwards
  //  > --> Right
  //  < --> Left
  //  B --> Brake
  //  O --> Headlights On
  //  P --> Headlights Off
  if (command == 'F') {
    lastLeft = 9;
    lastRight = 9;
    servoLeft.writeMicroseconds(1000);
    servoRight.writeMicroseconds(2000);
    Serial.println("Forward");
  } else if (command == 'G') {
    lastLeft = -9;
    lastRight =-9;
    servoLeft.writeMicroseconds(2000);
    servoRight.writeMicroseconds(1000);
    Serial.println("Backwards");
  } else if (command == '>') {
    lastLeft = 9;
    lastRight = -9;
    servoLeft.writeMicroseconds(1000);
    servoRight.writeMicroseconds(1000);
    Serial.println("Right");
  } else if (command == '<') {
    lastLeft = -9;
    lastRight = 9;
    servoLeft.writeMicroseconds(2000);
    servoRight.writeMicroseconds(2000);
    Serial.println("Left");
  } else if (command == 'O') {
    digitalWrite(HEADLIGHTS, HIGH);
    Serial.println("Headlights On");
  } else if (command == 'P') {
    digitalWrite(HEADLIGHTS, LOW);
    Serial.println("Headlights Off");
  }
  if (command == 'B'){
    lastLeft = 0;
    lastRight = 0;
    servoLeft.writeMicroseconds(1500);
    servoRight.writeMicroseconds(1500);
    digitalWrite(BRAKELIGHTS, HIGH);
    Serial.println("Brake");
  } else {
    digitalWrite(BRAKELIGHTS, LOW);
  }
}

//Translaates a string command into a function call
void handleCommand(char command[]){
  //print the command to ensure proper reception
  //printCommand(command);

  //Ignore corrupted commands
  if (strlen(command)>7) {
    Serial.println("Corrupt Command");
    return;
  }

  //Find the power of each motor from the command and set to negative if necessary
  int leftPower = command[1] - '0';
  if (command[0] == '-') {
    leftPower *= -1;
  }
  int rightPower = command[3] - '0';
  if (command[2] == '-') {
    rightPower *= -1;
  }

  //Set the left motor
  int l_speed = getMotorValueLeft(leftPower);
  servoLeft.writeMicroseconds(l_speed);

  //Set the right motor
  int r_speed = getMotorValueRight(rightPower);
  servoRight.writeMicroseconds(r_speed);

  //Set the headlights
  if (command[4] == '1') {
    digitalWrite(HEADLIGHTS, HIGH);
  } else if (command[4] == '0') {
    digitalWrite(HEADLIGHTS, LOW);
  }

  //Set the brake lights
  if (command[5] == '1') {
    digitalWrite(BRAKELIGHTS, HIGH);
  } else if (command[5] == '0') {
    digitalWrite(BRAKELIGHTS, LOW);
  }

  //Set the blinker related variables
  lastLeft = leftPower;
  lastRight = rightPower;
  
  //Used to debug what the speeds are set to
  Serial.print(l_speed);
  Serial.print("\t");
  Serial.print(r_speed);
  Serial.print("\t");
  Serial.print(command[4]);
  Serial.print("\t");
  Serial.print(command[5]);
  Serial.print("\n");
  
}

//Gets a value for the left motor based on a value in the range of -9 to 9
int getMotorValueLeft(int baseValue){
    int scalar = 20;
    if (baseValue == 0){
      return 1500;
    } else {
      return (1500 - (baseValue*scalar));
    }
}

//Gets a value for the right motor based on a value in the range of -9 to 9
int getMotorValueRight(int baseValue){
  int scalar = 20;
  if (baseValue == 0){
    return 1500;
  } else {
    return (1500 + (baseValue*scalar));
  }
}

//Sets the blinker lights based on how the car is turning
void setBlinkers(int left, int right){
  int diff = left-right;
  if (diff > 3) {
    //Turning right hard
    digitalWrite(LEFT_BLINKER, LOW);
    unsigned long timeDiff = millis() - lastRightOn;
    //Turn the blinker on or off based on elapsed time
    if (timeDiff > 500) {
      digitalWrite(RIGHT_BLINKER, HIGH);
      lastRightOn = millis();
    } else if (timeDiff > 250) {
      digitalWrite(RIGHT_BLINKER, LOW);
    }
  } else if (diff < -3) {
    //Turning left hard
    digitalWrite(RIGHT_BLINKER, LOW);
    unsigned long timeDiff = millis() - lastLeftOn;
    //Turn the blinker on or off based on elapsed time
    if (timeDiff > 500) {
      digitalWrite(LEFT_BLINKER, HIGH);
      lastLeftOn = millis();
    } else if (timeDiff > 250) {
      digitalWrite(LEFT_BLINKER, LOW);
    }
  } else {
    //Moving relatively straight
    digitalWrite(LEFT_BLINKER, LOW);
    digitalWrite(RIGHT_BLINKER, LOW);
    lastLeftOn = millis();
    lastRightOn = millis();
  }

  //Backing up
  if (left < 0 && right <0){
    unsigned long timeDiff = millis() - lastBackOn;
    if (timeDiff > 500) {
      digitalWrite(BRAKELIGHTS, HIGH);
      lastBackOn = millis();
    } else if (timeDiff > 250) {
      digitalWrite(BRAKELIGHTS, LOW);
    }
  } else if (left > 0 || right > 0) {
    digitalWrite(BRAKELIGHTS, LOW);
  }
    else {
    lastBackOn = millis();
  }
}

//Prints out the command to the serial monitor (can be changed to a char array print if necessary)
void printCommand(char command[]){
  Serial.println(command);
}
