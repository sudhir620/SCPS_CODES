#include <Arduino.h>
#include "esp_camera.h"
#include "esp_http_server.h"
#include <WiFi.h>
#include <PubSubClient.h>
#include <ESP32Servo.h>

#define CAMERA_MODEL_AI_THINKER

#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27

#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5

#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

#define LED_GPIO_NUM       4

#define IR_PIN             13
#define SERVO_PIN          12

#define SERVO_OPEN_ANGLE   90
#define SERVO_CLOSED_ANGLE 0

const unsigned long SERVO_OPEN_TIME = 6000;

const unsigned long MQTT_RECONNECT_INTERVAL = 5000;

const unsigned long OBJECT_CLEAR_TIME = 300;

const char* MQTT_BROKER = "broker.hivemq.com";
const uint16_t MQTT_PORT = 1883;
const char* MQTT_IMAGE_TOPIC = "2026091519991228/ESP32_CAM_EXIT/IMAGE";
const char* MQTT_SERVO_TOPIC = "2026091519991228/ESP32_CAM_EXIT/SERVO";
const char* MQTT_DISPLAY_TOPIC = "2026091519991228/GT_DISPLAY/2";

#define JPEG_QUALITY 12

const char* AP_NAME = "ESP32_CAM_EXIT_SETUP";

httpd_handle_t webServer = NULL;

WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

Servo gateServo;

char wifiSSID[64] = {0};
char wifiPassword[64] = {0};

bool wifiCredentialsReceived = false;
bool wifiConnected = false;

bool servoOpen = false;

unsigned long servoOpenedAt = 0;

bool objectCaptured = false;

unsigned long objectClearStarted = 0;

#define LOG_BUFFER_SIZE 80

String logBuffer[LOG_BUFFER_SIZE];

int logPosition = 0;

void logMessage(const String& message){
    Serial.println(message);
    logBuffer[logPosition] = message;
    logPosition++;
    if (logPosition >= LOG_BUFFER_SIZE){
        logPosition = 0;
    }
}

void logPrintf(const char* format, ...){
    char buffer[200];
    va_list args;
    va_start(args, format);
    vsnprintf(buffer, sizeof(buffer), format, args);
    va_end(args);
    logMessage(String(buffer));
}

