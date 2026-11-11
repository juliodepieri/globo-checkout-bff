# Globo Checkout BFF

Repositório: https://github.com/juliodepieri/globo-checkout-bff

## 1. Descrição

Backend for Frontend (BFF) de um checkout de assinatura. Expõe duas operações para o frontend:

- **Pré-checkout**: monta o carrinho com os planos escolhidos e devolve os meios de pagamento disponíveis.
- **Checkout**: paga o carrinho e devolve os dados da tela de sucesso ou um erro pronto para exibir.

**Stack:** Java 25 · Spring Boot 4 · Gradle · PostgreSQL + Flyway · RabbitMQ · Redis · JUnit 5 + Mockito +
Testcontainers · Docker Compose · OpenAPI (Swagger UI).

### Endpoints

| Método | Caminho | Finalidade |
|---|---|---|
| `POST` | `/operations/pre-checkout` | `{"products":[{"slug":"premium"}]}` → `cartId`, produtos (com preço), meios de pagamento |
| `POST` | `/operations/checkout` | `{"cartId":"…","paymentMethod":{"name":"Cartão de Crédito","card":{…}}}` → `status: "APROVADO"`, pagamento, produtos |
| `GET` | `/swagger-ui.html` | Documentação interativa com exemplos prontos de cada caso de sucesso e erro |
| `GET` | `/v3/api-docs` | Contrato OpenAPI (JSON) |
| `GET` | `/actuator/health` | Health check |

Planos: `basico` (R$ 19,90), `premium` (R$ 39,90), `familia` (R$ 59,90).
Meios de pagamento: `Cartão de Crédito`, `Cartão de Débito` (ambos exigem `card`), `Pix` (sem `card`).

### Regras de negócio

| Regra | Como é garantida | Testes |
|---|---|---|
| Checkout só para um `cartId` criado no pré-checkout | Carrinho buscado no banco; se não existe → `404 CART_NOT_FOUND` | `CheckoutServiceTest`, `CheckoutFlowIntegrationTest` |
| Meio de pagamento precisa ser um dos devolvidos no pré-checkout | O carrinho guarda os meios oferecidos; outro → `422 PAYMENT_METHOD_NOT_ALLOWED` | `CartTest`, `CheckoutServiceTest`, `CheckoutFlowIntegrationTest` |
| Produto inexistente devolve erro no padrão `frontendError` | `422 PRODUCT_NOT_FOUND`, sem criar carrinho | `PreCheckoutServiceTest`, `OperationsControllerTest`, `CheckoutFlowIntegrationTest` |
| Cartão terminado em `0002` é recusado | Gateway simulado → `422 PAYMENT_DECLINED`; o carrinho pode ser pago de novo | `SimulatedPaymentGatewayTest`, `CheckoutServiceTest`, `CheckoutFlowIntegrationTest` |
| Carrinho aprovado não pode ser pago de novo | Trava do carrinho (`SELECT ... FOR UPDATE`) + índice único no banco → `409 CART_ALREADY_PAID` | `CheckoutServiceTest`, `CheckoutFlowIntegrationTest` (inclusive 5 checkouts simultâneos) |
| Número completo e CVV nunca são gravados; no sucesso só os últimos dígitos | Não existe coluna para eles, não vão para eventos nem logs; a resposta traz só `lastDigits` | `CartTest`, `CheckoutFlowIntegrationTest` (confere banco e evento) |

### Erros

Toda falha, de qualquer endpoint, devolve **o mesmo objeto**, então o frontend tem um único parser de erro:

```json
{
  "displayType": "BLOCK",
  "errorCode": "PAYMENT_DECLINED",
  "cartId": "0b35ee30-…",
  "frontendError": {
    "code": "P-1001",
    "title": "Pagamento não aprovado",
    "description": "Não foi possível concluir o pagamento. Tente novamente ou escolha outro meio.",
    "primaryButton":   { "label": "Tentar novamente", "url": "RETRY" },
    "secondaryButton": { "label": "Trocar meio de pagamento", "url": "/checkout/pagamento" }
  }
}
```

- `displayType`: `BLOCK` = tela de erro, o fluxo para · `INTERVENTION` = aviso, o usuário corrige e reenvia.
- `errorCode` / `code` / status HTTP são códigos estáveis para a lógica do frontend.
- `title`, `description` e os textos dos botões seguem o `Accept-Language` (`pt-BR` padrão, `en`).

