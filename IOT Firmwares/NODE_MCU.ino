#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <PubSubClient.h>
#include <U8g2lib.h>

#define NUM_IR_SENSORS 4

const int IR_SENSOR_PINS[NUM_IR_SENSORS] = {
  D0,
  D7,
  D3,
  D4
};

#define IR_DETECTED LOW

bool previousOccupied[NUM_IR_SENSORS] = {
  false,
  false,
  false,
  false
};

U8G2_SSD1306_128X64_NONAME_F_HW_I2C display1(
  U8G2_R0,
  U8X8_PIN_NONE,
  D1,
  D2
);

U8G2_SSD1306_128X64_NONAME_F_SW_I2C display2(
  U8G2_R0,
  D6,
  D5,
  U8X8_PIN_NONE
);

#define DISPLAY_MSG_HOLD_MS 6000

unsigned long display1MsgTime = 0;
unsigned long display2MsgTime = 0;

bool display1ShowingMsg = false;
bool display2ShowingMsg = false;

ESP8266WebServer server(80);

bool configMode = true;

String wifiSSID = "";
String wifiPassword = "";

const char* MQTT_SERVER =
  "broker.hivemq.com";

const int MQTT_PORT = 1883;

String mqttClientId;

const char* MQTT_TOPIC_IR[NUM_IR_SENSORS] = {

  "2026091519991228/UPDATE_PARKING_SLOT/1",

  "2026091519991228/UPDATE_PARKING_SLOT/2",

  "2026091519991228/UPDATE_PARKING_SLOT/3",

  "2026091519991228/UPDATE_PARKING_SLOT/4"
};

const char* MQTT_TOPIC_OLED1 =
  "2026091519991228/GT_DISPLAY/1";

const char* MQTT_TOPIC_OLED2 =
  "2026091519991228/GT_DISPLAY/2";

WiFiClient espClient;

PubSubClient mqttClient(espClient);

String configPage();

void handleRoot();

void handleSave();

void startSoftAP();

void connectToWiFi();

void connectMQTT();

void mqttCallback(
  char* topic,
  byte* payload,
  unsigned int length
);

void publishIRStatus(
  int index
);

void checkIRSensors();

void showMessage(
  U8G2 &disp,
  const String &message
);

void showWelcome(
  U8G2 &disp
);

void updateDisplayTimeouts();

String configPage() {

  String html = R"rawliteral(

<!DOCTYPE html>

<html>

<head>

<meta name="viewport"
      content="width=device-width, initial-scale=1">

<title>Smart Parking Setup</title>

<style>

body {
    font-family: Arial;
    padding: 20px;
    background: #f5f5f5;
}

.container {
    max-width: 400px;
    margin: auto;
    background: white;
    padding: 20px;
    border-radius: 10px;
}

input {
    width: 100%;
    padding: 12px;
    margin: 8px 0;
    box-sizing: border-box;
}

button {
    width: 100%;
    padding: 12px;
    background: #007bff;
    color: white;
    border: none;
    border-radius: 5px;
}

</style>

</head>

<body>

<div class="container">

<h2>NODE MCU 8266 SETUP</h2>

<form action="/save" method="POST">

<label>Wi-Fi SSID</label>

<input
    type="text"
    name="ssid"
    placeholder="Enter Wi-Fi SSID"
    required
>

<label>Wi-Fi Password</label>

<input
    type="password"
    name="password"
    placeholder="Enter Wi-Fi Password"
>

<button type="submit">
Save & Connect
</button>

</form>

</div>

</body>

</html>

)rawliteral";

  return html;
}

void handleRoot() {

  server.send(
    200,
    "text/html",
    configPage()
  );
}

