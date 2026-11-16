# Proposta de trabalho - Indivíduo Calebe
- Integrantes: Calebe de Sousa Oliveira
- Data: 31/08/2026
- Domínio escolhido: Monitoramento de Vibração em Máquinas Rotativas

## 1. O problema
Falhas em motores industriais e compressores causam paradas inesperadas e prejuízos. A manutenção preditiva exige sensores dedicados e caros, que processam dados em nuvem. A solução proposta utiliza o celular como nó de borda para aprender a assinatura de vibração normal da máquina e alertar sobre desbalanceamentos localmente.

## 2. O que é observado
- Ativo ou processo: Motor ou máquina rotativa (ex: compressor, ventilador de bancada).
- Grandeza física medida: Aceleração nos eixos X, Y e Z.
- Sensor e origem da incerteza: Acelerômetro MEMS do celular, cuja incerteza provém do ruído branco do sensor e da vibração de fundo do ambiente.
- Período de amostragem pretendido e prazo associado: Amostragem a cada 50 milissegundos, com prazo rigoroso verificado na camada 1.

## 3. Mapeamento das dez capacidades
| # | Capacidade | O que significa no nosso domínio | Teste de aceitação |
|---|---|---|---|
| 1 | Domínio, contratos e estruturas | Estrutura que armazena janelas de vibração | A estrutura responde a recortes de tempo e média de aceleração em memória constante. |
| 2 | Incerteza e reconciliação | Reconciliação baseada na restrição da gravidade (1g) | O resíduo da aceleração cai em 3 janelas de leitura, detectando um erro grosseiro injetado (impacto na máquina). |
| 3 | Concorrência e região crítica | Buffer de leituras do acelerômetro | O teste de corrida na região crítica entre a thread do sensor e a da interface roda 10 vezes sem falhar. |
| 4 | Aquisição periódica em tempo real | Leitura contínua da vibração | O aplicativo roda por 30 minutos contínuos com a tela bloqueada lendo o acelerômetro, com logs provando a execução. |
| 5 | Ensaio reprodutível e injeção de falhas | Simulação de desbalanceamento mecânico | Reprodução determinística injetando picos de ruído simulado, com tabela de sensibilidade de detecção. |
| 6 | Instrumentação e escalonabilidade | Medição do custo computacional por ciclo | Tabela de tarefas com Ci medido e dispersão, atestando a escalonabilidade do laço de leitura. |
| 7 | Aprendizado online sob memória constante | Aprendizado da assinatura espectral normal | O sistema prevê o estado de vibração e valida a anomalia em um conjunto de dados retido (separado do treino). |
| 8 | Agente e modelo substituível | Alerta de anomalia local | O sistema continua funcionando e emitindo alertas usando apenas o RuleEngine quando o modelo de linguagem é removido. |
| 9 | Criptografia e frota multi-nó | Fusão de dados entre dois sensores | Dois celulares na mesma máquina trocam dados cifrados via ponto de acesso para confirmar um alerta de alta vibração em conjunto. |
| 10 | Validação cega, TRL e Lei de Amdahl | Avaliação do ganho com o modelo | Ensaio cego comparando o motor determinístico com o modelo; curva de speedup calculada com cinco repetições. |

## 4. Dados
- Como serão obtidos: Celular posicionado sobre uma máquina rotativa (ex: ventilador ou motor) com coleta contínua.
- Quantos dias ou quantas execuções: 10 execuções de 30 minutos (condições normais e induzindo trepidação).
- Como a separação treino e validação será congelada, e em que data: 7 execuções serão o conjunto de treino/ajuste e 3 execuções serão congeladas no dia 15/09/2026 para validação exclusiva.

## 5. Riscos
| Risco | Probabilidade | Plano B |
|---|---|---|
| Saturação rápida do buffer devido à alta taxa de amostragem do acelerômetro. | Alta | Implementar decimação ou médias móveis antes da inserção no buffer para aliviar a concorrência. |
| Inviabilidade de rodar o modelo na memória do aparelho testado. | Baixa | Demonstrar o projeto usando estritamente o RuleEngine para os alertas, documentando a restrição. |

## Parecer do docente
- [x] Aprovada - [ ] Aprovada com ressalva - [ ] A refazer
- Observações:
