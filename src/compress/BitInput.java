package compress;

/**
 * Leitor de bits sobre um vetor de bytes (par da classe {@link BitOutput}).
 *
 * Lê os bits na mesma ordem em que foram escritos: do bit mais significativo
 * para o menos significativo de cada byte.
 */
public class BitInput {

    private final byte[] data;
    /** Próximo byte de {@link #data} a ser carregado no acumulador. */
    private int pos;
    /** Acumulador com bits já carregados mas ainda não consumidos. */
    private long acc = 0;
    /** Quantidade de bits válidos dentro de {@link #acc}. */
    private int accBits = 0;

    /**
     * @param data   vetor que contém o fluxo de bits
     * @param offset posição (em bytes) onde o fluxo de bits começa
     */
    public BitInput(byte[] data, int offset) {
        this.data = data;
        this.pos = offset;
    }

    /**
     * Lê {@code nbits} bits (até 32) e os devolve como um inteiro sem sinal.
     *
     * @throws IllegalArgumentException se o fluxo terminar antes do esperado (dados corrompidos)
     */
    public int read(int nbits) {
        // Carrega bytes inteiros até ter bits suficientes.
        while (accBits < nbits) {
            if (pos >= data.length) {
                throw new IllegalArgumentException("Fim inesperado dos dados compactados (arquivo corrompido?)");
            }
            acc = (acc << 8) | (data[pos++] & 0xFF);
            accBits += 8;
        }
        accBits -= nbits;
        int value = (int) ((acc >>> accBits) & ((1L << nbits) - 1)); // extrai os nbits mais antigos
        acc &= (1L << accBits) - 1;                                  // mantém só os bits ainda não lidos
        return value;
    }

    /** Lê um único bit (0 ou 1). */
    public int readBit() {
        return read(1);
    }
}
