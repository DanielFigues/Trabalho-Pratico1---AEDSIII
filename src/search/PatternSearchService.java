package search;

import file.GameFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Busca de padrões na base de dados usando KMP e Boyer-Moore (Etapa 3).
 *
 * A busca pode ser feita em um CAMPO de texto de cada registro (nome, desenvolvedora
 * ou gêneros) ou no ARQUIVO BINÁRIO INTEIRO (bytes crus). Os dois algoritmos rodam
 * sobre exatamente os mesmos dados e o serviço compara quantas ocorrências cada um
 * achou, quantas comparações de bytes fez e quanto tempo levou.
 *
 * Para a comparação de tempo ser justa, os textos são carregados e convertidos para
 * bytes UMA vez, antes de cronometrar; o tempo medido é só o da busca em si.
 */
public class PatternSearchService {

    /** Onde a busca é realizada. */
    public enum Field {
        NAME("Nome do jogo"),
        DEVELOPER("Desenvolvedora"),
        GENRES("Gêneros"),
        WHOLE_FILE("Arquivo binário inteiro (bytes)");

        public final String label;

        Field(String label) {
            this.label = label;
        }
    }

    /** Quantas ocorrências de exemplo são guardadas para exibição. */
    private static final int SAMPLE_LIMIT = 10;

    /** Uma ocorrência encontrada (usada como exemplo na exibição). */
    public static class Hit {
        public final int gameId;      // id do jogo (ou -1 quando a busca é no arquivo inteiro)
        public final long position;   // posição da ocorrência dentro do campo (ou offset no arquivo)
        public final String context;  // texto do campo (ou trecho do arquivo ao redor da ocorrência)

        Hit(int gameId, long position, String context) {
            this.gameId = gameId;
            this.position = position;
            this.context = context;
        }
    }

    /** Resultado e métricas de um algoritmo. */
    public static class AlgorithmResult {
        public final String algorithm;
        public long occurrences;        // total de ocorrências (sobrepostas contam separadamente)
        public int recordsMatched;      // quantos registros (campos) tiveram ao menos uma ocorrência
        public long comparisons;        // comparações entre bytes do padrão e do texto
        public long nanos;              // tempo da busca
        public final List<Hit> sample = new ArrayList<>();

        AlgorithmResult(String algorithm) {
            this.algorithm = algorithm;
        }
    }

    /** Um texto onde buscar: o campo de um jogo, ou o arquivo inteiro. */
    private static class Target {
        final int gameId;
        final String original;  // texto original, para exibição
        final byte[] bytes;     // bytes (UTF-8) onde a busca é feita

        Target(int gameId, String original, byte[] bytes) {
            this.gameId = gameId;
            this.original = original;
            this.bytes = bytes;
        }
    }

    /**
     * Procura {@code pattern} com KMP e com Boyer-Moore.
     *
     * @param binPath    arquivo binário da base de dados
     * @param field      campo onde buscar (ou o arquivo inteiro)
     * @param pattern    padrão procurado (não vazio)
     * @param ignoreCase true para ignorar maiúsculas/minúsculas (só vale para campos de texto; a busca
     *                   no arquivo inteiro compara bytes exatos)
     * @return um resultado por algoritmo, na ordem KMP, Boyer-Moore
     */
    public static List<AlgorithmResult> search(String binPath, Field field, String pattern, boolean ignoreCase)
            throws IOException {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("O padrão não pode ser vazio");
        }

        boolean fold = ignoreCase && field != Field.WHOLE_FILE;   // "fold" = normalizar para minúsculas
        byte[] patternBytes = (fold ? pattern.toLowerCase(Locale.ROOT) : pattern).getBytes(StandardCharsets.UTF_8);
        List<Target> targets = loadTargets(binPath, field, fold);

        PatternMatcher[] matchers = { new KMP(patternBytes), new BoyerMoore(patternBytes) };
        List<AlgorithmResult> results = new ArrayList<>();