static const char BASE64_TABLE[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

String base64Encode(const uint8_t* data, size_t length){
    String output;
    output.reserve(((length + 2) / 3) * 4);
    for (size_t i = 0; i < length; i += 3){
        uint32_t value = ((uint32_t)data[i] << 16);
        if (i + 1 < length){
            value |= ((uint32_t)data[i + 1] << 8);
        }
        if (i + 2 < length){
            value |= data[i + 2];
        }
        output += BASE64_TABLE[(value >> 18) & 0x3F];
        output += BASE64_TABLE[(value >> 12) & 0x3F];
        if (i + 1 < length){
            output += BASE64_TABLE[(value >> 6) & 0x3F];
        }else{
            output += '=';
        }
        if (i + 2 < length){
            output += BASE64_TABLE[value & 0x3F];
        }else{
            output += '=';
        }
    }
    return output;
}



void urlDecode(char* input){
    char* source = input;
    char* destination = input;
    while (*source){
        if (*source == '+'){
            *destination++ = ' ';
            source++;
        }else if (*source == '%' && isxdigit(*(source + 1)) && isxdigit(*(source + 2)))
        {
            char hex[3] ={source[1], source[2], '\0'};
            *destination++ = (char)strtol(hex, NULL, 16);
            source += 3;
        }else{
            *destination++ = *source++;
        }
    }
    *destination = '\0';
}



bool captureJPEG(uint8_t** jpegBuffer, size_t* jpegLength){
    digitalWrite(LED_GPIO_NUM, HIGH);
    delay(150);
    camera_fb_t* frame = esp_camera_fb_get();
    if (!frame){
        digitalWrite(LED_GPIO_NUM, LOW);
        logMessage("ERROR: Camera capture failed");
        return false;
    }
    logPrintf("Camera frame: %u bytes, %dx%d", (unsigned)frame->len, frame->width, frame->height);
    uint8_t* jpeg = NULL;
    size_t jpegSize = 0;
    bool converted = frame2jpg(frame, JPEG_QUALITY, &jpeg, &jpegSize);
    esp_camera_fb_return(frame);
    digitalWrite(LED_GPIO_NUM, LOW);
    if (!converted || jpeg == NULL || jpegSize == 0){
        if (jpeg){
            free(jpeg);
        }
        logMessage("ERROR: JPEG conversion failed");
        return false;
    }
    *jpegBuffer = jpeg;
    *jpegLength = jpegSize;
    logPrintf("JPEG created: %u bytes", (unsigned)jpegSize);
    return true;
}



void publishImage(){
    if (!wifiConnected){
        logMessage("Image skipped: WiFi not connected");
        return;
    }
    if (!mqttClient.connected()){
        logMessage("Image skipped: MQTT not connected");
        return;
    }
    uint8_t* jpeg = NULL;
    size_t jpegLength = 0;
    logMessage("Capturing image...");
    if (!captureJPEG(&jpeg, &jpegLength)){
        logMessage("Image capture failed");
        return;
    }
    logMessage("Converting image to Base64...");
    String base64Image = base64Encode(jpeg, jpegLength);
    free(jpeg);
    logPrintf("Base64 size: %u characters", (unsigned)base64Image.length());
    logMessage("Publishing image to MQTT...");
    bool published = mqttClient.publish(MQTT_IMAGE_TOPIC, base64Image.c_str());
    if (published){
        logMessage("IMAGE PUBLISHED SUCCESSFULLY");
        if (wifiConnected && mqttClient.connected()){
            if (mqttClient.publish(MQTT_DISPLAY_TOPIC, "Image Published, Checking for booking end time...")){
                logMessage("Display message published: Image Published");
            }else{
                logMessage("ERROR: Display MQTT publish failed");
            }
        }
    }else{
        logMessage("ERROR: IMAGE MQTT PUBLISH FAILED");
    }
}

void mqttCallback(char* topic, byte* payload, unsigned int length){
    String message;
    for (unsigned int i = 0; i < length; i++){
        message += (char)payload[i];
    }
    message.trim();
    logPrintf("MQTT [%s] %s", topic, message.c_str());
    if (String(topic) == MQTT_SERVO_TOPIC && message.equalsIgnoreCase("OPEN")){
        logMessage("OPEN command received");
        gateServo.write(SERVO_OPEN_ANGLE);
        servoOpen = true;
        servoOpenedAt = millis();
        logMessage("SERVO OPENED");
        logMessage("Servo will close after 6 seconds");
    }
}

void connectMQTT(){
    static unsigned long lastAttempt = 0;
    if (millis() - lastAttempt < MQTT_RECONNECT_INTERVAL){
        return;
    }
    lastAttempt = millis();
    logMessage("Connecting to MQTT...");
    String clientId ="ESP32CAM-" +String((uint32_t)ESP.getEfuseMac(),HEX);

    if (mqttClient.connect(clientId.c_str())){
        logMessage("MQTT CONNECTED");
        bool subscribed = mqttClient.subscribe(MQTT_SERVO_TOPIC);
        if (subscribed){
            logMessage("Servo topic subscribed");
        }else{
            logMessage("ERROR: Servo topic subscribe failed");
        }
    }else{
        logPrintf("MQTT connection failed, rc=%d", mqttClient.state());
    }
}


const char ROOT_HTML[] PROGMEM =
R"rawliteral(
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>ESP32-CAM Exit</title>
<style>
body {
    font-family: Arial;
    max-width: 500px;
    margin: 30px auto;
    padding: 20px;
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
    margin-top: 10px;
    background: #2196F3;
    color: white;
    border: none;
    font-size: 16px;
}
a {
    display: block;
    padding: 12px;
    margin-top: 10px;
    text-align: center;
    background: #444;
    color: white;
    text-decoration: none;
}
</style>
</head>
<body>
<h2>ESP32-CAM Exit System</h2>
<p>Configure Wi-Fi to connect the ESP32-CAM to your parking network.</p>
<form method="POST" action="/save">
<label>Wi-Fi SSID</label>
<input name="ssid" required>
<label>Wi-Fi Password</label>
<input name="pass" type="password">
<button type="submit">Connect to Wi-Fi</button>
</form>
<a href="/logs">View Serial Logs</a>
<a href="/snapshot">View Camera Snapshot</a>
</body>
</html>
)rawliteral";


