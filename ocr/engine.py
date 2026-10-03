from io import BytesIO
from threading import Lock
import warnings

from PIL import Image, ImageOps, UnidentifiedImageError

MAX_PIXELS = 12_000_000
MAX_BYTES = 20 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = MAX_PIXELS


def build_pipeline():
    from paddleocr import PaddleOCR
    return PaddleOCR(
        ocr_version="PP-OCRv5",
        text_detection_model_name="PP-OCRv5_mobile_det",
        text_recognition_model_name="PP-OCRv5_mobile_rec",
        use_doc_orientation_classify=True,
        use_doc_unwarping=False,
        use_textline_orientation=True,
        device="cpu",
        enable_mkldnn=False,
        cpu_threads=2,
    )


def decode_image(data):
    if not data or len(data) > MAX_BYTES:
        raise ValueError("image_size")
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(data)) as source:
                if source.format not in {"PNG", "JPEG", "WEBP"}:
                    raise ValueError("image_format")
                if source.width * source.height > MAX_PIXELS:
                    raise ValueError("image_pixels")
                source.load()
                return ImageOps.exif_transpose(source).convert("RGB")
    except (UnidentifiedImageError, OSError, Image.DecompressionBombError, Image.DecompressionBombWarning) as exc:
        raise ValueError("invalid_image") from exc


class OcrEngine:
    def __init__(self, pipeline):
        self.pipeline = pipeline
        self.lock = Lock()

    def extract(self, image):
        import numpy as np
        with self.lock:
            results = list(self.pipeline.predict(np.asarray(image), text_rec_score_thresh=0.5))
        lines = []
        for result in results:
            payload = result.json["res"]
            for text, score in zip(payload["rec_texts"], payload["rec_scores"]):
                if float(score) >= 0.5 and text.strip():
                    lines.append(text.strip())
        text = "\n".join(lines)
        if len(text) > 100_000:
            raise ValueError("text_size")
        return {"text": text, "lineCount": len(lines), "engine": "PP-OCRv5_mobile"}
