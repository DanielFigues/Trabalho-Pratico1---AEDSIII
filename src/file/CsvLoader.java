package file;

import model.Game;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Responsável pela "Carga da base de dados": lê o arquivo steam.csv
 * (dataset público "Steam Store Games", disponível no Kaggle) e grava cada
 * linha como um registro no arquivo binário sequencial (GameFile).
 *
 * Colunas do CSV (na ordem):
 * appid,name,release_date,english,developer,publisher,platforms,
 * required_age,categories,genres,steamspy_tags,achievements,
 * positive_ratings,negative_ratings,average_playtime,median_playtime,
 * owners,price
 */
public class CsvLoader {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Realiza a carga do CSV para o arquivo binário.
     * @return quantidade de registros carregados com sucesso
     */
    public static int load(String csvPath, GameFile gameFile) throws IOException {
        int loaded = 0;
        int skipped = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(csvPath, java.nio.charset.StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine(); // descarta cabeçalho
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> f = CsvParser.parseLine(line);
                if (f.size() < 18) {
                    skipped++;
                    continue;
                }
                try {
                    Game game = toGame(f);
                    gameFile.create(game);
                    loaded++;
                    if (loaded % 5000 == 0) {
                        System.out.println("  ... " + loaded + " registros carregados");
                    }
                } catch (Exception ex) {
                    skipped++;
                }
            }
        }
        System.out.println("Carga concluída: " + loaded + " registros carregados, " + skipped + " ignorados.");
        return loaded;
    }

    private static Game toGame(List<String> f) {
        int appid = Integer.parseInt(f.get(0).trim());
        String name = f.get(1);
        LocalDate releaseDate;
        try {
            releaseDate = LocalDate.parse(f.get(2).trim(), DATE_FMT);
        } catch (Exception e) {
            releaseDate = LocalDate.of(1970, 1, 1);
        }
        String developer = f.get(4);
        List<String> genres = new ArrayList<>(Arrays.asList(f.get(9).split(";")));
        int positiveRatings = parseIntSafe(f.get(12));
        int negativeRatings = parseIntSafe(f.get(13));
        float price = parseFloatSafe(f.get(17));

        return new Game(appid, name, developer, releaseDate, genres, price, positiveRatings, negativeRatings);
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private static float parseFloatSafe(String s) {
        try { return Float.parseFloat(s.trim()); } catch (Exception e) { return 0f; }
    }
}
