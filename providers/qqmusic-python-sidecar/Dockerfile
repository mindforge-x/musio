FROM python:3.11-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app

ENV MUSIO_QQMUSIC_HOST=0.0.0.0
ENV MUSIO_QQMUSIC_PORT=18767

CMD ["python", "-m", "app.main"]