| errorCode | HTTP | displayType | code | Quando |
|---|---|---|---|---|
| `INVALID_REQUEST` | 400 (ou 404/405/415) | INTERVENTION | P-1000 | JSON malformado, campo inválido, rota inexistente |
| `PAYMENT_DECLINED` | 422 | BLOCK | P-1001 | Cartão recusado (número terminado em `0002`) |
| `PRODUCT_NOT_FOUND` | 422 | BLOCK | P-1002 | Slug de plano inexistente, ex. `ultra` |
| `CART_NOT_FOUND` | 404 | BLOCK | P-1003 | `cartId` que não veio do pré-checkout |
| `PAYMENT_METHOD_NOT_ALLOWED` | 422 | INTERVENTION | P-1004 | Meio não oferecido para o carrinho, ex. `Boleto` |
| `INVALID_PAYMENT_DATA` | 400 | INTERVENTION | P-1005 | Cartão ausente, cartão enviado com Pix ou cartão vencido |
| `CART_ALREADY_PAID` | 409 | BLOCK | P-1006 | O carrinho já foi pago |
| `SERVICE_UNAVAILABLE` | 503 | BLOCK | P-9001 | Um serviço externo está fora do ar |
| `INTERNAL_ERROR` | 500 | BLOCK | P-9999 | Erro inesperado |

## 2. Arquitetura

Arquitetura em camadas: cada camada só chama a camada de baixo.

```
bff/src/main/java/com/jdp/checkout/bff
├── controller/   entrada HTTP: valida o formato da requisição, chama o service, devolve o DTO
├── dto/          contrato do frontend: request/ (entrada + Bean Validation) e response/ (saída + ErrorResponse)
├── mapper/       DTO ⇄ tipos do service
├── service/      regras de checkout e transações (PreCheckoutService, CheckoutService)
├── entity/       entidades JPA com as próprias regras (Cart, Payment, OutboxEvent)
├── repository/   acesso ao banco (Spring Data JPA)
├── model/        objetos de valor sem persistência (Product, PaymentMethod, CardData, ChargeResult)
├── client/       integrações externas: catálogo, meios de pagamento, gateway de pagamento
├── messaging/    eventos de pagamento: outbox, relay para o RabbitMQ, listener
├── exception/    códigos de erro, exceções e o contrato único de erro (GlobalExceptionHandler)
└── config/       configuração do Spring: clientes HTTP, cache, RabbitMQ, OpenAPI, Clock
mock-services/    serviços externos simulados (catálogo e meios de pagamento)
```

### Fluxo

```
                     ┌──────────────────────────── BFF ─────────────────────────────┐
 1. pré-checkout     │                                                              │
 Frontend ──────────►│ controller ─► PreCheckoutService ─┬─► client ──(HTTP)────────┼─► catálogo
                     │                                   ├─► client ──(HTTP)────────┼─► meios de pagamento
                     │                                   │       (cache no Redis)   │
                     │                                   └─► repository ────────────┼─► PostgreSQL (carrinho)
 ◄── cartId, produtos, meios de pagamento                                           │
                     │                                                              │
 2. checkout         │                                                              │
 Frontend ──────────►│ controller ─► CheckoutService ─┬─► repository (trava) ───────┼─► PostgreSQL
                     │                                ├─► PaymentGateway (cobrança) │   (simulado)
                     │                                └─► grava pagamento + evento ─┼─► PostgreSQL (outbox)
 ◄── APROVADO ou erro                                                               │
                     │  OutboxRelay (a cada 1s) ────────────────────────────────────┼─► RabbitMQ
                     └──────────────────────────────────────────────────────────────┘
```

1. O **pré-checkout** busca planos e meios de pagamento, valida os slugs escolhidos e grava o carrinho com
   uma cópia dos preços e dos meios oferecidos.
2. O **checkout** trava o carrinho, confere se o meio foi oferecido e se os dados do cartão batem com ele,
   cobra no gateway e grava o pagamento e o evento na mesma transação. Pagamento recusado também é gravado,
   e o carrinho pode ser pago de novo.
3. O **OutboxRelay** publica os eventos gravados no RabbitMQ para processamento assíncrono.

### Integrações

