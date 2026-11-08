package search;

import java.util.ArrayList;
import java.util.List;

/**
 * Contrato dos algoritmos de casamento de padrões (Etapa 3).
 *
 * A busca é feita sobre BYTES (texto em UTF-8). Como o UTF-8 é auto-sincronizável
 * (o início de um caractere nunca se confunde com o meio de outro), procurar os
 * bytes do padrão nos bytes do texto encontra exatamente as mesmas ocorrências que
 * procurar os caracteres, inclusive com acentos. Trabalhar com bytes também permite
 * usar o MESMO código para campos de texto e para o arquivo binário inteiro.
 *
 * Cada objeto é criado para UM padrão: o pré-processamento do padrão (tabelas)
 * acontece no construtor e é reaproveitado em todas as buscas.
 */
public interface PatternMatcher {

    /** Nome do algoritmo (para exibição). */
    String getName();

    /**
     * Procura a primeira ocorrência do padrão em {@code text}, a partir da posição {@code from}.
     *
     * @return posição (índice do primeiro byte) da ocorrência, ou -1 se não houver
     */
    int indexOf(byte[] text, int from);

    /** Quantidade de comparações entre bytes (padrão x texto) realizadas desde a criação ou o último reset. */
    long getComparisons();

    /** Zera o contador de comparações. */
    void resetComparisons();

    /** Posições de TODAS as ocorrências do padrão em {@code text}, inclusive as que se sobrepõem. */
    default List<Integer> findAll(byte[] text) {
        List<Integer> positions = new ArrayList<>();
        int from = 0;
        int pos;
        while ((pos = indexOf(text, from)) != -1) {
            positions.add(pos);
            from = pos + 1; // continua logo após o início da ocorrência (permite sobreposição)
        }
        return positions;
    }
}
