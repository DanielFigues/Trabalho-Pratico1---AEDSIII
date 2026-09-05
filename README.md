# TP1 - Algoritmos e Estruturas de Dados III (PUC Minas)
### CRUD Sequencial + Ordenação Externa — Base de dados: Jogos da Steam

Tema escolhido: dataset público **"Steam Store Games"** (Kaggle), com ~27 mil
jogos da plataforma Steam (`data/steam.csv`).

## Como compilar e rodar

```bash
cd steamtp1
javac -d out $(find src -name "*.java")
java -cp out Main
```

O programa cria/usa o arquivo binário em `data/steam.bin`. Ao escolher a
opção 1 do menu, ele carrega `data/steam.csv` para dentro desse arquivo
binário (é criado automaticamente na primeira execução).

## Entidade e tipos de campo exigidos

A entidade `Game` (model/Game.java) representa um jogo, cobrindo os 5 tipos
de campo pedidos no enunciado:

| Requisito                        | Campo             | Observação |
|-----------------------------------|--------------------|------------|
| String de tamanho fixo            | `developer`        | 30 bytes fixos (trunca/preenche com espaço) |
| String de tamanho variável        | `name`             | `DataOutputStream.writeUTF` |
| Data                              | `releaseDate`      | gravada como `epochDay` (long) |
| Lista de valores com separador    | `genres`           | separador `;` (mesmo separador já usado no CSV original) |
| Inteiro / Float                   | `price` (float), `positiveRatings`/`negativeRatings` (int) | |

O `appid` da Steam é usado como **id** (chave primária) do registro.

## Estrutura do arquivo binário (GameFile)

```
[Cabeçalho] int  -> último id utilizado (4 bytes)
[Registro]  byte lápide (' ' válido / '*' removido) | int tamanho | byte[tamanho] vetor de bytes do objeto
[Registro]  ...
```

`model/Game.java` cuida apenas da (de)serialização do objeto em si
(`toByteArray`/`fromByteArray`). `file/GameFile.java` é quem envolve esse
vetor de bytes com a lápide e o indicador de tamanho, e implementa o CRUD:

- **create**: grava no fim do arquivo, atualizando o cabeçalho quando um id
  novo (maior que o último usado) é inserido.
- **read(id)**: varredura sequencial; para registros inválidos (removidos)
  ou com id diferente, pula direto `tamanho` bytes sem desserializar o
  objeto inteiro (otimização simples).
- **update**: se o novo registro serializado tem o mesmo tamanho do antigo,
  sobrescreve no próprio local; se o tamanho mudou, marca o antigo como
  removido (lápide) e grava o novo no fim do arquivo — exatamente como pede
  o enunciado.
- **delete(id)**: apenas marca a lápide do registro como removido.

## Ordenação Externa (`sort/ExternalSort.java`)

Implementa **Intercalação Balanceada de Múltiplos Caminhos** (Balanced
Multiway External Merge Sort), recebendo como parâmetros:

- **número de caminhos** — quantidade de arquivos temporários usados tanto
  na distribuição quanto na intercalação;
- **número máximo de registros em memória primária** — tamanho de cada bloco
  ordenado em memória (run inicial).

**Fase 1 — Distribuição:** lê apenas os registros *válidos* do arquivo de
origem em blocos de até N registros, ordena cada bloco em memória (por id)
e grava os blocos ordenados alternadamente nos arquivos-caminho.

**Fase 2 — Intercalação:** a cada rodada, agrupa uma run de cada um dos
arquivos-caminho e faz a intercalação (k-way merge, com fila de prioridade)
delas em uma única run maior, distribuindo o resultado nos arquivos da
próxima geração. Repete até restar uma única run.

Como só os registros válidos entram na ordenação, o arquivo resultante já
sai **compactado** (sem lápides nem espaços de registros antigos) — como
pedido no enunciado.

Ao final, o menu pergunta se o usuário deseja substituir o arquivo binário
principal pelo arquivo ordenado, para que as operações de CRUD seguintes
passem a atuar sobre ele.

## Autoria

Todo o código (parsing do CSV, serialização binária, CRUD sequencial e
ordenação externa) é autoral, com exceção das classes de E/S de arquivo do
próprio Java (`RandomAccessFile`, `DataInputStream`/`DataOutputStream`),
conforme permitido no enunciado.
