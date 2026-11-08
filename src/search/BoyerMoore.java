package search;

import java.util.Arrays;

/**
 * Algoritmo de Boyer-Moore (com as duas heurísticas: caractere ruim e sufixo bom).
 *
 * Ideia: o padrão é alinhado com o texto, mas comparado da DIREITA para a
 * ESQUERDA. Quando ocorre uma divergência, as duas heurísticas calculam o
 * quanto o padrão pode ser deslocado para a direita sem perder nenhuma
 * ocorrência, e vale o MAIOR dos dois deslocamentos:
 *
 *   - Caractere ruim (bad character): o byte do texto que causou a divergência
 *     é alinhado com a última ocorrência dele no padrão (ou o padrão pula por
 *     inteiro, se esse byte nem existe no padrão).
 *   - Sufixo bom (good suffix): se um sufixo do padrão já casou, o padrão é
 *     deslocado até que outra ocorrência desse sufixo (ou um prefixo do padrão
 *     que seja igual a parte dele) fique alinhada com o trecho já casado.
 *
 * Em textos grandes, com padrões longos e muitos bytes diferentes, é comum o
 * algoritmo olhar bem menos que n bytes (busca "sublinear"), pois a maioria
 * dos deslocamentos pula vários bytes de uma vez.
 *
 * Complexidade: pré-processamento O(m + 256); busca O(n/m) no melhor caso. No
 * pior caso (textos e padrões muito repetitivos, ex.: "aaaa...") esta versão
 * simples pode chegar a O(n*m), ao contrário do KMP, que é sempre O(n + m).
 */
public class BoyerMoore implements PatternMatcher {

    private final byte[] pattern;
    /** badChar[b] = deslocamento sugerido pelo caractere ruim quando o byte b do texto diverge. */
    private final int[] badChar;
    /** goodSuffix[i] = deslocamento sugerido pelo sufixo bom quando a divergência ocorre na posição i do padrão. */
    private final int[] goodSuffix;
    private long comparisons = 0;

    public BoyerMoore(byte[] pattern) {
        if (pattern.length == 0) {
            throw new IllegalArgumentException("O padrão não pode ser vazio");
        }
        this.pattern = pattern;
        this.badChar = buildBadCharTable(pattern);
        this.goodSuffix = buildGoodSuffixTable(pattern);
    }

    /**
     * Tabela do caractere ruim. Para um byte b que aparece no padrão (exceto na última posição),
     * guarda a distância entre a última ocorrência dele e o fim do padrão; para os demais, m.
     */
    private static int[] buildBadCharTable(byte[] p) {
        int m = p.length;
        int[] table = new int[256];
        Arrays.fill(table, m); // byte ausente do padrão: o padrão inteiro pode pular
        for (int i = 0; i < m - 1; i++) {
            table[p[i] & 0xFF] = m - 1 - i;
        }
        return table;
    }

    /**
     * Tabela do sufixo bom. Primeiro calcula suffixes[i] = tamanho do maior sufixo do padrão que
     * termina na posição i E também é sufixo do padrão inteiro; depois deriva os deslocamentos.
     */
    private static int[] buildGoodSuffixTable(byte[] p) {
        int m = p.length;
        int[] suffixes = new int[m];
        suffixes[m - 1] = m;
        int g = m - 1;
        int f = 0;
        for (int i = m - 2; i >= 0; i--) {
            if (i > g && suffixes[i + m - 1 - f] < i - g) {
                suffixes[i] = suffixes[i + m - 1 - f]; // reaproveita um valor já calculado dentro da janela [g, f]
            } else {
                if (i < g) g = i;
                f = i;
                while (g >= 0 && p[g] == p[g + m - 1 - f]) {
                    g--; // estende o casamento do sufixo para a esquerda
                }
                suffixes[i] = f - g;
            }
        }

        int[] shift = new int[m];
        Arrays.fill(shift, m); // sem nenhuma informação útil, desloca o padrão inteiro

        // Caso 1: o padrão tem um PREFIXO igual a um sufixo dele (suffixes[i] == i+1).
        // Se a divergência ocorre à esquerda desse prefixo, o padrão pode ser alinhado por ele.
        int j = 0;
        for (int i = m - 1; i >= 0; i--) {
            if (suffixes[i] == i + 1) {
                for (; j < m - 1 - i; j++) {
                    if (shift[j] == m) {
                        shift[j] = m - 1 - i;
                    }
                }
            }
        }
        // Caso 2: o sufixo que casou reaparece mais à esquerda dentro do padrão; alinha com essa ocorrência.
        for (int i = 0; i <= m - 2; i++) {
            shift[m - 1 - suffixes[i]] = m - 1 - i;
        }
        return shift;
    }

    @Override
    public String getName() {
        return "Boyer-Moore";
    }

    @Override
    public int indexOf(byte[] text, int from) {
        int m = pattern.length;
        int n = text.length;
        int pos = from; // alinhamento atual: o padrão começa em text[pos]
        while (pos <= n - m) {
            // Compara da direita para a esquerda enquanto os bytes forem iguais.
            int i = m - 1;
            while (i >= 0) {
                comparisons++;
                if (pattern[i] != text[pos + i]) break;
                i--;
            }
            if (i < 0) {
                return pos; // casou o padrão inteiro
            }
            // Divergência na posição i: desloca pelo maior entre as duas heurísticas.
            int byBadChar = badChar[text[pos + i] & 0xFF] - m + 1 + i;
            pos += Math.max(goodSuffix[i], byBadChar);
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
