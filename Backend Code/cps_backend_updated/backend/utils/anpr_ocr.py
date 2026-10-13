from ultralytics import YOLO
import cv2
import os
from google.cloud import vision
import io
import re
from google.oauth2 import service_account

credentials = service_account.Credentials.from_service_account_file(
    r"..\cps_project\anpr-ocr-510107-aa017fef6072.json"
)

def detect_and_ocr(image_path, conf_threshold=0.05):
    # image_dir = os.path.join("static", "model")
    image_dir = os.path.join("..\\cps_backend_updated\\backend\\static", "model")
    orc_image_dir = os.path.join(image_dir, "ocr_image")
    os.makedirs(image_dir, exist_ok=True)
    os.makedirs(orc_image_dir, exist_ok=True)
    model_path = os.path.join(image_dir, "best.pt")
    model = YOLO(model_path)
    results = model(image_path, conf=conf_threshold, imgsz=1280, augment=True)
    img = cv2.imread(image_path)
    if img is None:
        print("Error: Could not read image")
        return None
    client = vision.ImageAnnotatorClient(credentials=credentials)
    best_box = None
    best_confidence = 0.0
    for result in results:
        if len(result.boxes) == 0:
            continue
        for box in result.boxes:
            confidence = float(box.conf[0])
            if confidence > best_confidence:
                best_confidence = confidence
                best_box = box
    if best_box is None:
        print("No number plate detected!")
        output_path = os.path.join(image_dir, "ocr_image", "annotated_" + os.path.basename(image_path))
        cv2.imwrite(output_path, img)
        return None

    x1, y1, x2, y2 = map(int, best_box.xyxy[0])

    print(f"Best plate detected | Confidence: {best_confidence:.2f} | Box: ({x1}, {y1}, {x2}, {y2})")

    box_color = (0, 255, 0)
    cv2.rectangle(img,(x1, y1),(x2, y2),box_color,4)

    label = f"Plate {best_confidence:.2f}"
    (text_width, text_height), baseline = cv2.getTextSize(label,cv2.FONT_HERSHEY_SIMPLEX,0.8,2)
    label_y = max(y1, text_height + 10)

    cv2.rectangle(img,(x1, label_y - text_height - 10),(x1 + text_width + 10, label_y + baseline - 5),box_color,-1)

    cv2.putText(img,label,(x1 + 5, label_y - 5),cv2.FONT_HERSHEY_SIMPLEX,0.8,(255, 255, 255),2,cv2.LINE_AA)

    output_path = os.path.join(image_dir, "ocr_image","annotated_" + os.path.basename(image_path))

    cv2.imwrite(output_path, img)

    print(f"Annotated image saved → {output_path}")

    plate_crop = img[y1:y2, x1:x2]

    if plate_crop.size == 0:
        print("Invalid plate crop")
        return None

    crop_path = os.path.join(image_dir, "ocr_image","plate_crop.jpg")

    cv2.imwrite(crop_path, plate_crop)

    print(f"Saved cropped plate → {crop_path}")

    success, encoded_image = cv2.imencode(".jpg",plate_crop)

    if not success:
        print("Failed to encode cropped image")
        return None

    content = encoded_image.tobytes()

    image = vision.Image(content=content)

    response = client.text_detection(image=image)

    if response.error.message:
        print(
            f"Google Vision Error: "
            f"{response.error.message}"
        )
        return None

    texts = response.text_annotations

    if texts:
        full_text = texts[0].description.strip()
        clean_text = " ".join(full_text.split())
        clean_text = re.sub(r'[^A-Z0-9]', '', clean_text.upper())
        if len(clean_text)>10:
            clean_text = clean_text[-10:]
        print(f"OCR Result: {clean_text}")
        return clean_text
    print("No text found by OCR")
    return None

if __name__ == "__main__":
    IMAGE_PATH = r"..\cps_backend_updated\backend\static\model\images\val\video8_1550.jpg"
    if not os.path.exists(IMAGE_PATH):
        print(f"Image not found: {IMAGE_PATH}")
    else:
        results = detect_and_ocr(IMAGE_PATH)
        
        if results:
            print(f"Detected plate texts: {results}")
        else:
            print("No plate texts detected.")