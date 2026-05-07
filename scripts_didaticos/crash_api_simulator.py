import asyncio
import aiohttp
import time
import json
import random
import os

# Este script foi criado para fins didáticos: demonstrar como um pico irreal 
# de acessos derruba uma API REST síncrona/banco relacional que não possui 
# proteção adequada (sem rate limiting, sem Kafka, pool de conexões baixo).

import requests

# URL da nossa API Java (ajuste se necessário)
API_URL = "http://localhost:8080/predict/error"

# Busca o token JWT via requisição de login:
print("🔄 Buscando token JWT automaticamente...")
resp = requests.post("http://localhost:8080/auth/login", json={"username": "admin", "password": "admin123"})
if resp.status_code == 200:
    TOKEN = resp.json().get("token")
    print("✅ Token recebido com sucesso!")
else:
    print(f"❌ Falha ao obter token: {resp.text}")
    TOKEN = ""

# Quantidade de requisições simultâneas.
# Pode ser sobrescrita por variável de ambiente, ex.: NUM_REQUESTS=1000
NUM_REQUESTS = int(os.getenv("NUM_REQUESTS", "50000"))

headers = {
    "Content-Type": "application/json",
    "Authorization": f"Bearer {TOKEN}"
}

def generate_random_log():
    methods = ["GET", "POST", "PUT", "DELETE"]
    
    return {
        "method": random.choice(methods),
        "hour": random.randint(0, 23),
        "historicalAvgResponse": random.randint(10, 5000),
        "dayOfWeek": random.randint(0, 6)
    }

async def send_request(session, req_id):
    payload = generate_random_log()
    try:
        async with session.post(API_URL, json=payload, headers=headers) as response:
            status = response.status
            # Printa a cada 1000 requisições para vermos o progresso
            if req_id % 1000 == 0:
                print(f"[Req {req_id}] Status: {status}")
            return status
    except Exception as e:
        # Quando a API cair (Timeouts, Connection Refused), os erros aparecerão aqui
        print(f"🔥 [Req {req_id}] FALHA CRÍTICA - API CAIU! Erro: {type(e).__name__}")
        return str(e)

async def main():
    print(f"🚀 Iniciando ataque simulado com {NUM_REQUESTS} requisições simultâneas...")
    start_time = time.time()
    
    # O Connector limite garante que o Python tente abrir o máximo de conexões possíveis de uma vez
    connector = aiohttp.TCPConnector(limit=5000)
    
    async with aiohttp.ClientSession(connector=connector) as session:
        tasks = []
        for i in range(NUM_REQUESTS):
            tasks.append(send_request(session, i))
            
        # Dispara todas de uma vez "concorrentemente" 
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
    end_time = time.time()
    print(f"\n⏱️ Tempo total do ataque: {end_time - start_time:.2f} segundos.")
    print(" Verifique os logs do PostgreSQL (FATAL: sorry, too many clients already).")
    print(" Verifique os logs do Spring Boot (HikariPool / OutOfMemoryError).")

if __name__ == "__main__":
    if not TOKEN:
        print("⚠️ ERRO: Falha ao gerar TOKEN JWT, verifique a API.")
    else:
        asyncio.run(main())