void handleSave() {

  if (!server.hasArg("ssid")) {

    server.send(
      400,
      "text/plain",
      "SSID is required"
    );

    return;
  }


  wifiSSID =
    server.arg("ssid");

  wifiPassword =
    server.arg("password");


  Serial.println();

  Serial.println(
    "Received Wi-Fi configuration"
  );

  Serial.print("SSID: ");

  Serial.println(
    wifiSSID
  );


  server.send(
    200,
    "text/html",
    "<h2>Credentials received.</h2>"
    "<p>ESP8266 is connecting to Wi-Fi...</p>"
    "<p>Please wait...</p>"
  );


  delay(1000);

  Serial.println(
    "Stopping configuration server..."
  );

  server.stop();


  WiFi.softAPdisconnect(true);
  configMode = false;

  connectToWiFi();
}

void startSoftAP() {

  Serial.println();

  Serial.println(
    "Starting SoftAP (open network)..."
  );

  configMode = true;

  WiFi.mode(WIFI_AP_STA);

  WiFi.softAP(
    "NODE_MCU_8266_SETUP"
  );


  IPAddress IP =
    WiFi.softAPIP();


  Serial.print(
    "AP IP: "
  );

  Serial.println(
    IP
  );


  Serial.println(
    "Connect to this network and open "
    "http://192.168.4.1"
  );


  server.on(
    "/",
    HTTP_GET,
    handleRoot
  );


  server.on(
    "/save",
    HTTP_POST,
    handleSave
  );


  server.begin();


  Serial.println(
    "Configuration server started"
  );
}

void connectToWiFi() {

  Serial.println();

  Serial.println(
    "Connecting to Wi-Fi..."
  );


  WiFi.mode(WIFI_STA);


  WiFi.begin(
    wifiSSID.c_str(),
    wifiPassword.c_str()
  );


  int attempts = 0;


  while (
    WiFi.status() != WL_CONNECTED &&
    attempts < 30
  ) {

    delay(500);

    Serial.print(".");

    attempts++;
  }


  Serial.println();


  if (
    WiFi.status() == WL_CONNECTED
  ) {

    Serial.println(
      "Wi-Fi connected!"
    );


    Serial.print(
      "IP address: "
    );

    Serial.println(
      WiFi.localIP()
    );

    mqttClientId =
      "NODEMCU_PARKING_" +
      String(
        ESP.getChipId(),
        HEX
      );


    Serial.print(
      "MQTT Client ID: "
    );

    Serial.println(
      mqttClientId
    );

    mqttClient.setServer(
      MQTT_SERVER,
      MQTT_PORT
    );


    mqttClient.setCallback(
      mqttCallback
    );


    mqttClient.setBufferSize(
      512
    );


    mqttClient.setKeepAlive(
      60
    );

    connectMQTT();

  }

  else {

    Serial.println();

    Serial.println(
      "Wi-Fi connection failed."
    );

    Serial.println(
      "Starting SoftAP again..."
    );


    startSoftAP();
  }
}

void connectMQTT() {

  while (
    !mqttClient.connected()
  ) {

    Serial.println();

    Serial.println(
      "Connecting to MQTT..."
    );


    Serial.print(
      "Client ID: "
    );

    Serial.println(
      mqttClientId
    );


    bool result =
      mqttClient.connect(
        mqttClientId.c_str()
      );


    if (result) {

      Serial.println(
        "MQTT connected!"
      );

      bool sub1 =
        mqttClient.subscribe(
          MQTT_TOPIC_OLED1
        );

      Serial.print(
        "OLED1 subscribe: "
      );

      Serial.println(
        sub1
          ? "SUCCESS"
          : "FAILED"
      );

      bool sub2 =
        mqttClient.subscribe(
          MQTT_TOPIC_OLED2
        );

      Serial.print(
        "OLED2 subscribe: "
      );

      Serial.println(
        sub2
          ? "SUCCESS"
          : "FAILED"
      );

      delay(100);

      Serial.println();

      Serial.println(
        "Publishing initial IR states..."
      );

      for (
        int i = 0;
        i < NUM_IR_SENSORS;
        i++
      ) {

        publishIRStatus(i);

        delay(100);
      }

      Serial.println();

      Serial.println(
        "Initial IR states published."
      );
    }
    else {
      Serial.print(
        "MQTT failed, state="
      );

      Serial.println(
        mqttClient.state()
      );

      delay(3000);
    }
  }
}

