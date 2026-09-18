FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libgl1 \
    libglib2.0-0 \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-download models so everything runs 100% offline
RUN python -c "\
import easyocr; \
easyocr.Reader(['en'], gpu=False, verbose=False); \
print('EasyOCR models cached.') \
"

RUN python -c "\
from deepface import DeepFace; \
import numpy as np; \
dummy = np.zeros((112, 112, 3), dtype=np.uint8); \
DeepFace.represent(img_path=dummy, model_name='ArcFace', enforce_detection=False); \
DeepFace.extract_faces(img_path=dummy, anti_spoofing=True, enforce_detection=False); \
print('DeepFace models cached.') \
"

COPY . .

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
