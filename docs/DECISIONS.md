# Registro de Decisões Arquiteturais (ADR) — Projeto GAT108
- **Integrante:** Calebe de Sousa Oliveira
- **Domínio:** Monitoramento de Vibração em Máquinas Rotativas

## D-01 — Linguagem de Programação Principal
- **Data:** 16/11/2026
- **Decisão:** Utilizar Java como linguagem principal para todas as camadas do sistema e testes unitários.
- **Alternativas consideradas:** Kotlin; abordagem híbrida (Java + Kotlin).
- **Motivo:** Os contratos e testes de unidade exigidos pela disciplina são estruturados em Java, e o uso de primitivas explícitas de sincronização (`synchronized`, `ReentrantLock`, `Thread`) facilita a análise de concorrência e escalonabilidade exigida na AV1 e AV2.
- **Consequências:** Código mais verboso nas classes de domínio imutáveis, mas zero atrito de interoperabilidade com o JUnit e controle explícito sobre o ciclo de vida das threads.
- **Decidido por:** Calebe, em 16/11/2026.

## D-02 — Tecnologia de Interface Gráfica
- **Data:** 16/11/2026
- **Decisão:** Utilizar XML Views com ViewBinding.
- **Alternativas consideradas:** Jetpack Compose.
- **Motivo:** Alinhamento direto com a escolha do Java puro, eliminando a necessidade de configurar o compilador do Kotlin e permitindo foco total na lógica de aquisição em tempo real e reconciliação.
- **Consequências:** Atualização periódica dos painéis na UI será feita via `Handler` / `runOnUiThread` lendo cópias defensivas do buffer.
- **Decidido por:** Calebe, em 16/11/2026.

## D-03 — Modelo de Concorrência na Camada de Tempo Real
- **Data:** 16/11/2026
- **Decisão:** Utilizar `Thread`, `ScheduledExecutorService` e `ReentrantLock` explícitos na Camada 1.
- **Alternativas consideradas:** Corrotinas; `AsyncTask` (obsoleto).
- **Motivo:** Garante mapeamento 1-para-1 entre tarefa periódica e thread do sistema operacional, essencial para medir o tempo de execução ($C_i$) e provar escalonabilidade sem abstrações de pool ocultas.
- **Consequências:** Necessidade de gerenciamento explícito de interrupção e limpeza de recursos no ciclo de vida do `ForegroundService`.
- **Decidido por:** Calebe, em 16/11/2026.

## D-04 — Nível Mínimo de API Android (minSdk)
- **Data:** 16/11/2026
- **Decisão:** Fixar `minSdk = 26` (Android 8.0).
- **Alternativas consideradas:** `minSdk = 24`; `minSdk = 29`.
- **Motivo:** Suporta canais de notificação obrigatórios para o `ForegroundService` de aquisição contínua e garante acesso completo aos metadados de acurácia e resolução do `SensorManager`.
- **Consequências:** Verificação condicional em tempo de execução para permissões de serviço em primeiro plano.
- **Decidido por:** Calebe, em 16/11/2026.

## D-05 — Desenvolvimento Individual e Autoria
- **Data:** 16/11/2026
- **Decisão:** Execução individual integral do projeto após saída do integrante anterior da dupla.
- **Alternativas consideradas:** Redistribuição de escopo.
- **Motivo:** Conforme seção 2.2 e 3 do guia da disciplina, projetos individuais seguem exatamente as mesmas dez capacidades e critérios de aceitação.
- **Consequências:** Todos os commits serão assinados exclusivamente com a conta institucional `@ufla.br` do integrante.
- **Decidido por:** Calebe, em 16/11/2026.