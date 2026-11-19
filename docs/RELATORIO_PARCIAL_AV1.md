# Relatório Parcial — Avaliação I (GAT108)
**Sistema de Monitoramento de Vibração em Máquinas Rotativas via Nó de Borda Móvel**

* **Autor:** Calebe de Sousa Oliveira (`calebe.oliveira@estudante.ufla.br`)
* **Repositório:** `https://github.com/Calebeee/Avancada.git`
* **Data:** 19/11/2026

---

## 1. Problema e Domínio Físico
A detecção precoce de desbalanceamentos e folgas mecânicas em máquinas rotativas (motores elétricos, compressores e ventiladores industriais) evita paragens não programadas e danos estruturais. Este projeto utiliza o acelerômetro MEMS de um smartphone como nó de borda local para adquirir a aceleração triaxial $(a_x, a_y, a_z)$ a uma taxa periódica de **20 Hz (período $T = 50\text{ ms}$)**, reconciliar as leituras sob a restrição física da gravidade local ($1g \approx 9,80665\text{ m/s}^2$) e isolar impactos mecânicos severos sem depender de processamento em nuvem.

---

## 2. Arquitetura e Decisões de Projeto (ADR)
A arquitetura foi estruturada em pacotes isolados por responsabilidade (`domain`, `estimation`, `concurrency`, `validation`) para garantir testabilidade determinística em JVM sem acoplamento com o ciclo de vida do Android:

| ID | Decisão Arquitetural | Justificativa Técnica |
|---|---|---|
| **D-01** | **Implementação em Java Nativo** | Compatibilidade direta com as APIs do Android SDK, previsibilidade de memória e execução determinística nos testes unitários JUnit. |
| **D-02** | **Imutabilidade no Domínio (`VibrationSample`)** | Todos os campos (`timestampMs`, `ax`, `ay`, `az`, `accuracyStdDev`) são `final` com validação de invariantes no construtor, impedindo corrupção de estado entre threads. |
| **D-03** | **Exclusão Mútua Explícita com `ReentrantLock`** | Protege o `CircularVibrationBuffer` contra condições de corrida entre a thread de aquisição (50 ms) e a interface gráfica (250 ms), devolvendo sempre cópias defensivas imutáveis. |
| **D-04** | **`ForegroundService` + `PARTIAL_WAKE_LOCK`** | Garante que o agendador periódico (`ScheduledExecutorService`) mantenha a taxa de 20 Hz durante 30 minutos contínuos mesmo com o ecrã bloqueado (`minSdk 26`). |

---

## 3. Fundamentação Matemática e Incerteza
1. **Matriz de Covariância ($R$):** Em vez de valores arbitrados, a matriz diagonal $R \in \mathbb{R}^{3\times 3}$ é construída diretamente a partir do desvio padrão ($\sigma$) reportado pela resolução e status de acurácia do sensor (`SensorManager`):
   $$R = \text{diag}(\sigma^2, \sigma^2, \sigma^2)$$
2. **Reconciliação por Mínimos Quadrados Ponderados (WLS):** Em regime estacionário/uniforme de rotação, o vetor de aceleração deve satisfazer a restrição física $\|a\| = g = 9,80665\text{ m/s}^2$. O reconciliador projeta a leitura medida sobre a superfície esférica de raio $g$ ponderada pelo inverso da variância ($R^{-1}$).
3. **Detector de Erro Grosseiro:** Antes da reconciliação, avalia-se o resíduo normalizado:
   $$r_{\text{norm}} = \frac{|\|a\| - g|}{\sigma}$$
   Se $r_{\text{norm}} > 3,0$ (regra dos $3\sigma$), a amostra é classificada como impacto/erro grosseiro e rejeitada.

---

## 4. Resultados Experimentais e Evidências Quantitativas

### 4.1. Reconciliação sob Restrição de Gravidade e Prova Negativa (Capacidade 2)
O ensaio `Week02ReconciliationTest` avaliou três janelas distintas de operação com ruído ($\sigma = 0,10\text{ m/s}^2$) e executou uma prova negativa injetando um impacto lateral severo de $+12,0\text{ m/s}^2$ no eixo $X$ enquanto o eixo $Z$ permanecia íntegro em $9,80665\text{ m/s}^2$:

| Cenário / Janela | Resíduo Bruto Antes ($\text{m/s}^2$) | Resíduo Reconciliado ($\text{m/s}^2$) | Comportamento Observado |
|---|---|---|---|
| **Janela 1** | $0,2712$ | $0,000000$ | Convergência exata para a restrição $1g$ |
| **Janela 2** | $0,2211$ | $0,000000$ | Convergência exata para a restrição $1g$ |
| **Janela 3** | $0,3312$ | $0,000000$ | Convergência exata para a restrição $1g$ |
| **Prova Negativa (Sem Detector)** | Erro $Z = 0,0000$ | **Erro $Z = 3,6138\text{ m/s}^2$** | **Degradação:** sem o filtro, a normalização espalha o erro de $X$ para o eixo $Z$ limpo. |

