package file;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser simples de linhas CSV, no estilo RFC4180: respeita campos entre
 * aspas duplas (permitindo vírgulas dentro do campo) e aspas duplas escapadas
 * ("" dentro de um campo entre aspas representa uma aspas literal).
 *
 * Suficiente para o dataset "steam.csv" (Kaggle - Steam Store Games), que não
 * possui quebras de linha dentro de campos entre aspas.
 */
public final class CsvParser {

    private CsvParser() {}

    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++; // pula a segunda aspas (escape)
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