void mqttCallback(
  char* topic,
  byte* payload,
  unsigned int length
) {
  Serial.println();

  Serial.println(
    "===== MQTT CALLBACK START ====="
  );

  Serial.print(
    "Topic: "
  );

  Serial.println(
    topic
  );

  Serial.print(
    "Payload length: "
  );

  Serial.println(
    length
  );

  if (length > 200) {
    Serial.println(
      "Payload too large. Ignored."
    );
    return;
  }

  String message;

  message.reserve(
    length + 1
  );

  for (
    unsigned int i = 0;
    i < length;
    i++
  ) {
    message +=
      (char)payload[i];
  }

  Serial.print(
    "Message: "
  );

  Serial.println(
    message
  );

  if (
    strcmp(
      topic,
      MQTT_TOPIC_OLED1
    ) == 0
  ) {
    Serial.println(
      "Updating OLED1..."
    );

    showMessage(
      display1,
      message
    );

    display1MsgTime =
      millis();
    display1ShowingMsg =
      true;
  }

  else if (
    strcmp(
      topic,
      MQTT_TOPIC_OLED2
    ) == 0
  ) {
    Serial.println(
      "Updating OLED2..."
    );

    showMessage(
      display2,
      message
    );

    display2MsgTime =
      millis();

    display2ShowingMsg =
      true;
  }

  Serial.println(
    "===== MQTT CALLBACK END ====="
  );
}
void showMessage(
  U8G2 &disp,
  const String &message
) {
  disp.clearBuffer();

  disp.setFont(
    u8g2_font_ncenB08_tr
  );

  String lines[4];

  int lineIndex = 0;

  String currentLine = "";
  for (
    int i = 0;
    i < message.length();
    i++
  ) {
    char c =
      message[i];

    if (c == ' ') {
      String testLine =
        currentLine + " ";

      if (
        disp.getStrWidth(
          testLine.c_str()
        ) > 128
      ) {
        if (
          lineIndex < 4
        ) {
          lines[lineIndex++] =
            currentLine;
          currentLine = "";
        }
      }
      else {
        currentLine += " ";
      }
    }
    else {
      currentLine += c;

      if (
        disp.getStrWidth(
          currentLine.c_str()
        ) > 128
      ) {

        currentLine.remove(
          currentLine.length() - 1
        );

        if (
          lineIndex < 4
        ) {

          lines[lineIndex++] =
            currentLine;
        }

        currentLine =
          String(c);
      }
    }

    if (
      lineIndex >= 4
    ) {

      break;
    }
  }

  if (
    currentLine.length() > 0 &&
    lineIndex < 4
  ) {

    lines[lineIndex++] =
      currentLine;
  }

  disp.drawStr(
    0,
    12,
    lines[0].c_str()
  );

  disp.drawStr(
    0,
    27,
    lines[1].c_str()
  );

  disp.drawStr(
    0,
    42,
    lines[2].c_str()
  );

  disp.drawStr(
    0,
    57,
    lines[3].c_str()
  );

  disp.sendBuffer();
}

void showWelcome(
  U8G2 &disp
) {
  disp.clearBuffer();

  disp.setFont(
    u8g2_font_ncenB08_tr
  );

  disp.drawStr(
    0,
    35,
    "Your Welcome"
  );

  disp.sendBuffer();
}

void updateDisplayTimeouts() {
  if (
    display1ShowingMsg &&
    millis() - display1MsgTime >=
      DISPLAY_MSG_HOLD_MS
  ) {

    showWelcome(
      display1
    );

    display1ShowingMsg =
      false;
  }

  if (
    display2ShowingMsg &&
    millis() - display2MsgTime >=
      DISPLAY_MSG_HOLD_MS
  ) {

    showWelcome(
      display2
    );

    display2ShowingMsg =
      false;
  }
}

