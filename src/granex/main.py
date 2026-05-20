from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI()

# Esto permite que tu Java en la web se comunique con tu Python local sin bloqueos
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ImageMock(BaseModel):
    image_name: str

@app.get("/dashboard")
def get_dashboard():
    return {
        "clima": "☀️ CLIMA: 24°C, Soleado",
        "mercado": "🌾 SOJA ROSARIO: $280.000 ARS",
        "dolar": "💵 DÓLAR BLUE - Compra: $1200 | Venta: $1220"
    }

@app.post("/predict")
def predict_crop(data: ImageMock):
    return {
        "resultado": "Planta: Soja | Estado: Enferma | Diagnóstico: Roya | Recomendación: Fungicida"
    }