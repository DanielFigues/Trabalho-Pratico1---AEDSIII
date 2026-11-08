package compress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.PriorityQueue;

/**
 * Compressão de Huffman (codificação estatística, por BYTE).
 *
 * Ideia: bytes que aparecem muito no arquivo recebem códigos curtos e bytes
 * raros recebem códigos longos. Para isso:
 *   1) conta-se a frequência de cada um dos 256 valores possíveis de byte;
 *   2) constrói-se a árvore de Huffman juntando sempre os dois nós de menor
 *      frequência até restar uma única raiz;
 *   3) o código de cada byte é o caminho da raiz até a folha dele
 *      (esquerda = bit 0, direita = bit 1);
 *   4) o arquivo é reescrito trocando cada byte pelo seu código.
 *
 * Formato do arquivo compactado:
 *   [4 bytes]  assinatura "HUF1"
 *   [8 bytes]  long - tamanho original em bytes
 *   [4 bytes]  int  - quantidade N de símbolos distintos
 *   N x { [1 byte] símbolo, [8 bytes] long frequência }   (tabela de frequências)
 *   [restante] fluxo de bits com os códigos de Huffman (último byte completado com zeros)
 *
 * O descompressor lê a tabela de frequências e reconstrói A MESMA árvore.
 * Isso só funciona porque a construção é determinística: os empates de
 * frequência são desfeitos pelo identificador do nó (veja {@link Node#compareTo}).
 */
public class Huffman implements Compressor {

    private static final byte[] MAGIC = {'H', 'U', 'F', '1'};

    /** Nó da árvore de Huffman. Folhas guardam um símbolo; nós internos guardam dois filhos. */
    private static class Node implements Comparable<Node> {
        final int id;       // desempate determinístico: folhas = valor do byte (0..255), internos = 256, 257, ...
        final long freq;    // soma das frequências de todas as folhas abaixo deste nó
        final int symbol;   // byte representado (só faz sentido em folhas)
        final Node left;    // filho do bit 0 (null em folhas)
        final Node right;   // filho do bit 1 (null em folhas)

        Node(int id, long freq, int symbol, Node left, Node right) {
            this.id = id;
            this.freq = freq;
            this.symbol = symbol;
            this.left = left;
            this.right = right;
        }

        boolean isLeaf() {
            return left == null;
        }

        /** Menor frequência primeiro; em caso de empate, menor id primeiro (ordem total e estável). */
        @Override
        public int compareTo(Node o) {
            int c = Long.compare(freq, o.freq);
            return c != 0 ? c : Integer.compare(id, o.id);
        }
    }

    @Override
    public String getName() {
        return "Huffman";
    }

    // ---------------------------------------------------------------
    // Compressão
    // ---------------------------------------------------------------

    @Override
    public byte[] compress(byte[] data) {
        // Passo 1: tabela de frequências dos 256 valores possíveis de byte.
        long[] freq = new long[256];
        for (byte b : data) {
            freq[b & 0xFF]++;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 2 + 4096);
        try (DataOutputStream dos = new DataOutputStream(out)) {
            // Cabeçalho: assinatura, tamanho original e tabela de frequências.
            dos.write(MAGIC);
            dos.writeLong(data.length);
            int distinct = 0;
            for (long f : freq) if (f > 0) distinct++;
            dos.writeInt(distinct);
            for (int s = 0; s < 256; s++) {
                if (freq[s] > 0) {
                    dos.writeByte(s);
                    dos.writeLong(freq[s]);
                }
            }

            if (data.length > 0) {
                // Passo 2: árvore e Passo 3: código de cada símbolo.
                Node root = buildTree(freq);
                long[] code = new long[256];
                int[] length = new int[256];
                assignCodes(root, 0L, 0, code, length);

                // Caso especial: só existe UM símbolo distinto. A árvore é uma única folha
                // (código de 0 bits). Nada é gravado no fluxo: o descompressor repete o
                // símbolo "tamanho original" vezes.
                if (!root.isLeaf()) {
                    // Passo 4: troca cada byte pelo seu código de bits.
                    BitOutput bits = new BitOutput();
                    for (byte b : data) {
                        int s = b & 0xFF;
                        bits.write(code[s], length[s]);
                    }
                    dos.write(bits.toByteArray());
                }
            }
        } catch (IOException e) {
            // Não deve ocorrer: estamos escrevendo em memória.
            throw new RuntimeException("Falha ao compactar com Huffman", e);
        }
        return out.toByteArray();
    }