### 4.2. Teste de Corrida em Região Crítica (Capacidade 3)
O ensaio `Week03ConcurrencyTest` submeteu o `CircularVibrationBuffer` (capacidade de 100 amostras) a 10 execuções consecutivas de contenção simultânea (2.000 escritas concorrentes com 1.000 leituras de cópias defensivas):

| Execução | Escritas Concluídas | Tempo Total (ms) | Integridade / Monotonicidade | Status |
|---|---|---|---|---|
| **01** | 2.000 | 9 ms | Sem leituras rasgadas | PASSOU |
| **02** | 2.000 | 3 ms | Sem leituras rasgadas | PASSOU |
| **03** | 2.000 | 2 ms | Sem leituras rasgadas | PASSOU |
| **04** | 2.000 | 1 ms | Sem leituras rasgadas | PASSOU |
| **05** | 2.000 | 1 ms | Sem leituras rasgadas | PASSOU |
| **06** | 2.000 | 1 ms | Sem leituras rasgadas | PASSOU |
| **07** | 2.000 | 1 ms | Sem leituras rasgadas | PASSOU |
| **08** | 2.000 | 1 ms | Sem leituras rasgadas | PASSOU |
| **09** | 2.000 | 1 ms | Sem leituras rasgadas | PASSOU |
| **10** | 2.000 | 1 ms | Sem leituras rasgadas | PASSOU |

### 4.3. Sensibilidade na Injeção Determinística de Falhas (Capacidade 5)
Utilizando o `FaultInjector` com semente fixa (`seed = 42`, 200 amostras base, $\sigma = 0,10\text{ m/s}^2$ e limiar de corte $3,0\sigma = 0,30\text{ m/s}^2$), obtiveram-se os seguintes limites de deteção:

| Salto Injetado em $Z$ ($\text{m/s}^2$) | Falhas Injetadas | Falhas Detetadas | Taxa de Deteção (%) |
|---|---|---|---|
| **0,05** | 42 | 0 | **0,0%** |
| **0,15** | 42 | 0 | **0,0%** |
| **0,25** | 42 | 0 | **0,0%** |
| **0,30** | 42 | 42 | **100,0%** |
| **0,35** | 42 | 42 | **100,0%** |
| **0,50** | 42 | 42 | **100,0%** |
| **1,00** | 42 | 42 | **100,0%** |

**Análise do Limiar:** Observa-se uma transição abrupta (degrau) exatamente em $0,30\text{ m/s}^2$ ($3\sigma$). Perturbações abaixo de $0,25\text{ m/s}^2$ são absorvidas como ruído normal pelo reconciliador WLS, enquanto impactos iguais ou superiores a $0,30\text{ m/s}^2$ são 100% isolados pelo detector.

---

## 5. Aquisição Contínua e Evidência de Execução (Capacidade 4)
O serviço `VibrationAcquisitionService` opera em primeiro plano com notificação persistente (`NotificationChannel`), `PARTIAL_WAKE_LOCK` e gravação em ficheiro CSV (`vibration_log_<timestamp>.csv`) a cada 50 ms (20 Hz), com `flush` periódico a cada 20 amostras (1 segundo) para garantir persistência em disco mesmo após 30 minutos contínuos com o ecrã bloqueado.

---

## 6. Riscos Identificados e Mitigações
* **Saturação de memória em coletas longas:** Mitigado pelo uso de buffer circular de tamanho fixo (`capacity = 600`, equivalente a 30 segundos em RAM) aliado à descarga contínua para ficheiro CSV.
* **Contaminação da reconciliação por impactos mecânicos:** Mitigado pela filtragem prévia via `GrossErrorDetector` antes da projeção WLS.

---

## 7. Roteiro para a Avaliação II (Capacidades 6 a 10)
1. **Capacidade 6:** Instrumentação do tempo de execução ($C_i$) e análise de escalonabilidade (Liu & Layland) do laço de 50 ms.
2. **Capacidade 7:** Extração de características espectrais (FFT/energia por banda) e aprendizagem online sob memória constante com validação em conjunto congelado.
3. **Capacidade 8:** Integração de agente local substituível com fallback determinístico (`RuleEngine`).
4. **Capacidades 9 e 10:** Comunicação cifrada entre dois nós móveis e avaliação comparativa via Lei de Amdahl.

---

## 8. Checklist de Integridade do Repositório
* [x] Commits individuais realizados com e-mail institucional `@estudante.ufla.br`.
* [x] Ficheiros `docs/PROPOSAL.md` e `docs/DECISIONS.md` versionados.
* [x] Ausência de binários pesados (`*.tflite`, `*.onnx`, `/build`) bloqueados pelo `.gitignore`.
* [x] Suíte completa de testes unitários (`Week01` a `Week04`) executando com 100% de aprovação.
