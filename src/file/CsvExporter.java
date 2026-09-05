package file;

import model.Game;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Exporta o conteúdo de um arquivo binário (GameFile) de volta para um
 * arquivo CSV, contendo apenas os campos que fazem parte da entidade Game
 * (id/appid, name, developer, release_date, genres, price, positive_ratings,
 * negative_ratings). Útil para conferir visualmente o resultado do CRUD e
 * da Ordenação Externa em uma planilha comum.
 */
public class CsvExporter {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String[] HEADER = {
            "appid", "name", "developer", "release_date", "genres",
            "price", "positive_ratings", "negative_ratings"
    };

    /** @return quantidade de registros exportados */
    public static int export(GameFile gameFile, String csvPath) throws IOException {
        AtomicInteger count = new AtomicInteger();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(csvPath, java.nio.charset.StandardCharsets.UTF_8))) {
            writer.write(String.join(",", HEADER));
            writer.newLine();

            gameFile.forEachValid(g -> {
                try {
                    writer.write(toCsvLine(g));
                    writer.newLine();
                    count.incrementAndGet();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        return count.get();
    }

    private static String toCsvLine(Game g) {
        String[] campos = {
                String.valueOf(g.getId()),
                g.getName(),
                g.getDeveloper(),
                g.getReleaseDate() != null ? g.getReleaseDate().format(DATE_FMT) : "",
                String.join(";", g.getGenres()),
                String.valueOf(g.getPrice()),
                String.valueOf(g.getPositiveRatings()),
                String.valueOf(g.getNegativeRatings())
        };
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsv(campos[i]));
        }
        return sb.toString();
    }

    /** Coloca entre aspas (e escapa aspas internas) campos que contenham vírgula, aspas ou quebra de linha. */
    private static String escapeCsv(String valor) {
        if (valor == null) return "";
        boolean precisaAspas = valor.contains(",") || valor.contains("\"")
                || valor.contains("\n") || valor.contains("\r");
        if (!precisaAspas) return valor;
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }
}
