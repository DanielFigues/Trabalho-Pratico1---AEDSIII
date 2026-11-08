# TP1 e TP3 - Algoritmos e Estruturas de Dados III (PUC Minas)
### CRUD Sequencial + Ordenação Externa (TP1) e Compactação + Casamento de Padrões (TP3) — Base de dados: Jogos da Steam

Tema escolhido: dataset público **"Steam Store Games"** (Kaggle), com ~27 mil
jogos da plataforma Steam (`data/steam.csv`).

## Como compilar e rodar

```bash
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

## Etapa 3 — Compactação e Casamento de Padrões

Opções adicionadas ao menu principal:

| Opção | O que faz |
|-------|-----------|
| 9  | Compacta a base (`data/steam.bin`) com **Huffman** e **LZW**, mostra ganho/perda e tempo de cada um e diz qual foi melhor |
| 10 | Descompacta uma versão **X** e **substitui** o arquivo de dados pelo resultado, mostrando o tempo de cada algoritmo |
| 11 | Busca um padrão (nome, desenvolvedora, gêneros ou arquivo inteiro) com **KMP** e **Boyer-Moore** e compara os dois |

### Compactação (`compress/`)

Os algoritmos recebem o arquivo binário **inteiro** como vetor de bytes, sem conhecer a
entidade `Game`. Por isso são compactados todos os campos, incluindo o cabeçalho (último id),
as lápides, os indicadores de tamanho e as strings, e a descompressão devolve o arquivo
byte a byte idêntico ao original.

- **Nome dos arquivos gerados:** `nomeArquivo` + `NomeAlgoritmo` + `Compressao` + `X`, por exemplo
  `steamHuffmanCompressao1.bin` e `steamLZWCompressao1.bin`. Cada execução da opção 9 cria uma
  nova versão (X = 1, 2, 3, ...) para os dois algoritmos, sem apagar as anteriores.
- **Huffman** (`Huffman.java`): frequência de cada um dos 256 valores de byte, árvore de Huffman
  e código de bits por byte. O arquivo compactado guarda a tabela de frequências e o
  descompressor reconstrói a mesma árvore (os empates são desfeitos de forma determinística).
- **LZW** (`LZW.java`): **dicionário inicial definido pelo grupo** com os 256 valores de byte
  (códigos 0–255) mais o código de controle `CLEAR` (256); as sequências aprendidas começam no
  código 257. Os códigos têm largura variável (9 a 16 bits) e, quando o dicionário enche, o
  compressor emite `CLEAR` e recomeça do dicionário inicial.
- **Descompressão:** o usuário informa a versão X; os dois algoritmos são executados e
  cronometrados, o resultado dos dois precisa ser idêntico e o arquivo de dados é substituído
  (gravando primeiro em um arquivo temporário).
- **Medidas:** o ganho é `(1 - tamanho compactado / tamanho original) * 100` (valor negativo
  indica perda). O tempo considera só a execução do algoritmo sobre os bytes em memória, sem a
  leitura/gravação dos arquivos. Antes de gravar, a compressão é conferida descompactando
  em memória.

Exemplo de resultado com o `steam.bin` deste repositório (8.355.097 bytes):

| Algoritmo | Tamanho compactado | Ganho |
|-----------|-------------------:|------:|
| Huffman   | 5.391.529 bytes    | 35,47% |
| LZW       | 2.996.657 bytes    | 64,13% |

O LZW comprime mais porque os registros têm muitas **sequências** repetidas (nomes de gêneros,
desenvolvedoras, a mesma estrutura de campos em todo registro), e o dicionário aproveita isso. O
Huffman só enxerga a frequência de bytes isolados. Em compensação o Huffman costuma comprimir
mais rápido, e o LZW descomprime mais rápido (a descompressão do Huffman lê um bit por vez).

### Casamento de padrões (`search/`)

Foram escolhidos **KMP** e **Boyer-Moore** (a força bruta não é permitida). A busca pode ser
feita no nome, na desenvolvedora, nos gêneros ou no arquivo binário inteiro (bytes). O padrão e o
texto são comparados como bytes UTF-8, o que encontra as mesmas ocorrências que a comparação
por caracteres, inclusive com acentos. Nos campos de texto é possível ignorar maiúsculas e
minúsculas.

**Justificativa da escolha:**

- **KMP** nunca volta no texto e tem custo garantido de O(n + m) no pior caso, qualquer que seja
  o padrão. É a escolha segura para padrões com muita repetição (por exemplo `aaaa`) e para
  varrer o arquivo de forma estritamente sequencial.
- **Boyer-Moore** compara o padrão da direita para a esquerda e usa as heurísticas do caractere
  ruim e do sufixo bom para pular vários bytes de uma vez. Com um alfabeto grande (texto em
  inglês, 256 valores de byte) e padrões de tamanho médio, costuma examinar bem menos de n bytes.
- Escolher um de cada tipo permite **comparar na prática** um algoritmo de custo previsível com
  um de bom desempenho médio. A opção 11 mostra, para cada um, ocorrências, número de
  comparações e tempo. Por exemplo, ao buscar `counter` no nome dos jogos, o KMP fez 1.535.100
  comparações e o Boyer-Moore 236.616, com o mesmo número de ocorrências (90).
- O tempo é medido sobre textos já carregados em memória e depois de uma passada de
  aquecimento da JVM, para a comparação ser justa. Como a base é pequena, o tempo dos dois
  fica próximo; a diferença aparece melhor na contagem de comparações.

### Testes

```bash
javac -d out $(find src -name "*.java")
java -cp out tests.Etapa3Tests          # roda a partir da pasta raiz do projeto
```

Os testes conferem a ida e volta (compactar e descompactar) de Huffman e LZW em casos de borda
(arquivo vazio, 1 byte, um só símbolo, dados aleatórios que fazem o dicionário do LZW encher e
reiniciar) e no arquivo real, a recusa de arquivos inválidos ou truncados, e KMP e Boyer-Moore
contra um oráculo simples em mais de 2000 combinações de texto e padrão.

## Autoria

Todo o código (parsing do CSV, serialização binária, CRUD sequencial,
ordenação externa, compactação Huffman e LZW, e casamento de padrões KMP e
Boyer-Moore) é autoral, com exceção das classes de E/S de arquivo do
próprio Java (`RandomAccessFile`, `DataInputStream`/`DataOutputStream`),
conforme permitido no enunciado.