| Integração | Como | Observações |
|---|---|---|
| Catálogo | HTTP `GET /catalog/products` (`ProductCatalogClient`) | Cache no Redis (`CACHE_TTL`). Produtos inativos são ignorados. |
| Meios de pagamento | HTTP `GET /payment-methods` (`PaymentMethodsClient`) | Cache no Redis. Meios desconhecidos são ignorados. |
| Gateway de pagamento | Interface `PaymentGateway`, hoje `SimulatedPaymentGateway` | Cartão terminado em `0002` → recusado; qualquer outro → aprovado; Pix → aprovado com código simulado. |
| PostgreSQL | JPA + Flyway | Carrinhos, pagamentos e eventos do outbox. Número do cartão e CVV nunca são gravados. |
| RabbitMQ | Outbox → exchange `checkout.events` → fila `checkout.payment-events` (+ DLQ) | Eventos `payment.approved` / `payment.declined`, entrega at-least-once. |
| Redis | Spring Cache | Se o Redis cair, o BFF chama os serviços direto. |

Os serviços de catálogo e meios de pagamento são mocks no próprio projeto (`mock-services`), mas o BFF os
consome por clientes próprios (`client/`) via HTTP, nunca direto no endpoint: trocar pelo serviço real é só
mudar a URL. Falha ou timeout de um serviço externo vira `503 SERVICE_UNAVAILABLE`.

### Datas e fuso horário

- **Tudo em UTC.** O horário vem de um `Clock` injetado (`Clock.systemUTC()` em `ClockConfig`); o código nunca
  chama `Instant.now()` direto, então os testes usam um `Clock.fixed` e não dependem do dia em que rodam.
- **`Instant` no Java, `timestamptz` no banco.** `timestamptz` não guarda o fuso: guarda o instante absoluto
  (em UTC) e só converte na leitura. Assim os dados não dependem do fuso do servidor.
- **JSON em ISO-8601 com `Z`** (ex.: `"paidAt": "2026-11-11T13:05:23Z"`). O frontend converte para o horário
  local do usuário.
- **Validade do cartão em UTC-12, de propósito.** Um cartão vale até o fim do mês de validade, mas em qual
  fuso depende do emissor, que pode ser de qualquer país (e um país pode ter vários fusos). O BFF só considera
  o cartão vencido quando o mês acabou **em todos os fusos** (UTC-12 é o último a virar o mês). Assim nunca
  bloqueia um cartão válido; um cartão vencido há poucas horas segue para o emissor, que tem a palavra final
  e recusa. Em UTC, um cartão brasileiro seria recusado indevidamente a partir das 21h do último dia do mês.

## 3. Build, execução e deploy

### Instalação

Pré-requisitos: Git e Docker com Compose (para rodar sem Docker, JDK 25+).

```bash
git clone https://github.com/juliodepieri/globo-checkout-bff.git
cd globo-checkout-bff
```

### Docker (recomendado)

```bash
docker compose up -d --build     # compila e sobe BFF, mock services, PostgreSQL, RabbitMQ e Redis
docker compose ps                # confere se os 5 containers estão "running" / "healthy"
./scripts/demo.sh                # opcional: executa todos os cenários de sucesso e erro
docker compose logs -f bff       # acompanha os logs do BFF
docker compose stop              # parar mantendo os dados
docker compose down              # parar e remover os containers (os dados do banco são apagados junto)
```

| Serviço | Container | URL |
|---|---|---|
| BFF | `globo-checkout-bff` | http://localhost:8080 · Swagger: http://localhost:8080/swagger-ui.html |
| Mock services | `globo-checkout-bff-mock-services` | http://localhost:8081 |
| PostgreSQL | `globo-checkout-bff-postgres` | `localhost:5432` (banco, usuário e senha: `checkout`) |
| RabbitMQ | `globo-checkout-bff-rabbitmq` | `localhost:5672` · UI: http://localhost:15672 (guest/guest) |
| Redis | `globo-checkout-bff-redis` | `localhost:6379` |

**Porta 5432 ocupada** (outro PostgreSQL na máquina): o container `globo-checkout-bff-postgres` não sobe (fica
`created` no `docker compose ps -a`) e o BFF não inicia; rodando manualmente, o BFF conecta no outro banco e
falha com `password authentication failed for user "checkout"`. Use outra porta no host:

```bash
POSTGRES_PORT=5433 docker compose up -d --build
```

### Como simular sucesso e recusa

| Cenário | Como provocar | Resposta |
|---|---|---|
| Cartão aprovado | número que **não** termina em `0002`, ex. `4111111111114242` | `200` + `lastDigits` |
| Cartão recusado | número terminado em **`0002`**, ex. `4111111111110002` | `422 PAYMENT_DECLINED` |
| Pix aprovado | `"paymentMethod": {"name": "Pix"}`, sem `card` | `200` + `pixCode` |
| Pagamento duplicado | pagar de novo um carrinho aprovado | `409 CART_ALREADY_PAID` |
| Produto inexistente | slug `ultra` | `422 PRODUCT_NOT_FOUND` |
| Carrinho inexistente | `cartId` que não veio do pré-checkout | `404 CART_NOT_FOUND` |

```bash
# 1. Pré-checkout: guarde o cartId da resposta
curl -s -X POST localhost:8080/operations/pre-checkout -H 'Content-Type: application/json' \
  -d '{"products":[{"slug":"premium"}]}'

# 2a. Sucesso: cartão aprovado
curl -s -X POST localhost:8080/operations/checkout -H 'Content-Type: application/json' \
  -d '{"cartId":"<cartId>","paymentMethod":{"name":"Cartão de Crédito",
       "card":{"holderName":"Ana Souza","number":"4111111111114242","expiry":"12/28","cvv":"123"}}}'

# 2b. Recusa: mesmo comando com o número terminado em 0002
curl -s -X POST localhost:8080/operations/checkout -H 'Content-Type: application/json' \
  -d '{"cartId":"<cartId>","paymentMethod":{"name":"Cartão de Crédito",
       "card":{"holderName":"Ana Souza","number":"4111111111110002","expiry":"12/28","cvv":"123"}}}'
```

Também é possível testar pelo Swagger UI: em *Try it out*, escolha um dos exemplos prontos. No checkout,
troque o `cartId` do exemplo pelo devolvido no pré-checkout antes de executar (o do exemplo não existe e
devolve `404 CART_NOT_FOUND`). Ou rode `./scripts/demo.sh`, que executa todos os cenários.

### Manual

Requer JDK 25+. Suba só a infraestrutura no Docker e depois as apps pelo Gradle:

```bash
docker compose up -d postgres rabbitmq redis
./gradlew :mock-services:bootRun     # terminal 1, porta 8081
./gradlew :bff:bootRun               # terminal 2, porta 8080
```

Com o PostgreSQL em outra porta, informe a URL ao BFF:

```bash
POSTGRES_PORT=5433 docker compose up -d postgres rabbitmq redis
DB_URL=jdbc:postgresql://localhost:5433/checkout ./gradlew :bff:bootRun
```

Gerar os jars sem executar: `./gradlew build` (saída em `bff/build/libs` e `mock-services/build/libs`).

### Testes

```bash
./gradlew test
```

Cobrem o pré-checkout, o sucesso (cartão e Pix) e todos os cenários de erro. Os testes unitários usam
JUnit 5 + Mockito. O `CheckoutFlowIntegrationTest` usa Testcontainers (PostgreSQL e RabbitMQ reais) e é
pulado quando não há Docker. Ele roda o fluxo completo pela API, um cenário por teste, agrupados por assunto;
cada teste cita a regra de negócio que comprova:

| Grupo | Cenários |
|---|---|
| `PreCheckout` | carrinho criado com produtos e meios de pagamento · produto inexistente → `frontendError` |
| `Checkout` | cartão aprovado devolve só os últimos dígitos · Pix aprovado com `pixCode` · cartão `0002` recusado · carrinho recusado pode ser pago com outro meio · carrinho aprovado não pode ser pago de novo · 5 checkouts simultâneos aprovam só um · carrinho inexistente · meio de pagamento não oferecido |
| `CardData` | número completo e CVV não ficam no banco · evento publicado sem dados do cartão |
| `OpenApi` | Swagger documenta cada erro com o `frontendError` real |

### Variáveis de ambiente

| Variável | Padrão | Descrição |
|---|---|---|
| `SERVER_PORT` | `8080` | Porta HTTP do BFF |
| `DB_URL` | `jdbc:postgresql://localhost:5432/checkout` | URL JDBC do PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | `checkout` / `checkout` | Credenciais do PostgreSQL |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Endereço do RabbitMQ |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Credenciais do RabbitMQ |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Endereço do Redis |
| `CACHE_TYPE` | `redis` | `redis`, ou `none` para desligar o cache |
| `CACHE_TTL` | `10m` | Tempo de cache do catálogo e dos meios de pagamento (ex.: `30m`, `1h`) |
| `CATALOG_BASE_URL` | `http://localhost:8081` | URL do serviço de catálogo |
| `PAYMENT_METHODS_BASE_URL` | `http://localhost:8081` | URL do serviço de meios de pagamento |
| `API_DOCS_ENABLED` | `true` | `false` esconde o Swagger UI e o `/v3/api-docs` |
| `POSTGRES_PORT` | `5432` | Só no Docker Compose: porta do PostgreSQL no host, se a 5432 estiver ocupada |
