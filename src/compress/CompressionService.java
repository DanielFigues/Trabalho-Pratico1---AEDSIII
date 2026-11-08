package compress;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orquestra a compressão e a descompressão da base de dados (Etapa 3).
 *
 * Responsabilidades:
 *   - aplicar OS DOIS algoritmos (Huffman e LZW) ao arquivo de dados inteiro;
 *   - gravar os arquivos compactados com o nome exigido no enunciado:
 *         nomeArquivo + NomeAlgoritmo + "Compressao" + X          (ex.: steamHuffmanCompressao1.bin)
 *     em que X é a versão da compressão (cada nova compressão incrementa X);
 *   - medir tempo e ganho/perda de cada algoritmo e compará-los;
 *   - descompactar uma versão X e SUBSTITUIR o arquivo de dados pelo resultado.
 *
 * Os tempos medidos consideram apenas a execução do algoritmo sobre os bytes
 * em memória (sem o tempo de ler/gravar os arquivos), para a comparação entre
 * Huffman e LZW ser justa.
 */
public class CompressionService {

    /** Resultado (e métricas) da execução de um algoritmo. */
    public static class Result {
        public final String algorithm;  // nome do algoritmo
        public final File file;         // arquivo compactado (na compressão) ou usado como entrada (na descompressão)
        public final long inputSize;    // bytes de entrada do algoritmo
        public final long outputSize;   // bytes produzidos pelo algoritmo
        public final long nanos;        // tempo de execução em nanossegundos

        Result(String algorithm, File file, long inputSize, long outputSize, long nanos) {
            this.algorithm = algorithm;
            this.file = file;
            this.inputSize = inputSize;
            this.outputSize = outputSize;
            this.nanos = nanos;
        }

        /** Porcentagem de ganho (positiva) ou perda (negativa) de espaço em relação à entrada. */
        public double gainPercent() {
            return inputSize == 0 ? 0.0 : (1.0 - (double) outputSize / inputSize) * 100.0;
        }

        public double millis() {
            return nanos / 1_000_000.0;
        }
    }

    /** Algoritmos oferecidos, na ordem em que são executados. */
    private final List<Compressor> algorithms = List.of(new Huffman(), new LZW());

    // ---------------------------------------------------------------
    // Nomes de arquivo e versões
    // ---------------------------------------------------------------

