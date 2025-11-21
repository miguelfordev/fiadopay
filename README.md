# FiadoPay — Backend Refatorado (Java 21 + Spring Boot)

Este projeto é uma refatoração completa do FiadoPay, seguindo a especificação da disciplina.  
Foram aplicadas boas práticas modernas de engenharia de software, com foco em desacoplamento, modularidade, anotações customizadas, reflexão e processamento assíncrono — mantendo 100% do contrato original da API.

---

# 1. Contexto

O sistema original possuía alto acoplamento e baixa coesão.  
Grande parte das responsabilidades estava centralizada em `PaymentService`, que acumulava funções completamente distintas:

*   criação e autenticação de merchants  
*   criação, consulta e refund de pagamentos  
*   cálculo de juros e parcelamento  
*   validações antifraude  
*   simulação de gateway  
*   envio de webhooks de forma **bloqueante**  
*   acesso direto aos repositórios  
*   nenhuma extensibilidade via plugins  
*   fluxo 100% síncrono  

Na prática, isso tornava o FiadoPay rígido, difícil de modificar e distante de um gateway real.

---

# 2. Objetivo da Refatoração

A meta foi reorganizar totalmente o núcleo interno do FiadoPay sem alterar:

*   rotas  
*   formatos das requisições  
*   regras de idempotência  
*   retorno dos endpoints  
*   comportamento dos webhooks  
*   simulação de aprovação/declínio  

Ou seja: **externamente nada muda**, mas internamente o sistema passa a ser modular, limpo e extensível.

---

# 3. Decisões de Arquitetura

## 3.1. Fachada `PaymentServiceFacade`

Antes: o controller chamava vários serviços.  
Depois: um único ponto central coordena tudo.

Benefícios:
*   menor acoplamento  
*   código mais legível  
*   serviço pronto para expansão  
*   teste isolado facilitado  

---

## 3.2. Estratégias de Juros (`strategies/`)

Criamos um pacote `strategies/` contendo:

- `PixInterestStrategy`
- `DebitInterestStrategy`
- `BoletoInterestStrategy`

Todas implementam:

```
public interface PaymentInterestStrategy {
    InterestResult calculate(BigDecimal amount, Integer installments);
}
```
Cada estratégia recebe a anotação:
```
@PaymentHandler("PIX")
```
Isso elimina IFs gigantes e habilita plugabilidade real.

## 3.3. Anotações Customizadas + Reflexão

Criada a anotação:
```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PaymentHandler {
    String value();
}
```
A classe PaymentCreatorService faz descoberta automática de todas as estratégias anotadas.

Vantagens:

*   adicionar um novo método de pagamento = criar 1 classe → o sistema detecta sozinho
*   zero alteração no núcleo
*   comportamento dinâmico e extensível

## 3.4. Processamento Assíncrono com ExecutorService

Para remover o comportamento bloqueante original, criamos:

config/ExecutorConfig.java:
```
@Bean
public ExecutorService paymentExecutor() {
    return Executors.newFixedThreadPool(10);
}
```
Esse pool de threads processa:

*   aprovação/declínio dos pagamentos
*   simulação de antifraude
*   delays de gateway
*   envio de webhooks

O fluxo se torna:

*   API recebe pagamento
*   retorna imediatamente com PENDING
*   thread separada processa
*   webhook é disparado depois

Isso simula gateways reais como Stripe, Adyen e Pagar.me.

## 3.5. Webhook Automático

WebhookProcessor executa em background:

*   leitura do pagamento
*   envio do callback
*   possíveis retries
*   mudança de status

## 3.6. Simulação de Antifraude

FailureSimulator retorna true com 60% de chance:

```
public boolean shouldFail() {
    return random.nextDouble() < 0.6;
}
```
Resultado:

*   pagamento pode ser APPROVED ou DECLINED
*   simulação realista de gateway

# 4. Arquitetura Final
```
edu.ucsal.fiadopay
├── annotations/
│   └── PaymentHandler.java
│
├── config/
│   ├── ExecutorConfig.java
│   └── HttpClientConfig.java
│
├── controller/
│   ├── PaymentController.java
│   ├── PaymentRequest.java
│   └── PaymentResponse.java
│
├── domain/
│   ├── Merchant.java
│   └── Payment.java
│
├── records/
│   └── InterestResult.java
│
├── repo/
│   ├── MerchantRepository.java
│   └── PaymentRepository.java
│
├── service/
│   ├── PaymentServiceFacade.java
│
│   ├── auth/
│   │   └── MerchantAuthService.java
│
│   ├── payment/
│   │   ├── PaymentCreatorService.java
│   │   └── PaymentQueryService.java
│
│   ├── fraud/
│   │   └── FailureSimulator.java
│
│   └── webhook/
│       └── WebhookProcessor.java
│
└── strategies/
    ├── PixInterestStrategy.java
    ├── DebitInterestStrategy.java
    ├── BoletoInterestStrategy.java
    └── PaymentInterestStrategy.java
```
# 5. Mecanismo de Reflexão

