
import os
import tempfile
from pathlib import Path
from datetime import datetime

from dotenv import load_dotenv
from fastapi import FastAPI, UploadFile, File, HTTPException
from openai import AsyncOpenAI

load_dotenv(
    dotenv_path=Path(__file__).resolve().parent / ".env"
)

app = FastAPI(title="RoadTrip Backend")


@app.get("/")
def home():
    return {
        "app": "RoadTrip",
        "status": "running",
        "message": "Backend server is ready"
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "timestamp": datetime.now().isoformat()
    }


@app.post("/stt")
async def speech_to_text(audio: UploadFile = File(...)):
    api_key = os.getenv("OPENAI_API_KEY")

    if not api_key:
        raise HTTPException(
            status_code=500,
            detail="OPENAI_API_KEY is not configured"
        )

    audio_bytes = await audio.read()

    if not audio_bytes:
        raise HTTPException(
            status_code=400,
            detail="Audio file is empty"
        )

    if len(audio_bytes) > 10 * 1024 * 1024:
        raise HTTPException(
            status_code=413,
            detail="Audio file is too large"
        )

    client = AsyncOpenAI(api_key=api_key)

    try:
        result = await client.audio.transcriptions.create(
            model="gpt-4o-mini-transcribe",
            file=("recording.wav", audio_bytes, "audio/wav"),
            language="ko"
        )

        return {
            "success": True,
            "text": result.text
        }

    
    except Exception as e:
        print(
            f"[STT ERROR] {type(e).__name__}: {e}",
            flush=True
        )

        raise HTTPException(
            status_code=502,
            detail="Speech recognition failed"
        )