        for (PatternMatcher matcher : matchers) {
            AlgorithmResult r = new AlgorithmResult(matcher.getName());

            // Passada de "aquecimento" (não cronometrada): sem ela, o algoritmo que roda primeiro seria
            // prejudicado pela compilação JIT da JVM e a comparação de tempo ficaria injusta.
            for (Target t : targets) {
                matcher.findAll(t.bytes);
            }
            matcher.resetComparisons();

            long start = System.nanoTime();
            for (Target t : targets) {
                List<Integer> positions = matcher.findAll(t.bytes);
                if (positions.isEmpty()) continue;
                r.occurrences += positions.size();
                r.recordsMatched++;
                // Guarda só as primeiras ocorrências para mostrar ao usuário (fora do que importa para o tempo).
                for (int pos : positions) {
                    if (r.sample.size() >= SAMPLE_LIMIT) break;
                    r.sample.add(new Hit(t.gameId, pos, describe(t, pos, patternBytes.length)));
                }
            }
            r.nanos = System.nanoTime() - start;
            r.comparisons = matcher.getComparisons();
            results.add(r);
        }
        return results;
    }

    /** Lê a base e monta os textos onde a busca será feita. */
    private static List<Target> loadTargets(String binPath, Field field, boolean fold) throws IOException {
        List<Target> targets = new ArrayList<>();

        if (field == Field.WHOLE_FILE) {
            // Arquivo inteiro: um único "texto" com todos os bytes (cabeçalho, lápides, tamanhos e campos).
            byte[] all = Files.readAllBytes(Path.of(binPath));
            targets.add(new Target(-1, null, all));
            return targets;
        }

        try (GameFile gameFile = new GameFile(binPath)) {
            gameFile.forEachValid(g -> {
                String text = switch (field) {
                    case NAME -> g.getName();
                    case DEVELOPER -> g.getDeveloper();
                    default -> String.join(";", g.getGenres());
                };
                if (text == null) text = "";
                String searchable = fold ? text.toLowerCase(Locale.ROOT) : text;
                targets.add(new Target(g.getId(), text, searchable.getBytes(StandardCharsets.UTF_8)));
            });
        }
        return targets;
    }

    /** Texto de exemplo mostrado para uma ocorrência. */
    private static String describe(Target t, int pos, int patternLength) {
        if (t.original != null) {
            return t.original; // campo de um jogo: mostra o valor completo do campo
        }
        // Arquivo inteiro: mostra alguns bytes ao redor da ocorrência, trocando os que não são imprimíveis por '.'.
        int from = Math.max(0, pos - 12);
        int to = Math.min(t.bytes.length, pos + patternLength + 12);
        String snippet = new String(t.bytes, from, to - from, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (char c : snippet.toCharArray()) {
            sb.append(Character.isISOControl(c) || c == '�' ? '.' : c);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Relatório para o usuário
    // ---------------------------------------------------------------

    /** Mostra exemplos de ocorrências e compara os dois algoritmos. */
    public static void printReport(Field field, String pattern, List<AlgorithmResult> results) {
        AlgorithmResult first = results.get(0);
        System.out.println();
        System.out.println("Busca por \"" + pattern + "\" em: " + field.label);

        if (first.occurrences == 0) {
            System.out.println("Nenhuma ocorrência encontrada.");
        } else {
            System.out.println("Ocorrências: " + first.occurrences + " em " + first.recordsMatched
                    + (field == Field.WHOLE_FILE ? " arquivo" : " registro(s)"));
            System.out.println("Primeiras ocorrências:");
            for (Hit h : first.sample) {
                if (h.gameId >= 0) {
                    System.out.println("  #" + h.gameId + " | " + h.context + "  (posição " + h.position + " no campo)");
                } else {
                    System.out.println("  offset " + h.position + " | ..." + h.context + "...");
                }
            }
        }

        // Os dois algoritmos precisam concordar; se não concordarem, há um bug.
        for (AlgorithmResult r : results) {
            if (r.occurrences != first.occurrences) {
                System.out.println("ATENÇÃO: os algoritmos encontraram quantidades diferentes de ocorrências!");
                break;
            }
        }

        System.out.println();
        System.out.println(String.format("%-12s %12s %16s %12s", "Algoritmo", "Ocorrências", "Comparações", "Tempo (ms)"));
        for (AlgorithmResult r : results) {
            System.out.println(String.format(Locale.forLanguageTag("pt-BR"), "%-12s %12d %,16d %12.3f",
                    r.algorithm, r.occurrences, r.comparisons, r.nanos / 1_000_000.0));
        }

        AlgorithmResult fewest = results.get(0);
        AlgorithmResult fastest = results.get(0);
        for (AlgorithmResult r : results) {
            if (r.comparisons < fewest.comparisons) fewest = r;
            if (r.nanos < fastest.nanos) fastest = r;
        }
        System.out.println();
        System.out.println("Menos comparações: " + fewest.algorithm + " | Mais rápido: " + fastest.algorithm);
    }
}
