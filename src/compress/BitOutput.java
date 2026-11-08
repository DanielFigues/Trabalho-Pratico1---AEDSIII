package compress;

import java.util.Arrays;

/**
 * Escritor de bits em memória.
 *
 * Os algoritmos de compressão geram códigos com tamanhos que não são múltiplos
 * de 8 bits (ex.: códigos de Huffman de 3 bits, códigos do LZW de 9 a 16 bits).
 * Esta classe acumula esses bits e os empacota em bytes, sempre do bit MAIS
 * significativo para o MENOS significativo de cada byte (ordem "big-endian"
 * de bits). Os bits que sobrarem no último byte são preenchidos com zeros.
 */
public class BitOutput {

    /** Bytes já completos. */
    private byte[] buffer = new byte[1 << 16];
    /** Quantidade de bytes completos já guardados em {@link #buffer}. */
    private int size = 0;
    /** Acumulador dos bits ainda não "fechados" em um byte (sempre menos de 8 bits válidos). */
    private long acc = 0;
    /** Quantidade de bits válidos dentro de {@link #acc}. */
    private int accBits = 0;

    /**
     * Escreve os {@code nbits} bits menos significativos de {@code value},
     * começando pelo mais significativo deles.
     *
     * @param value valor cujos bits serão escritos
     * @param nbits quantidade de bits a escrever (0 a 63)
     */
    public void write(long value, int nbits) {
        // O acumulador é um long (64 bits) e pode conter até 7 bits pendentes.
        // Para nunca estourá-lo, códigos longos são escritos em pedaços de até 32 bits.
        while (nbits > 32) {
            nbits -= 32;
            writeChunk(value >>> nbits, 32);
        }
        writeChunk(value, nbits);
    }

    /** Escreve um pedaço de até 32 bits. */
    private void writeChunk(long value, int nbits) {
        if (nbits == 0) return;
        long mask = (1L << nbits) - 1;
        acc = (acc << nbits) | (value & mask);   // empurra os bits novos para a direita do acumulador
        accBits += nbits;
        // Enquanto houver 8 bits ou mais, "fecha" um byte pegando os 8 bits mais antigos.
        while (accBits >= 8) {
            accBits -= 8;
            appendByte((byte) (acc >>> accBits));
        }
        acc &= (1L << accBits) - 1;              // descarta os bits que já viraram byte
    }

    private void appendByte(byte b) {
        if (size == buffer.length) {
            buffer = Arrays.copyOf(buffer, buffer.length * 2); // cresce o vetor dobrando a capacidade
        }
        buffer[size++] = b;
    }

    /**
     * Finaliza a escrita: completa o último byte com zeros (se necessário) e
     * devolve todos os bytes gerados.
     */
    public byte[] toByteArray() {
        if (accBits > 0) {
            // Alinha os bits restantes à esquerda do byte e completa com zeros à direita.
            appendByte((byte) (acc << (8 - accBits)));
            acc = 0;
            accBits = 0;
        }
        return Arrays.copyOf(buffer, size);
    }
}
