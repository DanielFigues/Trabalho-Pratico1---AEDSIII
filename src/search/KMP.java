package search;

/**
 * Algoritmo de Knuth-Morris-Pratt (KMP).
 *
 * Ideia: quando uma comparação falha no meio de um casamento parcial, o
 * algoritmo NUNCA volta no texto. Ele reaproveita o que já sabe sobre o padrão:
 * se os últimos j bytes lidos casam com o início do padrão e o byte seguinte
 * falha, o próximo prefixo do padrão que ainda pode casar é o maior "borda"
 * (prefixo que também é sufixo) de padrão[0..j-1].
 *
 * Esses tamanhos de borda são calculados uma vez só, na tabela de falhas
 * ("fail"), em O(m). A busca percorre o texto em O(n).
 *
 * Complexidade: O(n + m) no pior caso, independentemente do padrão e do texto.
 */
public class KMP implements PatternMatcher {

    private final byte[] pattern;
    /**
     * fail[i] = tamanho da maior borda (prefixo que também é sufixo) de pattern[0..i].
     * Quando falha o casamento do byte pattern[j], o algoritmo continua com j = fail[j-1].
     */
    private final int[] fail;
    private long comparisons = 0;

    public KMP(byte[] pattern) {
        if (pattern.length == 0) {
            throw new IllegalArgumentException("O padrão não pode ser vazio");
        }
        this.pattern = pattern;
        this.fail = buildFailureTable(pattern);
    }

    /** Pré-processamento: calcula a tabela de falhas comparando o padrão com ele mesmo. */
    private static int[] buildFailureTable(byte[] p) {
        int[] fail = new int[p.length];
        int k = 0; // tamanho da borda atual
        for (int i = 1; i < p.length; i++) {
            // Enquanto o byte novo não estende a borda atual, encurta a borda.
            while (k > 0 && p[i] != p[k]) {
                k = fail[k - 1];
            }
            if (p[i] == p[k]) {
                k++; // a borda cresceu um byte
            }
            fail[i] = k;
        }
        return fail;
    }

    @Override
    public String getName() {
        return "KMP";
    }

    @Override
    public int indexOf(byte[] text, int from) {
        int m = pattern.length;
        int j = 0; // quantos bytes do padrão já casaram com o texto
        for (int i = from; i < text.length; i++) {
            // Tenta estender o casamento atual com text[i]; se falhar, recua j pela tabela (sem recuar i).
            boolean matched;
            while (true) {
                comparisons++;
                if (text[i] == pattern[j]) {
                    matched = true;
                    break;
                }
                if (j == 0) {
                    matched = false; // nem o primeiro byte do padrão casou: segue para o próximo byte do texto
                    break;
                }
                j = fail[j - 1];
            }
            if (matched) {
                j++;
                if (j == m) {
                    return i - m + 1; // casou o padrão inteiro
                }
            }
        }
        return -1;
    }

    @Override
    public long getComparisons() {
        return comparisons;
    }

    @Override
    public void resetComparisons() {
        comparisons = 0;
    }
}
