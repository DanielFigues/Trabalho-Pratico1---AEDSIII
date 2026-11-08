package compress;

/**
 * Contrato comum dos algoritmos de compressão de dados (Etapa 3).
 *
 * Os algoritmos trabalham sobre o conteúdo BRUTO do arquivo binário (vetor de
 * bytes), sem conhecer a entidade Game. Assim, TODOS os bytes do arquivo são
 * compactados - cabeçalho (último id), lápides, indicadores de tamanho dos
 * registros, strings de tamanho fixo/variável e demais campos - exatamente
 * como pede o enunciado. A descompressão devolve o arquivo byte a byte
 * idêntico ao original (compressão SEM perdas).
 */
public interface Compressor {

    /** Nome do algoritmo; também é usado na composição do nome dos arquivos gerados. */
    String getName();

    /** Compacta o vetor de bytes recebido e devolve o vetor compactado (com cabeçalho próprio). */
    byte[] compress(byte[] data);

    /**
     * Reconstrói o vetor original a partir de um vetor gerado por {@link #compress(byte[])}.
     *
     * @throws IllegalArgumentException se os dados estiverem corrompidos ou não
     *                                  tiverem sido gerados por este algoritmo
     */
    byte[] decompress(byte[] data);
}
