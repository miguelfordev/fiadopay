FiadoPay — Backend Refatorado (Java 21 + Spring Boot)
Este projeto é uma refatoração do FiadoPay, seguindo as exigências da disciplina para aplicar boas práticas de engenharia de software, anotações customizadas, reflexão, threads assíncronas e manutenção do contrato da API original.
O objetivo principal foi tornar o sistema mais modular, extensível, seguro e organizado, mantendo todo o comportamento que o FiadoPay já tinha.

Contexto
O FiadoPay original apresentava um nível crítico de alto acoplamento e baixa coesão.
 Grande parte das regras essenciais estavam centralizadas dentro de um único serviço (PaymentService), que misturava responsabilidades diversas e independentes.
Dentro dessa classe havia:
Criação e autenticação de merchants


Criação, consulta e refund de pagamentos


Processamento síncrono dos pagamentos


Juros e regras de parcelamento coladas no código


Validações antifraude embutidas diretamente na lógica


Envio de webhooks de forma bloqueante


Persistência realizada diretamente via repository


Ausência total de extensibilidade


Nenhum processamento assíncrono


Em um sistema de pagamentos real, esse design seria inviável:
 alterar ou adicionar um método de pagamento, regra de antifraude ou webhook exigiria modificar o núcleo do sistema, quebrando modularidade e fragilizando toda a arquitetura.


Objetivo da Refatoração
A meta foi refatorar completamente o núcleo interno do FiadoPay sem alterar nenhum comportamento exposto na API, preservando:
Rota de autenticação fake


Idempotência com chave obrigatória


Fluxo de criação de pagamento


Webhooks


Estrutura conceitual dos pagamentos


Juros aplicados para pagamentos parcelados


Ou seja:
O cliente que consome o FiadoPay não deve perceber nenhuma alteração,
 mas internamente o sistema agora conta com um arcabouço robusto, extensível e orientado a componentes.

Decisões de Design
🔹 2.1. Introdução da fachada: PaymentServiceFacade
Antes, o controller chamava múltiplos serviços diretamente.
Agora, um único ponto orquestra tudo, reduz acoplamento.
Isso melhora:
testes,


troca de implementação,


leitura de código.


Estratégias de juros por método de pagamento
Criamos o pacote:
strategies/

Com implementações para cada método:
PixInterestStrategy


DebitInterestStrategy


BoletoInterestStrategy


E todas implementam:
public interface PaymentInterestStrategy

Cada estratégia foi anotada com:
@PaymentHandler("PIX")
@PaymentHandler("DEBIT")
@PaymentHandler("BOLETO")

Assim, o FiadoPay passou a suportar juros/sem juros por estratégia, e fica fácil estender para Cartão (com juros reais).
Uso de reflexão + anotações customizadas
Criamos a anotação:
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PaymentHandler {
    String value();
}

O PaymentCreatorService lê todas as estratégias automaticamente, assim:
O código fica menos hardcoded


Não existe mais if(method.equals("PIX")) ...


O sistema escala com 1 linha nova de código por estratégia


2.4. Processamento Assíncrono com ExecutorService
Para remover o comportamento bloqueante do FiadoPay — onde a criação do pagamento esperava toda a cadeia de processamento (cálculo de juros, antifraude, simulação de gateway e disparo de webhook) — foi introduzido um mecanismo de execução paralela via ExecutorService.
Criamos a classe:
config/ExecutorConfig.java

Ela expõe um bean Spring responsável por gerenciar um pool fixo de threads:
Executors.newFixedThreadPool(10);

Esse pool é utilizado para:
Processar pagamentos em background
 O usuário recebe imediatamente a resposta da API (status PENDING), enquanto o processamento real ocorre “por trás do sistema”, tal como gateways como Stripe, Pagar.me e Adyen.


Executar webhooks de forma assíncrona
 O envio do webhook agora ocorre em outra thread, com possíveis retries, evitando travar o fluxo principal de pagamento.


Simular latências e fluxos reais de aprovação
 O sistema agora permite simular:


análise de antifraude


processamento externo


delays no gateway


marcação posterior como APPROVED ou DECLINED


Por que isso melhora o sistema?
Antes, todo o fluxo era síncrono, o que:
degradava o tempo de resposta,


tornava o sistema irreal para um gateway de pagamentos,


acoplava API a tempo de processamento,


inviabilizava futuras escalabilidades.


Agora, com processamento assíncrono:
a API responde rápido,


os fluxos ficam independentes,


e o FiadoPay passa a se comportar como um gateway de verdade, com eventos internos rodando em paralelo.


2.5. Webhook automático
O WebhookProcessor recebe o pagamento depois do processamento e dispara um callback.
O fluxo é:
Cria pagamento → Status = PENDING
Thread roda → APPROVED ou DECLINED
Webhook enviado automaticamente

2.6. Simulação de fraude
O FailureSimulator devolve true ou false com probabilidade de 60%.
Isso força o sistema a alternar entre:
APPROVED


DECLINED


Simula exatamente o comportamento de um gateway real.

Arquitetura Final
edu.ucsal.fiadopay
 ├── annotations/
 │     └── PaymentHandler.java
 │
 ├── config/
 │     ├── ExecutorConfig.java
 │     ├── HttpClientConfig.java
 │
 ├── controller/
 │     ├── PaymentController.java
 │     ├── PaymentRequest.java
 │     ├── PaymentResponse.java
 │
 ├── domain/
 │     ├── Merchant.java
 │     ├── Payment.java
 │
 ├── records/
 │     └── InterestResult.java
 │
 ├── repo/
 │     ├── MerchantRepository.java
 │     ├── PaymentRepository.java
 │
 ├── service/
 │     ├── PaymentServiceFacade.java
 │
 ├── service/auth/
 │     └── MerchantAuthService.java
 │
 ├── service/payment/
 │     ├── PaymentCreatorService.java
 │     ├── PaymentQueryService.java
 │
 ├── service/webhook/
 │     └── WebhookProcessor.java
 │
 ├── service/fraud/
 │     └── FailureSimulator.java
 │
 └── strategies/
       ├── BoletoInterestStrategy.java
       ├── DebitInterestStrategy.java
       ├── PixInterestStrategy.java
       └── PaymentInterestStrategy.java

Mecanismo de Reflexão
Como funciona:
Spring injeta automaticamente todas as classes que implementam PaymentInterestStrategy.


No momento da criação do pagamento, o código verifica:


a classe tem @PaymentHandler?


o valor do handler bate com o req.method()?


Se sim → essa estratégia calcula os juros para aquele pagamento.


Esse mecanismo permite plugabilidade total.

Threads e Assíncrono
O processamento principal é feito por:
executor.submit(() -> { ... })

Dentro dessa thread ocorre:
espera simulada (Thread.sleep)


simulação de fraudes


atualização do status


envio do webhook


Isso evita travar a requisição principal, como um gateway de verdade.

Padrões Aplicados
Facade
PaymentServiceFacade unifica a complexidade do fluxo.
Strategy
Cada método de pagamento tem sua estratégia.
Annotation + Reflection
Para selecionar estratégias dinamicamente.
Repository Pattern
Com Spring Data JPA.
Asynchronous Processing
Com ExecutorService.

Limites e Pontos Conhecidos
O webhook não verifica SSL real (é simulado).


Fraude é aleatória e não baseada em dados comportamentais.


Não há persistência garantida caso o servidor desligue no meio da execução.


Taxas de juros ainda são estáticas (para cartão ainda não implementadas).


O sistema ainda não permite reprocessamento de webhook.


