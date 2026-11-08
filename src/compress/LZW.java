package compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Compressão LZW (Lempel-Ziv-Welch, codificação por dicionário adaptativo).
 *
 * Ideia: em vez de contar a frequência de cada byte (Huffman), o LZW descobre
 * SEQUÊNCIAS de bytes que se repetem e as troca por um único código do
 * dicionário. O dicionário não é gravado no arquivo: ele é reconstruído
 * identicamente pelo descompressor enquanto lê os códigos.
 *
 * DICIONÁRIO INICIAL (decisão do grupo, conforme o enunciado):
 *   código 0..255 -> as 256 sequências de UM byte (cada valor possível de byte);
 *   código 256    -> CLEAR, código de controle que manda esvaziar o dicionário;
 *   código 257... -> sequências novas aprendidas durante a compressão.
 * Com os 256 bytes no dicionário inicial, qualquer arquivo pode ser compactado
 * (todo byte de entrada já possui um código desde o começo).
 *
 * LARGURA DOS CÓDIGOS: variável. Começa com 9 bits e cresce (10, 11, ... até
 * {@value #MAX_BITS}) conforme o dicionário aumenta, para não desperdiçar bits
 * com códigos que ainda nem existem.
 *
 * DICIONÁRIO CHEIO: quando chega a 2^{@value #MAX_BITS} entradas o compressor
 * emite o código CLEAR e ambos os lados recomeçam do dicionário inicial. Isso
 * permite que o dicionário se readapte quando o conteúdo do arquivo muda.
 *
 * Formato do arquivo compactado:
 *   [4 bytes] assinatura "LZW1"
 *   [8 bytes] long - tamanho original em bytes
 *   [restante] fluxo de bits com os códigos (último byte completado com zeros)
 */
public class LZW implements Compressor {

    private static final byte[] MAGIC = {'L', 'Z', 'W', '1'};

    /** Largura máxima de um código, em bits. */
    private static final int MAX_BITS = 16;
    /** Quantidade máxima de entradas no dicionário (2^16 = 65536). */
    private static final int MAX_CODES = 1 << MAX_BITS;
    /** Largura inicial dos códigos (precisa comportar os códigos 0..256). */
    private static final int MIN_BITS = 9;

    /** Código de controle: "esvazie o dicionário e recomece". */
    private static final int CLEAR = 256;
    /** Primeiro código disponível para sequências aprendidas. */
    private static final int FIRST_CODE = 257;

    /** Tamanho da tabela hash do compressor (potência de 2, bem maior que MAX_CODES para ficar esparsa). */
    private static final int HASH_BITS = MAX_BITS + 1;
    private static final int HASH_SIZE = 1 << HASH_BITS;

    @Override
    public String getName() {
        return "LZW";
    }

    /**
     * Menor largura (em bits, mínimo 9) capaz de representar todos os códigos
     * menores que {@code nextCode}, ou seja, nextCode <= 2^largura.
     *
     * Compressor e descompressor chamam esta função com o MESMO valor de
     * nextCode em cada passo, por isso sempre concordam sobre a largura.
     */
    private static int bitsFor(int nextCode) {
        int bits = 32 - Integer.numberOfLeadingZeros(nextCode - 1); // bits necessários para o maior código possível
        return Math.max(MIN_BITS, bits);
    }

    // ---------------------------------------------------------------
    // Compressão
    // ---------------------------------------------------------------

    @Override
    public byte[] compress(byte[] data) {
        BitOutput bits = new BitOutput();

        if (data.length > 0) {
            // Dicionário do compressor: mapeia (código do prefixo, próximo byte) -> código da sequência.
            // Implementado como tabela hash de endereçamento aberto, sem objetos Integer (mais rápido).
            int[] keys = new int[HASH_SIZE];
            int[] values = new int[HASH_SIZE];
            Arrays.fill(keys, -1); // -1 = posição livre

            int nextCode = FIRST_CODE;    // próximo código livre do dicionário
            int width = MIN_BITS;         // largura atual dos códigos
            int w = data[0] & 0xFF;       // "w" = maior sequência já reconhecida no dicionário (guardada pelo código)

            for (int i = 1; i < data.length; i++) {
                int c = data[i] & 0xFF;
                int key = (w << 8) | c;   // chave = sequência w seguida do byte c
                int slot = hash(key);
                while (keys[slot] != -1 && keys[slot] != key) {
                    slot = (slot + 1) & (HASH_SIZE - 1); // colisão: tenta a próxima posição
                }

                if (keys[slot] == key) {
                    w = values[slot];     // w+c já está no dicionário: continua estendendo a sequência
                } else {
                    // w+c é uma sequência nova: emite o código de w...
                    bits.write(w, width);
                    if (nextCode < MAX_CODES) {
                        // ...e aprende w+c com o próximo código livre.
                        keys[slot] = key;
                        values[slot] = nextCode++;
                    } else {
                        // Dicionário cheio: avisa o descompressor e recomeça do dicionário inicial.
                        bits.write(CLEAR, width);
                        Arrays.fill(keys, -1);
                        nextCode = FIRST_CODE;
                    }
                    width = bitsFor(nextCode); // a largura do PRÓXIMO código depende do tamanho atual do dicionário
                    w = c;                     // recomeça a sequência a partir do byte que não coube
                }
            }
            bits.write(w, width); // emite o código da última sequência pendente
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(out)) {
            dos.write(MAGIC);
            dos.writeLong(data.length);
            dos.write(bits.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Falha ao compactar com LZW", e); // não ocorre: escrita em memória
        }
        return out.toByteArray();
    }

    /** Função de espalhamento (hash multiplicativo) que devolve uma posição da tabela. */
    private static int hash(int key) {
        return (key * 0x9E3779B1) >>> (32 - HASH_BITS);
    }

    // ---------------------------------------------------------------
    // Descompressão
    // ---------------------------------------------------------------

    @Override
    public byte[] decompress(byte[] data) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            byte[] magic = new byte[MAGIC.length];
            dis.readFully(magic);
            if (!Arrays.equals(magic, MAGIC)) {
                throw new IllegalArgumentException("Arquivo não foi gerado pela compressão LZW");
            }
            long originalLength = dis.readLong();
            if (originalLength < 0 || originalLength > Integer.MAX_VALUE - 8) {
                throw new IllegalArgumentException("Cabeçalho LZW inválido");
            }

            byte[] result = new byte[(int) originalLength];
            if (originalLength == 0) {
                return result;
            }

            // Dicionário do descompressor: cada entrada é "sequência do prefixo + 1 byte".
            // Guardamos, para cada código: o código do prefixo, o último byte, o primeiro byte
            // da sequência e o comprimento dela (para escrevê-la direto na posição certa).
            int[] prefix = new int[MAX_CODES];
            byte[] lastByte = new byte[MAX_CODES];
            byte[] firstByte = new byte[MAX_CODES];
            int[] length = new int[MAX_CODES];
            for (int b = 0; b < 256; b++) { // dicionário inicial: os 256 bytes
                prefix[b] = -1;
                lastByte[b] = (byte) b;
                firstByte[b] = (byte) b;
                length[b] = 1;
            }

            BitInput bits = new BitInput(data, MAGIC.length + 8);
            int nextCode = FIRST_CODE; // próximo código livre no dicionário do DESCOMPRESSOR
            // O compressor adiciona cada entrada logo após emitir um código, enquanto o descompressor só
            // consegue adicioná-la ao ler o código SEGUINTE. Por isso o descompressor fica "uma entrada
            // atrás". encNext imita o nextCode do compressor no momento em que o código foi emitido, e só
            // assim as larguras dos códigos coincidem.
            int encNext = FIRST_CODE;
            int prev = -1;             // código lido no passo anterior (-1 = nenhum, logo após um CLEAR)
            int written = 0;           // quantos bytes já foram escritos em result

            while (written < result.length) {
                int code = bits.read(bitsFor(encNext));

                if (code == CLEAR) {   // dicionário foi esvaziado pelo compressor: recomeça igual
                    nextCode = FIRST_CODE;
                    encNext = FIRST_CODE;
                    prev = -1;
                    continue;
                }

                if (prev != -1) {
                    // Aprende a entrada que o compressor criou depois de emitir "prev":
                    //   sequência(prev) + primeiro byte da sequência atual.
                    if (nextCode >= MAX_CODES || code > nextCode) {
                        throw new IllegalArgumentException("Código LZW inválido (arquivo corrompido?)");
                    }
                    // Se code == nextCode, a entrada ainda não existe (caso especial "KwKwK"): a sequência
                    // atual é sequência(prev) + primeiro byte de sequência(prev).
                    byte first = (code < nextCode) ? firstByte[code] : firstByte[prev];
                    prefix[nextCode] = prev;
                    lastByte[nextCode] = first;
                    firstByte[nextCode] = firstByte[prev];
                    length[nextCode] = length[prev] + 1;
                    nextCode++;
                } else if (code >= 256) {
                    // Logo após um CLEAR (ou no início) só podem aparecer bytes simples.
                    throw new IllegalArgumentException("Código LZW inválido (arquivo corrompido?)");
                }

                // Escreve a sequência do código atual de trás para frente, subindo pela cadeia de prefixos.
                int len = length[code];
                if (written + len > result.length) {
                    throw new IllegalArgumentException("Dados LZW excedem o tamanho original (arquivo corrompido?)");
                }
                int idx = written + len - 1;
                for (int c = code; c != -1; c = prefix[c]) {
                    result[idx--] = lastByte[c];
                }
                written += len;

                prev = code;
                if (encNext < MAX_CODES) {
                    encNext++; // o compressor também adicionou uma entrada após emitir este código
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo LZW truncado ou corrompido", e);
        }
    }
}