    /** Nome original do arquivo sem a extensão (ex.: "steam.bin" -> "steam"). */
    private static String baseName(File source) {
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Monta o arquivo "nomeArquivoNomeAlgoritmoCompressaoX" na mesma pasta do arquivo de dados. */
    private static File compressedFile(File source, String algorithm, int version) {
        return new File(source.getAbsoluteFile().getParentFile(),
                baseName(source) + algorithm + "Compressao" + version + ".bin");
    }

    /** Lista as versões de compressão já existentes na pasta (qualquer algoritmo), em ordem crescente. */
    public List<Integer> availableVersions(File source) {
        StringBuilder alternatives = new StringBuilder();
        for (Compressor c : algorithms) {
            if (alternatives.length() > 0) alternatives.append('|');
            alternatives.append(Pattern.quote(c.getName()));
        }
        Pattern pattern = Pattern.compile("^" + Pattern.quote(baseName(source))
                + "(?:" + alternatives + ")Compressao(\\d+)\\.bin$");

        TreeSet<Integer> versions = new TreeSet<>();
        File[] files = source.getAbsoluteFile().getParentFile().listFiles();
        if (files != null) {
            for (File f : files) {
                Matcher m = pattern.matcher(f.getName());
                if (m.matches()) {
                    versions.add(Integer.parseInt(m.group(1)));
                }
            }
        }
        return new ArrayList<>(versions);
    }

    /** Próxima versão livre: 1 + maior versão já existente (ou 1 se ainda não há nenhuma). */
    private int nextVersion(File source) {
        List<Integer> existing = availableVersions(source);
        return existing.isEmpty() ? 1 : existing.get(existing.size() - 1) + 1;
    }

    // ---------------------------------------------------------------
    // Compressão
    // ---------------------------------------------------------------

    /**
     * Compacta o arquivo de dados com todos os algoritmos, gerando uma nova versão X.
     * O arquivo original NÃO é alterado.
     *
     * @return um resultado por algoritmo (na mesma ordem de execução)
     */
    public List<Result> compress(File source) throws IOException {
        byte[] original = Files.readAllBytes(source.toPath()); // arquivo inteiro: cabeçalho, lápides, tamanhos e campos
        int version = nextVersion(source);
        List<Result> results = new ArrayList<>();

        for (Compressor algorithm : algorithms) {
            long start = System.nanoTime();
            byte[] packed = algorithm.compress(original);
            long elapsed = System.nanoTime() - start;

            // Garantia de integridade (fora da medição de tempo): descompacta em memória e compara
            // com o original. Se algo estiver errado, nenhum arquivo compactado é gravado.
            byte[] check = algorithm.decompress(packed);
            if (!Arrays.equals(original, check)) {
                throw new IllegalStateException("Falha de integridade: a descompressão " + algorithm.getName()
                        + " não reproduziu o arquivo original");
            }

            File out = compressedFile(source, algorithm.getName(), version);
            Files.write(out.toPath(), packed);
            results.add(new Result(algorithm.getName(), out, original.length, packed.length, elapsed));
        }
        return results;
    }

    // ---------------------------------------------------------------
    // Descompressão
    // ---------------------------------------------------------------

    /**
     * Descompacta a versão {@code version} e substitui o arquivo de dados pelo resultado.
     * Cada algoritmo cuja versão exista é executado (e cronometrado); os resultados precisam ser
     * idênticos entre si. O arquivo de dados só é substituído depois que tudo foi conferido.
     *
     * IMPORTANTE: quem chama deve fechar o arquivo de dados antes (necessário em alguns sistemas
     * operacionais para poder sobrescrevê-lo).
     *
     * @return um resultado por algoritmo executado
     * @throws FileNotFoundException se não existir nenhum arquivo compactado dessa versão
     */
    public List<Result> decompress(File dataFile, int version) throws IOException {
        List<Result> results = new ArrayList<>();
        byte[] restored = null; // conteúdo descompactado (deve ser igual para todos os algoritmos)

        for (Compressor algorithm : algorithms) {
            File in = compressedFile(dataFile, algorithm.getName(), version);
            if (!in.exists()) {
                continue; // essa versão não foi gerada por este algoritmo
            }
            byte[] packed = Files.readAllBytes(in.toPath());

            long start = System.nanoTime();
            byte[] data = algorithm.decompress(packed);
            long elapsed = System.nanoTime() - start;

            if (restored == null) {
                restored = data;
            } else if (!Arrays.equals(restored, data)) {
                throw new IllegalStateException("Os algoritmos produziram resultados diferentes para a versão "
                        + version + "; o arquivo de dados não foi alterado");
            }
            results.add(new Result(algorithm.getName(), in, packed.length, data.length, elapsed));
        }

        if (restored == null) {
            throw new FileNotFoundException("Nenhum arquivo compactado da versão " + version + " foi encontrado em "
                    + dataFile.getAbsoluteFile().getParent());
        }

        // Substituição segura: grava em um arquivo temporário e só então troca pelo arquivo de dados.
        File tmp = new File(dataFile.getAbsoluteFile().getParentFile(), dataFile.getName() + ".tmp");
        Files.write(tmp.toPath(), restored);
        try {
            Files.move(tmp.toPath(), dataFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), dataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return results;
    }

    // ---------------------------------------------------------------
    // Relatórios para o usuário
    // ---------------------------------------------------------------

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static String fmt(String format, Object... args) {
        return String.format(PT_BR, format, args);
    }

    /** Mostra, para cada algoritmo, o arquivo gerado, o ganho/perda e o tempo, e diz qual foi melhor. */
    public static void printCompressionReport(List<Result> results) {
        System.out.println();
        for (Result r : results) {
            double gain = r.gainPercent();
            System.out.println(fmt("[%s] %s", r.algorithm, r.file.getName()));
            System.out.println(fmt("    tamanho: %,d -> %,d bytes", r.inputSize, r.outputSize));
            System.out.println(fmt("    %s: %.2f%%", gain >= 0 ? "ganho de espaço" : "perda de espaço (arquivo maior)",
                    Math.abs(gain)));
            System.out.println(fmt("    tempo de compressão: %.2f ms", r.millis()));
        }

        Result smallest = results.get(0);
        Result fastest = results.get(0);
        for (Result r : results) {
            if (r.outputSize < smallest.outputSize) smallest = r;
            if (r.nanos < fastest.nanos) fastest = r;
        }
        System.out.println();
        System.out.println("Comparação:");
        System.out.println("  Menor arquivo (melhor taxa de compressão): " + winner(results, true));
        System.out.println("  Mais rápido                              : " + winner(results, false));
        if (smallest == fastest) {
            System.out.println("  => " + smallest.algorithm + " foi melhor nos dois critérios para esta base.");
        } else {
            System.out.println("  => Resultado dividido: " + smallest.algorithm + " compacta mais, "
                    + fastest.algorithm + " é mais rápido.");
        }
    }

    /** Mostra o tempo de cada descompressão e qual algoritmo foi mais rápido. */
    public static void printDecompressionReport(List<Result> results) {
        System.out.println();
        for (Result r : results) {
            System.out.println(fmt("[%s] %s", r.algorithm, r.file.getName()));
            System.out.println(fmt("    tamanho: %,d -> %,d bytes", r.inputSize, r.outputSize));
            System.out.println(fmt("    tempo de descompressão: %.2f ms", r.millis()));
        }
        System.out.println();
        if (results.size() > 1) {
            System.out.println("Comparação (o resultado final é idêntico nos dois algoritmos):");
            System.out.println("  Mais rápido na descompressão: " + winner(results, false));
        } else {
            System.out.println("Só um algoritmo possui essa versão; não há comparação a fazer.");
        }
    }

    /** Texto com o vencedor pelo tamanho ({@code bySize}) ou pelo tempo, tratando empate. */
    private static String winner(List<Result> results, boolean bySize) {
        Result best = results.get(0);
        for (Result r : results) {
            if (bySize ? r.outputSize < best.outputSize : r.nanos < best.nanos) best = r;
        }
        for (Result r : results) {
            if (r != best && (bySize ? r.outputSize == best.outputSize : r.nanos == best.nanos)) {
                return "empate";
            }
        }
        return best.algorithm;
    }
}