esp_err_t rootHandler(httpd_req_t* request){
    httpd_resp_set_type(request, "text/html");
    httpd_resp_send(request, ROOT_HTML, HTTPD_RESP_USE_STRLEN);
    return ESP_OK;
}



esp_err_t saveHandler(httpd_req_t* request){
    char body[512] = {0};
    int length = httpd_req_recv(request, body, sizeof(body) - 1);
    if (length <= 0){
        httpd_resp_send_500(request);
        return ESP_FAIL;
    }
    logMessage("WiFi configuration received");
    char* ssid = strstr(body, "ssid=");
    char* password = strstr(body, "pass=");
    if (!ssid){
        logMessage("ERROR: SSID missing");
        httpd_resp_send_500(request);
        return ESP_FAIL;
    }
    ssid += 5;
    char* separator = strchr(ssid, '&');
    if (separator){
        *separator = '\0';
    }
    if (password){
        password += 5;
        separator = strchr(password, '&');
        if (separator){
            *separator = '\0';
        }
    }
    strncpy(wifiSSID, ssid, sizeof(wifiSSID) - 1);
    wifiSSID[sizeof(wifiSSID) - 1] = '\0';
    urlDecode(wifiSSID);
    if (password){
        strncpy(wifiPassword, password, sizeof(wifiPassword) - 1);
        wifiPassword[sizeof(wifiPassword) - 1] = '\0';
        urlDecode(wifiPassword);
    }else{
        wifiPassword[0] = '\0';
    }
    logPrintf("SSID: %s", wifiSSID);
    wifiCredentialsReceived = true;
    wifiConnected = false;
    const char response[] =
        "<html>"
        "<body>"
        "<h2>Wi-Fi credentials received</h2>"
        "<p>ESP32-CAM is connecting...</p>"
        "<p>You can check the Serial Monitor.</p>"
        "</body>"
        "</html>";
    httpd_resp_set_type(request, "text/html");
    httpd_resp_send(request, response, HTTPD_RESP_USE_STRLEN);
    return ESP_OK;
}


esp_err_t snapshotHandler(httpd_req_t* request){
    uint8_t* jpeg = NULL;
    size_t jpegLength = 0;
    if (!captureJPEG(&jpeg, &jpegLength)){
        httpd_resp_send_500(request);
        return ESP_FAIL;
    }
    httpd_resp_set_type(request, "image/jpeg");
    httpd_resp_set_hdr(request, "Cache-Control", "no-cache, no-store, must-revalidate");
    esp_err_t result = httpd_resp_send(request, (const char*)jpeg, jpegLength);
    free(jpeg);
    return result;
}


String createLogPage(){
    String html;
    html.reserve(15000);
    html += 
        "<!DOCTYPE html>"
        "<html>"
        "<head>"
        "<meta name='viewport' "
        "content='width=device-width,initial-scale=1'>"
        "<meta http-equiv='refresh' content='2'>"
        "<title>ESP32-CAM Logs</title>"
        "<style>"
        "body{"
        "background:#111;"
        "color:#00ff66;"
        "font-family:monospace;"
        "padding:10px;"
        "}"
        "h2{"
        "color:white;"
        "}"
        ".status{"
        "color:#aaa;"
        "margin-bottom:15px;"
        "}"
        ".logs{"
        "background:#000;"
        "padding:15px;"
        "border-radius:8px;"
        "white-space:pre-wrap;"
        "word-wrap:break-word;"
        "}"
        "a{"
        "display:inline-block;"
        "background:#333;"
        "color:white;"
        "padding:10px;"
        "margin:4px;"
        "text-decoration:none;"
        "}"
        "</style>"
        "</head>"
        "<body>"
        "<h2>ESP32-CAM Serial Logs</h2>"
        "<div class='status'>"
        "Auto refresh: 2 seconds"
        "</div>"
        "<a href='/'>Home</a>"
        "<a href='/snapshot'>Snapshot</a>"
        "<div class='logs'>";
    for (int i = 0; i < LOG_BUFFER_SIZE; i++){
        int index = (logPosition + i) % LOG_BUFFER_SIZE;
        if (logBuffer[index].length() > 0){
            html += logBuffer[index];
            html += "\n";
        }
    }
    html +=
        "</div>"
        "</body>"
        "</html>";
    return html;
}


