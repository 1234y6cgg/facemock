from contextlib import asynccontextmanager
from threading import BoundedSemaphore

from fastapi import FastAPI, HTTPException, UploadFile
from engine import MAX_BYTES, OcrEngine, build_pipeline, decode_image

slots = BoundedSemaphore(2)


@asynccontextmanager
async def lifespan(app):
    app.state.engine = OcrEngine(build_pipeline())
    yield


app = FastAPI(title="FaceMock local OCR", lifespan=lifespan, docs_url=None, redoc_url=None)


@app.get("/health")
def health():
    return {"status": "UP", "engine": "PP-OCRv5_mobile", "paddleocrVersion": "3.7.0"}


@app.post("/extract")
def extract(file: UploadFile):
    if not slots.acquire(blocking=False):
        raise HTTPException(503, "OCR_BUSY")
    try:
        data = file.file.read(MAX_BYTES + 1)
        image = decode_image(data)
        try:
            return app.state.engine.extract(image)
        finally:
            image.close()
    except ValueError as exc:
        raise HTTPException(422, "INVALID_IMAGE") from exc
    except Exception as exc:
        # Neither image data nor recognized resume text is written to logs.
        raise HTTPException(503, "OCR_UNAVAILABLE") from exc
    finally:
        file.file.close()
        slots.release()
