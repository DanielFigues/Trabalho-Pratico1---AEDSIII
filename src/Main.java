import file.CsvLoader;
import file.GameFile;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import model.Game;
import sort.ExternalSort;

/**
 * Trabalho Prático I - Algoritmos e Estruturas de Dados III (PUC Minas)
 * Tema escolhido: base de dados de jogos da Steam (dataset "Steam Store Games").
 *
 * Menu principal (uso pelo terminal), conforme especificado no enunciado:
 *   1) Carga da base de dados (CSV -> arquivo binário)
 *   2) Ler um registro por id
 *   3) Atualizar um registro
 *   4) Deletar um registro
 *   5) Ordenação Externa (intercalação balanceada, com nº de caminhos e
 *      nº máximo de registros em memória primária)
 */
public class Main {

    private static final String CSV_PATH = "data/steam.csv";
    private static final String BIN_PATH = "data/steam.bin";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) throws IOException {
        new File("data").mkdirs();
 
        System.out.println(" TP1 - AED III - CRUD Sequencial de Jogos da Steam"); 

        boolean sair = false;
        while (!sair) {
            try (GameFile gameFile = new GameFile(BIN_PATH)) {
                exibirMenu();
                String opcao = sc.nextLine().trim();
                switch (opcao) {
                    case "1" -> carregarBase(gameFile);
                    case "2" -> lerRegistro(gameFile);
                    case "3" -> atualizarRegistro(gameFile);
                    case "4" -> deletarRegistro(gameFile);
                    case "5" -> ordenacaoExterna(gameFile);
                    case "6" -> estatisticas(gameFile);
                    case "7" -> listarAmostra(gameFile);
                    case "0" -> sair = true;
                    default -> System.out.println("Opção inválida.");
                }
            }
        }
        System.out.println("Encerrando. Até mais!");
    }

    private static void exibirMenu() {
        System.out.println();
        System.out.println("---------------------------------------------------------");
        System.out.println("1 - Carregar base de dados (CSV -> binário)");
        System.out.println("2 - Ler um registro (id)");
        System.out.println("3 - Atualizar um registro (id)");
        System.out.println("4 - Deletar um registro (id)");
        System.out.println("5 - Ordenação Externa");
        System.out.println("6 - Estatísticas do arquivo");
        System.out.println("7 - Listar amostra de registros");
        System.out.println("0 - Sair");
        System.out.print("Escolha uma opção: ");
    }

    // ---------------------------------------------------------------

    private static void carregarBase(GameFile gameFile) throws IOException {
        File csv = new File(CSV_PATH);
        if (!csv.exists()) {
            System.out.println("Arquivo CSV não encontrado em: " + csv.getAbsolutePath());
            System.out.print("Informe o caminho do CSV: ");
            String path = sc.nextLine().trim();
            csv = new File(path);
            if (!csv.exists()) {
                System.out.println("Arquivo não encontrado. Operação cancelada.");
                return;
            }
        }
        System.out.println("Carregando " + csv.getPath() + " ...");
        long inicio = System.currentTimeMillis();
        CsvLoader.load(csv.getPath(), gameFile);
        long fim = System.currentTimeMillis();
        System.out.println("Tempo de carga: " + (fim - inicio) + " ms");
    }

    private static void lerRegistro(GameFile gameFile) throws IOException {
        int id = lerInt("Informe o id (appid) do jogo: ");
        Game g = gameFile.read(id);
        if (g == null) {
            System.out.println("Registro não encontrado (id=" + id + ").");
        } else {
            System.out.println("Encontrado:");
            System.out.println("  " + g);
        }
    }

    private static void atualizarRegistro(GameFile gameFile) throws IOException {
        int id = lerInt("Informe o id (appid) do jogo a atualizar: ");
        Game atual = gameFile.read(id);
        if (atual == null) {
            System.out.println("Registro não encontrado (id=" + id + "). Nada a atualizar.");
            return;
        }
        System.out.println("Registro atual: " + atual);
        System.out.println("Informe os novos valores (ENTER para manter o valor atual).");

        String name = lerStringOpcional("Nome [" + atual.getName() + "]: ", atual.getName());
        String developer = lerStringOpcional("Desenvolvedora [" + atual.getDeveloper() + "]: ", atual.getDeveloper());

        String dataStr = lerStringOpcional(
                "Data de lançamento (aaaa-mm-dd) [" + atual.getReleaseDate() + "]: ", null);
        LocalDate releaseDate = atual.getReleaseDate();
        if (dataStr != null) {
            try {
                releaseDate = LocalDate.parse(dataStr, DATE_FMT);
            } catch (Exception e) {
                System.out.println("Data inválida, mantendo a anterior.");
            }
        }

        String genresStr = lerStringOpcional(
                "Gêneros separados por ';' [" + String.join(";", atual.getGenres()) + "]: ", null);
        List<String> genres = atual.getGenres();
        if (genresStr != null) {
            genres = Arrays.asList(genresStr.split(";"));
        }

        Float price = lerFloatOpcional("Preço [" + atual.getPrice() + "]: ");
        float finalPrice = price != null ? price : atual.getPrice();

        Game atualizado = new Game(id, name, developer, releaseDate, genres,
                finalPrice, atual.getPositiveRatings(), atual.getNegativeRatings());

        boolean ok = gameFile.update(atualizado);
        System.out.println(ok ? "Registro atualizado com sucesso." : "Falha ao atualizar.");
    }

    private static void deletarRegistro(GameFile gameFile) throws IOException {
        int id = lerInt("Informe o id (appid) do jogo a deletar: ");
        boolean ok = gameFile.delete(id);
        System.out.println(ok ? "Registro removido (lápide marcada)." : "Registro não encontrado.");
    }

    private static void ordenacaoExterna(GameFile gameFile) throws IOException {
        int numeroCaminhos = lerInt("Número de caminhos (arquivos temporários, mínimo 2): ");
        int maxRegistros = lerInt("Número máximo de registros ordenados em memória primária: ");

        String destPath = "data/steam_ordenado.bin";
        System.out.println("Executando ordenação externa (intercalação balanceada)...");
        long inicio = System.currentTimeMillis();
        ExternalSort.sort(gameFile, destPath, numeroCaminhos, maxRegistros);
        long fim = System.currentTimeMillis();
        System.out.println("Ordenação concluída em " + (fim - inicio) + " ms.");
        System.out.println("Arquivo ordenado gerado em: " + destPath);

        System.out.print("Deseja passar a usar o arquivo ordenado nas próximas operações de CRUD? (s/n): ");
        String resp = sc.nextLine().trim().toLowerCase();
        if (resp.startsWith("s")) {
            gameFile.close();
            File original = new File(BIN_PATH);
            File ordenado = new File(destPath);
            original.delete();
            ordenado.renameTo(original);
            System.out.println("Arquivo binário principal substituído pela versão ordenada e compactada.");
        }
    }

    private static void estatisticas(GameFile gameFile) throws IOException {
        int[] stats = gameFile.stats();
        System.out.println("Registros válidos : " + stats[0]);
        System.out.println("Registros removidos: " + stats[1]);
        System.out.println("Último id utilizado: " + gameFile.getLastId());
        System.out.println("Tamanho do arquivo : " + gameFile.length() + " bytes");
    }

    private static void listarAmostra(GameFile gameFile) throws IOException {
        int limite = lerInt("Quantos registros deseja listar (a partir do início do arquivo)? ");
        int[] count = {0};
        gameFile.forEachValid(g -> {
            if (count[0] < limite) {
                System.out.println("  " + g);
                count[0]++;
            }
        });
    }

    // ---------------------------------------------------------------
    // Auxiliares de leitura do console
    // ---------------------------------------------------------------

    private static int lerInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = sc.nextLine().trim();
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                System.out.println("Valor inválido, informe um número inteiro.");
            }
        }
    }

    private static String lerStringOpcional(String prompt, String defaultIfBlank) {
        System.out.print(prompt);
        String s = sc.nextLine();
        if (s == null || s.isBlank()) {
            return defaultIfBlank;
        }
        return s;
    }

    private static Float lerFloatOpcional(String prompt) {
        System.out.print(prompt);
        String s = sc.nextLine();
        if (s == null || s.isBlank()) return null;
        try {
            return Float.parseFloat(s.trim());
        } catch (NumberFormatException e) {
            System.out.println("Valor inválido, mantendo o anterior.");
            return null;
        }
    }
}