esp_err_t logsHandler(httpd_req_t* request){
    String html = createLogPage();
    httpd_resp_set_type(request, "text/html");
    httpd_resp_set_hdr(request, "Cache-Control", "no-cache");
    httpd_resp_send(request, html.c_str(), html.length());
    return ESP_OK;
}


bool startWebServer(){
    httpd_config_t config = HTTPD_DEFAULT_CONFIG();
    config.server_port = 80;
    config.max_uri_handlers = 10;
    config.stack_size = 8192;
    if (httpd_start(&webServer, &config) != ESP_OK){
        logMessage("ERROR: Web server failed");
        return false;
    }
    httpd_uri_t root = {.uri = "/", .method = HTTP_GET, .handler = rootHandler, .user_ctx = NULL};
    httpd_uri_t save = {.uri = "/save", .method = HTTP_POST, .handler = saveHandler, .user_ctx = NULL};
    httpd_uri_t snapshot = {.uri = "/snapshot", .method = HTTP_GET, .handler = snapshotHandler, .user_ctx = NULL};
    httpd_uri_t logs = {.uri = "/logs", .method = HTTP_GET, .handler = logsHandler, .user_ctx = NULL};
    httpd_register_uri_handler(webServer, &root);
    httpd_register_uri_handler(webServer, &save);
    httpd_register_uri_handler(webServer, &snapshot);
    httpd_register_uri_handler(webServer, &logs);
    logMessage("Web server started");
    return true;
}

bool initializeCamera(){
    camera_config_t config = {};
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;
    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;
    config.pin_sccb_sda = SIOD_GPIO_NUM;
    config.pin_sccb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;
    config.xclk_freq_hz = 20000000;
    config.pixel_format = PIXFORMAT_RGB565;
    config.frame_size = FRAMESIZE_QVGA;
    config.jpeg_quality = JPEG_QUALITY;
    config.fb_count = 1; 
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
    config.fb_location = CAMERA_FB_IN_PSRAM;

    if (psramFound()){
        logMessage("PSRAM detected");
        config.fb_count = 2;
        config.grab_mode = CAMERA_GRAB_LATEST;
    }else{
        logMessage("WARNING: PSRAM not detected");
        config.frame_size = FRAMESIZE_QQVGA;
        config.fb_location = CAMERA_FB_IN_DRAM;
    }
    esp_err_t error = esp_camera_init(&config);
    if (error != ESP_OK){
        logPrintf("Camera init failed: 0x%x", error);
        return false;
    }
    sensor_t* sensor = esp_camera_sensor_get();
    if (sensor){
        sensor->set_framesize(sensor, FRAMESIZE_QVGA);
    }
    pinMode(LED_GPIO_NUM, OUTPUT);
    digitalWrite(LED_GPIO_NUM, LOW);
    logMessage("Camera initialized");
    return true;
}

bool connectToWiFi(){
    logPrintf("Connecting to WiFi: %s", wifiSSID);
    WiFi.begin(wifiSSID, wifiPassword);
    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 25000){
        delay(500);
        Serial.print(".");
    }
    Serial.println();
    if (WiFi.status() != WL_CONNECTED){
        logMessage("WiFi connection FAILED");
        return false;
    }
    wifiConnected = true;
    logMessage("================================");
    logMessage("WiFi connected successfully");
    logPrintf("ESP32-CAM IP: %s", WiFi.localIP().toString().c_str());
    logMessage("================================");
    logMessage("Closing WiFi provisioning SoftAP...");
    WiFi.softAPdisconnect(true);
    delay(200);
    WiFi.mode(WIFI_STA);
    logMessage("Provisioning SoftAP CLOSED");
    logPrintf("Web logs: http://%s/logs", WiFi.localIP().toString().c_str());
    return true;
}