void publishIRStatus(
  int index
) {
  if (
    index < 0 ||
    index >= NUM_IR_SENSORS
  ) {

    return;
  }

  int sensorValue =
    digitalRead(
      IR_SENSOR_PINS[index]
    );

  bool occupied =
    (
      sensorValue ==
      IR_DETECTED
    );

  const char* message =
    occupied
      ? "occupied"
      : "available";

  Serial.print(
    "IR"
  );

  Serial.print(
    index + 1
  );

  Serial.print(
    " Status: "
  );

  Serial.println(
    message
  );

  Serial.print(
    "Publishing to: "
  );

  Serial.println(
    MQTT_TOPIC_IR[index]
  );

  if (
    !mqttClient.connected()
  ) {

    Serial.println(
      "MQTT not connected."
    );

    return;
  }

  bool result =
    mqttClient.publish(
      MQTT_TOPIC_IR[index],
      message,
      true
    );

  Serial.print(
    "Publish result: "
  );

  Serial.println(
    result
      ? "SUCCESS"
      : "FAILED"
  );

  if (!result) {

    Serial.print(
      "MQTT state: "
    );

    Serial.println(
      mqttClient.state()
    );
  }

  previousOccupied[index] =
    occupied;
}

void checkIRSensors() {
  for (
    int i = 0;
    i < NUM_IR_SENSORS;
    i++
  ) {
    int sensorValue =
      digitalRead(
        IR_SENSOR_PINS[i]
      );

    bool occupied =
      (
        sensorValue ==
        IR_DETECTED
      );

    if (
      occupied !=
      previousOccupied[i]
    ) {

      Serial.println();

      Serial.print(
        "IR"
      );

      Serial.print(
        i + 1
      );

      Serial.println(
        " state changed"
      );

      const char* message =
        occupied
          ? "occupied"
          : "available";

      Serial.print(
        "Slot "
      );

      Serial.println(
        occupied
          ? "OCCUPIED"
          : "AVAILABLE"
      );

      if (
        mqttClient.connected()
      ) {

        bool result =
          mqttClient.publish(
            MQTT_TOPIC_IR[i],
            message,
            true
          );

        Serial.print(
          "Publish result: "
        );

        Serial.println(
          result
            ? "SUCCESS"
            : "FAILED"
        );
      }

      previousOccupied[i] =
        occupied;
    }
  }
}

void setup() {

  Serial.begin(
    115200
  );


  delay(500);


  Serial.println();

  Serial.println(
    "================================"
  );

  Serial.println(
    " SMART PARKING NODEMCU ESP8266 "
  );

  Serial.println(
    "================================"
  );

  for (
    int i = 0;
    i < NUM_IR_SENSORS;
    i++
  ) {

    pinMode(
      IR_SENSOR_PINS[i],
      INPUT
    );
  }

  display1.begin();

  showWelcome(
    display1
  );

  display2.begin();

  showWelcome(
    display2
  );

  mqttClientId =
    "NODEMCU_PARKING_" +
    String(
      ESP.getChipId(),
      HEX
    );

  Serial.print(
    "MQTT Client ID: "
  );

  Serial.println(
    mqttClientId
  );

  startSoftAP();
}

void loop() {

  if (configMode) {

    server.handleClient();

  }

  if (
    !configMode &&
    WiFi.status() == WL_CONNECTED
  ) {

    if (
      !mqttClient.connected()
    ) {

      Serial.println();

      Serial.println(
        "MQTT connection lost."
      );

      connectMQTT();
    }

    if (
      mqttClient.connected()
    ) {

      Serial.println(
        "Before mqttClient.loop()"
      );

      mqttClient.loop();

      Serial.println(
        "After mqttClient.loop()"
      );
    }

    Serial.println(
      "Before checkIRSensors()"
    );

    checkIRSensors();


    Serial.println(
      "After checkIRSensors()"
    );
  }

  updateDisplayTimeouts();

  delay(100);
}