    /** Constrói a árvore de Huffman a partir da tabela de frequências (ao menos 1 símbolo com frequência > 0). */
    private static Node buildTree(long[] freq) {
        PriorityQueue<Node> queue = new PriorityQueue<>();
        for (int s = 0; s < 256; s++) {
            if (freq[s] > 0) {
                queue.add(new Node(s, freq[s], s, null, null)); // uma folha por símbolo presente
            }
        }
        int nextId = 256; // ids dos nós internos continuam depois dos ids das folhas
        while (queue.size() > 1) {
            Node a = queue.poll(); // os dois nós de menor frequência...
            Node b = queue.poll();
            queue.add(new Node(nextId++, a.freq + b.freq, -1, a, b)); // ...viram filhos de um novo nó
        }
        return queue.poll();
    }

    /** Percorre a árvore guardando, para cada folha, o código (bits) e o comprimento dele. */
    private static void assignCodes(Node node, long bits, int depth, long[] code, int[] length) {
        if (node.isLeaf()) {
            code[node.symbol] = bits;
            length[node.symbol] = depth;
            return;
        }
        if (depth >= 62) {
            // Impossível na prática (exigiria um arquivo de bilhões de bytes), mas protege o long.
            throw new IllegalStateException("Árvore de Huffman profunda demais");
        }
        assignCodes(node.left, bits << 1, depth + 1, code, length);        // ramo esquerdo: acrescenta bit 0
        assignCodes(node.right, (bits << 1) | 1, depth + 1, code, length); // ramo direito: acrescenta bit 1
    }

    // ---------------------------------------------------------------
    // Descompressão
    // ---------------------------------------------------------------

    @Override
    public byte[] decompress(byte[] data) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

            // Confere a assinatura para recusar arquivos que não foram gerados por este algoritmo.
            byte[] magic = new byte[MAGIC.length];
            dis.readFully(magic);
            if (!java.util.Arrays.equals(magic, MAGIC)) {
                throw new IllegalArgumentException("Arquivo não foi gerado pela compressão Huffman");
            }

            long originalLength = dis.readLong();
            int distinct = dis.readInt();
            if (originalLength < 0 || originalLength > Integer.MAX_VALUE - 8 || distinct < 0 || distinct > 256) {
                throw new IllegalArgumentException("Cabeçalho Huffman inválido");
            }

            // Lê a tabela de frequências e confere se ela soma o tamanho original.
            long[] freq = new long[256];
            long total = 0;
            for (int i = 0; i < distinct; i++) {
                int symbol = dis.readUnsignedByte();
                long f = dis.readLong();
                if (f <= 0) throw new IllegalArgumentException("Tabela de frequências inválida");
                freq[symbol] = f;
                total += f;
            }
            if (total != originalLength) {
                throw new IllegalArgumentException("Tabela de frequências inconsistente com o tamanho original");
            }

            byte[] result = new byte[(int) originalLength];
            if (originalLength == 0) {
                return result;
            }

            Node root = buildTree(freq); // reconstrói exatamente a mesma árvore da compressão

            if (root.isLeaf()) {
                // Só um símbolo distinto: basta repetir o símbolo.
                java.util.Arrays.fill(result, (byte) root.symbol);
                return result;
            }

            // Lê bit a bit descendo na árvore: bit 0 = esquerda, bit 1 = direita.
            // Ao chegar em uma folha, emite o símbolo e volta para a raiz.
            int headerSize = MAGIC.length + 8 + 4 + distinct * 9;
            BitInput bits = new BitInput(data, headerSize);
            Node node = root;
            int written = 0;
            while (written < result.length) {
                node = (bits.readBit() == 0) ? node.left : node.right;
                if (node.isLeaf()) {
                    result[written++] = (byte) node.symbol;
                    node = root;
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Arquivo Huffman truncado ou corrompido", e);
        }
    }
}