void setup(){
    Serial.begin(115200);
    Serial.setDebugOutput(true);
    delay(1000);
    Serial.println();
    Serial.println("================================");
    Serial.println("       ESP32-CAM EXIT");
    Serial.println("================================");
    if (!initializeCamera()){
        logMessage("Camera initialization failed");
        while (true){
            delay(1000);
        }
    }
    pinMode(IR_PIN, INPUT_PULLUP);
    logMessage("IR sensor initialized");
    gateServo.attach(SERVO_PIN);
    gateServo.write(SERVO_CLOSED_ANGLE);
    servoOpen = false;
    logMessage("Servo initialized - CLOSED");
    WiFi.mode(WIFI_AP_STA);
    bool apStarted = WiFi.softAP(AP_NAME);
    if (apStarted){
        logMessage("WiFi provisioning AP started");
        logPrintf("AP SSID: %s", AP_NAME);
        logPrintf("AP IP: %s", WiFi.softAPIP().toString().c_str());
    }else{
        logMessage("ERROR: SoftAP failed");
    }
    startWebServer();
    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    mqttClient.setCallback(mqttCallback);
    mqttClient.setBufferSize(8192);
    logMessage("MQTT initialized");
    logMessage("================================");
    logMessage("Waiting for WiFi configuration");
    logMessage("Open http://192.168.4.1");
    logMessage("================================");
}


void loop()
{
    if (wifiCredentialsReceived && !wifiConnected){
        if (connectToWiFi()){
            wifiCredentialsReceived = false;
        }else{
            delay(3000);
        }
    }
    if (wifiConnected){
        if (!mqttClient.connected()){
            connectMQTT();
        }else{
            mqttClient.loop();
        }
    }
    bool objectDetected =(digitalRead(IR_PIN) == LOW);
    if (objectDetected){
        objectClearStarted = 0;
        if (!objectCaptured){
            if (objectDetectedAt == 0){
                objectDetectedAt = millis();

                logMessage("--------------------------------");
                logMessage("OBJECT DETECTED");
                if (wifiConnected && mqttClient.connected()){
                    if (mqttClient.publish(MQTT_DISPLAY_TOPIC, "Vehicle Detected, Please Wait...")){
                        logMessage("Display message published");
                    }else{
                        logMessage("ERROR: Display MQTT publish failed");
                    }
                }else{
                    logMessage("Display message skipped: MQTT not connected");
                }

                logMessage("Waiting 2 seconds before image capture...");
            }
            if (millis() - objectDetectedAt >= IMAGE_CAPTURE_DELAY){
                objectCaptured = true;

                logMessage("2 seconds completed");
                logMessage("Capturing image now...");
                publishImage();

                logMessage("IMAGE CAPTURE COMPLETE");
                logMessage("Object is still present - no additional captures");
                logMessage("--------------------------------");
                objectDetectedAt = 0;
            }
        }
    }
    else{
        if (!objectCaptured && objectDetectedAt != 0){

            objectDetectedAt = 0;

            logMessage("OBJECT LEFT BEFORE 2 SECONDS");
            logMessage("Image capture cancelled");
            logMessage("System ARMED for next object");
        }
        if (objectCaptured){
            if (objectClearStarted == 0){
                objectClearStarted = millis();
            }
            if (millis() - objectClearStarted >= OBJECT_CLEAR_TIME){

                objectCaptured = false;
                objectClearStarted = 0;
                objectDetectedAt = 0;

                logMessage("OBJECT LEFT");
                logMessage("System ARMED for next object");
            }
        }
    }
    if (servoOpen){
        if (millis() - servoOpenedAt >= SERVO_OPEN_TIME){
            gateServo.write(SERVO_CLOSED_ANGLE);
            servoOpen = false;
            logMessage("6 seconds completed");
            logMessage("SERVO CLOSED");
        }
    }
    delay(20);
}