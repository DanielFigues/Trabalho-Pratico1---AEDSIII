import file.CsvExporter;
import file.CsvLoader;
import compress.CompressionService;
import file.GameFile;
import model.Game;
import search.PatternSearchService;
import sort.ExternalSort;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Algoritmos e Estruturas de Dados III (PUC Minas)
 * TP1 (CRUD sequencial + ordenação externa) e TP3 (compactação e casamento de padrões).
 * Tema escolhido: base de dados de jogos da Steam (dataset "Steam Store Games").
 *
 * Menu principal (uso pelo terminal), conforme especificado no enunciado:
 *   1) Carga da base de dados (CSV -> arquivo binário)
 *   2) Ler um registro por id
 *   3) Atualizar um registro
 *   4) Deletar um registro
 *   5) Ordenação Externa (intercalação balanceada, com nº de caminhos e
 *      nº máximo de registros em memória primária)
 *
 * Opções da Etapa 3:
 *   9) Compactar a base (Huffman e LZW), comparando ganho e tempo
 *  10) Descompactar uma versão X da compactação (substitui o arquivo de dados)
 *  11) Casamento de padrões (KMP e Boyer-Moore) em um campo ou no arquivo inteiro
 */
public class Main {

    private static final String CSV_PATH;
    private static final String BIN_PATH;
    private static final String SORTED_PATH;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final Scanner sc = new Scanner(System.in);

    /** Serviço da Etapa 3 que executa Huffman e LZW sobre o arquivo de dados. */
    private static final CompressionService COMPRESSAO = new CompressionService();

    static {
        // Os caminhos são resolvidos a partir da pasta do PROJETO (onde ficam
        // as pastas "out" e "data"), e não do diretório de onde o comando
        // "java" foi chamado (cwd). Isso evita o problema de o programa
        // criar/procurar "data/..." em um lugar inesperado quando o usuário
        // roda "java -cp out Main" de outra pasta.
        File projectRoot = resolveProjectRoot();
        CSV_PATH = new File(projectRoot, "data/steam.csv").getPath();
        BIN_PATH = new File(projectRoot, "data/steam.bin").getPath();
        SORTED_PATH = new File(projectRoot, "data/steam_ordenado.bin").getPath();
    }