O Spring injeta todas as classes que implementam PaymentInterestStrategy.
O PaymentCreatorService faz:

*   varre as estratégias
*   identifica as anotadas com @PaymentHandler
*   compara o valor com req.method()
*   aplica a correspondente

Isso elimina IF/ELSE e cria um sistema baseado em plugins nativos.

# 6. Processamento Assíncrono

O fluxo principal executa:
```
executor.submit(() -> {
    Thread.sleep(...);
    // fraude
    // gateway
    // atualizar status
    // enviar webhook
});
```
Ganho:

*   API rápida
*   gateway realista
*   escalabilidade
*   isolamento dos fluxos internos

# 7. Padrões Aplicados

| Padrão               | Onde foi usado                           |
|----------------------|-------------------------------------------|
| Facade               | PaymentServiceFacade                      |
| Strategy             | Métodos de pagamento (PIX/DEBIT/BOLETO)  |
| Repository           | Spring Data JPA                           |
| Annotation + Reflection | Descoberta das estratégias            |
| Async Processing     | ExecutorService                           |
| IoC/DI               | Spring Boot                               |

# 8. Limites do Sistema

* Webhook não verifica SSL real
* Fraude é pseudoaleatória
* Persistência pode falhar em desligamento abrupto
* Juros reais de cartão não implementados
* Webhook não possui DLQ

# 9. Prints

## 9.1 Anotações Customizadas:

* Comprova o uso de metadados customizados fundamentais para o funcionamento do sistema. Essas anotações permitem que os handlers sejam descobertos por reflexão, evitando if-else e switch-case espalhados pela aplicação.


<img width="671" height="197" alt="Captura de tela 2025-11-21 180442" src="https://github.com/user-attachments/assets/6bafb7e7-ea1b-4249-bb3c-697db69355a6" />
<img width="648" height="176" alt="Captura de tela 2025-11-21 181007" src="https://github.com/user-attachments/assets/23826823-3155-4da2-9a83-88cbcd7a558d" />

## 9.2 Estratégias de Juros:

* Valida a implementação do Padrão Strategy + Annotation-Based Discovery.

<img width="643" height="335" alt="Captura de tela 2025-11-21 181104" src="https://github.com/user-attachments/assets/649ac38a-d6ec-4215-9ac0-d1b2ce23d87b" />
<img width="691" height="374" alt="Captura de tela 2025-11-21 181217" src="https://github.com/user-attachments/assets/39721eb6-0e47-4d90-b2d5-e2627c64a832" />
<img width="627" height="348" alt="Captura de tela 2025-11-21 181258" src="https://github.com/user-attachments/assets/86780015-3f76-49fb-821a-8f1072cfe8ad" />


## 9.3 ExecutorService em ação:

* Demonstra execução assíncrona dos webhooks, requisito essencial do trabalho.

  <img width="458" height="286" alt="Captura de tela 2025-11-21 181410" src="https://github.com/user-attachments/assets/9114d3a4-7cd1-4d04-a399-449f4e40416e" />
  <img width="629" height="214" alt="Captura de tela 2025-11-21 181503" src="https://github.com/user-attachments/assets/3a96e651-468c-44b8-ad6a-d2a8ad296cfd" />


## 9.4 Controller em funcionamento:

* Mostra que a API está coerente com o contrato definido: auth, idempotência e CRUD de pagamentos.

<img width="888" height="539" alt="Captura de tela 2025-11-21 181823" src="https://github.com/user-attachments/assets/731eead4-436f-40bb-81ea-ea211bc279bb" />

# 10. Como Rodar (FiadoPay Simulator)

Gateway de pagamento FiadoPay para a disciplina de POO Avançado. Substitui PSPs reais com um backend em memória (H2).

## Instalação e Execução

```
./mvnw spring-boot:run
# ou
mvn spring-boot:run

```
## Pré-requisitos

Para executar este projeto, certifique-se de ter o ambiente configurado com:
Java: JDK 21 ou superior.
Maven: 3.9.x ou superior.
h2 console: http://localhost:8080/h2
Swagger UI: http://localhost:8080/swagger-ui.html




