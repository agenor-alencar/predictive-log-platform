FROM python:3.12-slim

WORKDIR /app

# psycopg2-binary inclui o driver compilado ? nao precisa de build-essential nem libpq-dev
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --default-timeout=300 --retries=3 -r requirements.txt

COPY . .

RUN mkdir -p /app/models /app/data

EXPOSE 8000

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
