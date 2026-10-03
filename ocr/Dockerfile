FROM python:3.11-slim-bookworm
WORKDIR /service
ENV PYTHONUNBUFFERED=1 PADDLE_PDX_MODEL_SOURCE=BOS PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True OMP_NUM_THREADS=2
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 libgl1 libglib2.0-0 && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN python -m pip install --no-cache-dir -r requirements.txt
COPY engine.py app.py ./
# Download public model weights at build time. Resume files never leave this service.
RUN python -c "from engine import build_pipeline; build_pipeline()"
EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--no-access-log"]
