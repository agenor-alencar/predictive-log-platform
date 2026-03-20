# Como testar a API Java no Postman

Este guia contém o passo a passo de como configurar as requisições no Postman para testar as rotas da **API Java (Spring Boot)**.

---

## 1. Login e Geração do Token JWT

Como a API é protegida, o primeiro passo é sempre realizar o Login para obter um token de acesso válido.

*   **Método:** `POST`
*   **URL:** `http://localhost:8080/auth/login`
*   **Headers:**
    *   `Content-Type: application/json`
*   **Body (raw -> JSON):**
```json
{
    "username": "admin",
    "password": "admin123"
}
```

**Resultado:** O retorno (response) conterá um campo `"token"`. Copie o valor desse token, pois ele será necessário no `Authorization` das próximas requisições.

---

## 2. Previsão de Erro (`/predict/error`)

Endpoint utilizado para prever a probabilidade de uma determinada requisição HTTP retornar um código de erro (4xx ou 5xx), usando o modelo de Machine Learning.

*   **Método:** `POST`
*   **URL:** `http://localhost:8080/predict/error`
*   **Headers:**
    *   `Content-Type: application/json`
    *   `Authorization`: Selecione o Tipo **"Bearer Token"** e cole o token obtido no Passo 1. (O Postman enviará como `Bearer SEU_TOKEN_AQUI`)
*   **Body (raw -> JSON):**
```json
{
    "method": "GET",
    "hour": 14,
    "historicalAvgResponse": 250.5,
    "dayOfWeek": 3
}
```

---

## 3. Previsão de Tempo de Resposta (`/predict/response-time`)

Endpoint que prevê o tempo esperado de resposta para uma requisição específica.

*   **Método:** `POST`
*   **URL:** `http://localhost:8080/predict/response-time`
*   **Headers:**
    *   `Content-Type: application/json`
    *   `Authorization`: Tipo **"Bearer Token"** com o token obtido no Passo 1.
*   **Body (raw -> JSON):**
```json
{
    "method": "POST",
    "hour": 10,
    "historicalAvgResponse": 120.0,
    "dayOfWeek": 1
}
```

---

## 4. Consulta de Sumário Estatístico (`/stats/summary`)

Retorna as métricas e agregações do sistema sobre os logs (quantidade de acessos, frequências de métodos, média de tempo de resposta).

*   **Método:** `GET`
*   **URL:** `http://localhost:8080/stats/summary`
*   **Headers:**
    *   `Authorization`: Tipo **"Bearer Token"** com o token obtido no Passo 1.
*   **Body:** *(Nenhum)*

---

## 💡 Dica Bônus: Automatizando o Token no Postman

Para não ter que ficar copiando e colando o Token toda vez que ele expirar:

1. Vá na aba **"Tests"** da requisição de **Login** (Passo 1).
2. Cole este código:
    ```javascript
    var jsonData = pm.response.json();
    pm.environment.set("jwt_token", jsonData.token);
    ```
3. Garanta que você tem um Ambiente (Environment) selecionado no canto superior direito do Postman.
4. Agora, nas requisições 2, 3 e 4, vá na aba de Autorização (**Authorization**), escolha o tipo **Bearer Token** e, no campo Token, escreva `{{jwt_token}}`.
5. O Postman passará a usar essa variável dinamicamente todas as vezes que você rodar o login!
