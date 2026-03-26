# Guia de Deploy - OCI (Oracle Cloud Infrastructure)

Este guia descreve como conectar, preparar e rodar o projeto **Predictive Log Platform** em uma instância Ubuntu 20.04 na Oracle Cloud.

## 1. Conexão SSH

Para conectar ao servidor, você precisará da chave privada (.key ou .pem) gerada durante a criação da instância.

**Comando:**
```bash
ssh -i /caminho/para/sua/chave_privada.key ubuntu@152.70.220.118
```

> [!TIP]
> No Windows, se você usar o PowerShell, certifique-se de que as permissões da chave estão restritas (apenas seu usuário com leitura).

---

## 2. Preparação do Servidor (Instalação do Docker)

Assim que entrar no servidor (usuário `ubuntu`), execute os comandos abaixo para instalar o Docker e Docker Compose:

```bash
# Atualizar repositórios
sudo apt update && sudo apt upgrade -y

# Instalar Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Adicionar seu usuário ao grupo docker (para não precisar de sudo sempre)
sudo usermod -aG docker ubuntu
# (Será necessário deslogar e logar novamente para aplicar isso)

# Instalar Docker Compose Plugin
sudo apt install -y docker-compose-plugin
```

---

## 3. Enviando o Projeto para o Servidor

Você tem duas opções principais:

### Opção A: Via Git (Recomendado)
Se o seu projeto estiver em um repositório (GitHub/GitLab):
```bash
git clone https://github.com/mfsjunior/predictive-log-platform.git
cd predictive-log-platform
```

### Opção B: Via SCP (Cópia direta)
Execute este comando **na sua máquina local** (onde está o código):
```bash
scp -i ./sua-chave.key -r c:/projetos/predictive-log-platform ubuntu@152.70.220.118:~/
```

---

## 4. Rodando a Plataforma

Dentro da pasta do projeto no servidor:
```bash
docker compose up -d
```

---

## 5. Configuração de Rede (IMPORTANTE)

A Oracle Cloud bloqueia quase todas as portas por padrão. Você precisará abrir as portas no **Console da OCI** e no **firewall interno** do Ubuntu.

### No Console OCI (VCN Ingress Rules):
Vá em: *Networking -> Virtual Cloud Networks -> vcn-... -> Security Lists -> Default Security List* e adicione **Ingress Rules**:
- Portas **8080** (Java API)
- Portas **8000** (Python ML)
- Portas **3000** (Grafana)
- Portas **5000** (MLflow)
- Portas **9090** (Prometheus)

### No Firewall do Ubuntu (iptables):
As imagens Ubuntu da OCI vêm com regras rígidas de `iptables`. Rode estes comandos para liberar o tráfego Docker:
```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8080 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8000 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 3000 -j ACCEPT
sudo netfilter-persistent save
```

---

## 6. Verificação
Após o deploy, acesse no navegador:
- **API:** `http://152.70.220.118:8080/actuator/health`
- **Grafana:** `http://152.70.220.118:3000` (admin/admin)