    /**
     * Descobre a pasta raiz do projeto (a pasta que contém "out" e "data")
     * a partir da localização real do .class em execução, e não do
     * diretório de trabalho atual (user.dir).
     */
    private static File resolveProjectRoot() {
        try {
            Path codeLocation = Path.of(
                    Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // codeLocation costuma ser a pasta "out" (quando rodado com -cp out)
            // ou um .jar dentro da pasta raiz do projeto.
            File codeDir = codeLocation.toFile();
            if (!codeDir.isDirectory()) {
                codeDir = codeDir.getParentFile(); // caso esteja rodando a partir de um .jar
            }
            File parent = codeDir.getParentFile();
            return parent != null ? parent : new File(".");
        } catch (URISyntaxException | NullPointerException e) {
            // Fallback: usa o diretório de trabalho atual.
            return new File(".");
        }
    }

    public static void main(String[] args) throws IOException {
        new File(BIN_PATH).getParentFile().mkdirs();

        System.out.println("=========================================================");
        System.out.println(" AED III - Jogos da Steam (CRUD, Compactação, Padrões)");
        System.out.println("=========================================================");
        System.out.println("Pasta de dados em uso : " + new File(BIN_PATH).getParentFile().getAbsolutePath());
        System.out.println("Arquivo CSV esperado  : " + new File(CSV_PATH).getAbsolutePath());
        System.out.println("Arquivo binário (.bin): " + new File(BIN_PATH).getAbsolutePath());

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
                    case "8" -> exportarParaCsv();
                    case "9" -> compactarBase();
                    case "10" -> descompactarBase(gameFile);
                    case "11" -> casamentoDePadroes();
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
        System.out.println("8 - Exportar um arquivo .bin para .csv");
        System.out.println("9 - Compactar a base de dados (Huffman e LZW)");
        System.out.println("10 - Descompactar uma versão da base de dados");
        System.out.println("11 - Casamento de padrões (KMP e Boyer-Moore)");
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

        String destPath = SORTED_PATH;
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

    /**
     * Exporta um arquivo binário (.bin) qualquer - por exemplo o
     * "steam_ordenado.bin" gerado pela Ordenação Externa - de volta para
     * um arquivo .csv, contendo os campos da entidade Game.
     * Abre o .bin em uma GameFile própria e independente da usada pelo menu,
     * então funciona mesmo que o usuário não tenha trocado o arquivo
     * principal pelo arquivo ordenado.
     */
    private static void exportarParaCsv() throws IOException {
        System.out.print("Caminho do arquivo .bin a exportar [" + SORTED_PATH + "]: ");
        String binPath = sc.nextLine().trim();
        if (binPath.isBlank()) binPath = SORTED_PATH;

        File binFile = new File(binPath);
        if (!binFile.exists()) {
            System.out.println("Arquivo não encontrado: " + binFile.getAbsolutePath());
            return;
        }

        String csvPadrao = binPath.endsWith(".bin")
                ? binPath.substring(0, binPath.length() - 4) + ".csv"
                : binPath + ".csv";
        System.out.print("Caminho do .csv de saída [" + csvPadrao + "]: ");
        String csvPath = sc.nextLine().trim();
        if (csvPath.isBlank()) csvPath = csvPadrao;

        try (GameFile origem = new GameFile(binFile.getPath())) {
            int total = CsvExporter.export(origem, csvPath);
            System.out.println("Exportação concluída: " + total + " registros gravados em "
                    + new File(csvPath).getAbsolutePath());
        }
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
    // Etapa 3 - Compressão de dados
    // ---------------------------------------------------------------

    /**
     * Compacta o arquivo de dados com Huffman e LZW. Cada execução cria uma nova
     * versão X, com os arquivos "nomeArquivoNomeAlgoritmoCompressaoX", e mostra
     * ganho/perda, tempo de cada algoritmo e qual deles foi melhor.
     */
    private static void compactarBase() throws IOException {
        File dados = new File(BIN_PATH);
        if (!dados.exists() || dados.length() == 0) {
            System.out.println("Não há arquivo de dados para compactar. Carregue a base primeiro (opção 1).");
            return;
        }
        System.out.println("Compactando " + dados.getName() + " (" + dados.length() + " bytes) com Huffman e LZW...");
        try {
            CompressionService.printCompressionReport(COMPRESSAO.compress(dados));
            System.out.println("Integridade verificada: a descompressão de cada algoritmo reproduz o arquivo original.");
        } catch (IllegalStateException e) {
            System.out.println("Erro na compactação: " + e.getMessage());
        }
    }

    /**
     * Descompacta a versão X informada pelo usuário e SUBSTITUI o arquivo de dados
     * pelo resultado, mostrando o tempo de cada algoritmo e qual foi mais rápido.
     */
    private static void descompactarBase(GameFile gameFile) throws IOException {
        File dados = new File(BIN_PATH);
        List<Integer> versoes = COMPRESSAO.availableVersions(dados);
        if (versoes.isEmpty()) {
            System.out.println("Nenhuma versão compactada encontrada. Use a opção 9 para compactar.");
            return;
        }
        System.out.println("Versões disponíveis: " + versoes);
        int versao = lerInt("Informe a versão X que deseja descompactar: ");
        if (!versoes.contains(versao)) {
            System.out.println("A versão " + versao + " não existe.");
            return;
        }
        System.out.print("Isso SUBSTITUIRÁ o arquivo de dados atual (" + dados.getName() + "). Continuar? (s/n): ");
        if (!sc.nextLine().trim().toLowerCase().startsWith("s")) {
            System.out.println("Operação cancelada.");
            return;
        }

        gameFile.close(); // libera o arquivo de dados para poder ser substituído
        try {
            CompressionService.printDecompressionReport(COMPRESSAO.decompress(dados, versao));
            System.out.println("Arquivo de dados substituído pela versão " + versao + " descompactada.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Erro na descompactação: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Etapa 3 - Casamento de padrões
    // ---------------------------------------------------------------

    /**
     * Busca um padrão (texto) em um campo dos registros ou no arquivo binário inteiro,
     * usando KMP e Boyer-Moore, e compara os dois algoritmos.
     */
    private static void casamentoDePadroes() throws IOException {
        File dados = new File(BIN_PATH);
        if (!dados.exists() || dados.length() == 0) {
            System.out.println("Não há arquivo de dados para pesquisar. Carregue a base primeiro (opção 1).");
            return;
        }

        System.out.println("Onde buscar o padrão?");
        System.out.println("  1 - Nome do jogo");
        System.out.println("  2 - Desenvolvedora");
        System.out.println("  3 - Gêneros");
        System.out.println("  4 - Arquivo binário inteiro (bytes)");
        PatternSearchService.Field campo;
        switch (lerInt("Escolha: ")) {
            case 1 -> campo = PatternSearchService.Field.NAME;
            case 2 -> campo = PatternSearchService.Field.DEVELOPER;
            case 3 -> campo = PatternSearchService.Field.GENRES;
            case 4 -> campo = PatternSearchService.Field.WHOLE_FILE;
            default -> {
                System.out.println("Opção inválida.");
                return;
            }
        }

        System.out.print("Padrão a procurar: ");
        String padrao = sc.nextLine();
        if (padrao.isEmpty()) {
            System.out.println("O padrão não pode ser vazio.");
            return;
        }

        boolean ignorarCaixa = false;
        if (campo != PatternSearchService.Field.WHOLE_FILE) {
            System.out.print("Ignorar maiúsculas/minúsculas? (s/n): ");
            ignorarCaixa = sc.nextLine().trim().toLowerCase().startsWith("s");
        } else {
            System.out.println("(No arquivo inteiro a comparação é exata, byte a byte.)");
        }

        PatternSearchService.printReport(campo, padrao,
                PatternSearchService.search(BIN_PATH, campo, padrao, ignorarCaixa));
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
