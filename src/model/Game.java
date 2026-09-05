package model;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Entidade "Game" (Jogo da Steam).
 *
 * Representa um registro do domínio escolhido (base de dados de jogos da
 * plataforma Steam - dataset publico do Kaggle "Steam Store Games").
 *
 * Campos e seus tipos, conforme exigido pelo enunciado do TP1:
 *
 *   1) String de tamanho FIXO  -> developer   (30 caracteres, truncado/preenchido)
 *   2) String de tamanho VARIÁVEL -> name (nome do jogo)
 *   3) Data                    -> releaseDate (data de lançamento)
 *   4) Lista de valores c/ separador -> genres (generos, separador ';')
 *   5) Inteiro / Float         -> price (float) e positiveRatings/negativeRatings (int)
 *
 * O campo "id" (appid da Steam) é usado como chave primária do registro,
 * conforme exigido pela estrutura do arquivo (leitura/atualização/remoção por id).
 *
 * O objeto sabe se (de)serializar para um vetor de bytes bruto (toByteArray /
 * fromByteArray). O arquivo binário sequencial (GameFile) é quem envolve
 * esse vetor de bytes com a lápide e o indicador de tamanho do registro.
 */
public class Game {

    /** Tamanho fixo (em bytes) reservado para o campo "developer". */
    public static final int DEVELOPER_FIXED_LEN = 30;

    /** Separador usado na lista de gêneros. */
    public static final String GENRE_SEPARATOR = ";";

    private int id;                 // appid da Steam
    private String name;            // string de tamanho variável
    private String developer;       // string de tamanho fixo
    private LocalDate releaseDate;  // data
    private List<String> genres;    // lista de valores com separador
    private float price;            // float
    private int positiveRatings;    // inteiro
    private int negativeRatings;    // inteiro

    public Game() {
        this.genres = new ArrayList<>();
    }

    public Game(int id, String name, String developer, LocalDate releaseDate,
                List<String> genres, float price, int positiveRatings, int negativeRatings) {
        this.id = id;
        this.name = name;
        this.developer = developer;
        this.releaseDate = releaseDate;
        this.genres = genres != null ? genres : new ArrayList<>();
        this.price = price;
        this.positiveRatings = positiveRatings;
        this.negativeRatings = negativeRatings;
    }

    // ---------------------------------------------------------------
    // Getters / Setters
    // ---------------------------------------------------------------

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDeveloper() { return developer; }
    public void setDeveloper(String developer) { this.developer = developer; }

    public LocalDate getReleaseDate() { return releaseDate; }
    public void setReleaseDate(LocalDate releaseDate) { this.releaseDate = releaseDate; }

    public List<String> getGenres() { return genres; }
    public void setGenres(List<String> genres) { this.genres = genres; }

    public float getPrice() { return price; }
    public void setPrice(float price) { this.price = price; }

    public int getPositiveRatings() { return positiveRatings; }
    public void setPositiveRatings(int positiveRatings) { this.positiveRatings = positiveRatings; }

    public int getNegativeRatings() { return negativeRatings; }
    public void setNegativeRatings(int negativeRatings) { this.negativeRatings = negativeRatings; }

    // ---------------------------------------------------------------
    // Serialização binária
    // ---------------------------------------------------------------

    /**
     * Converte o objeto para um vetor de bytes.
     * Layout: id(int) | developer(30 bytes fixos) | name(UTF variável) |
     *         releaseDate.toEpochDay()(long) | genres(UTF variável, join ';') |
     *         price(float) | positiveRatings(int) | negativeRatings(int)
     */
    public byte[] toByteArray() {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(baos)) {

            dos.writeInt(id);
            dos.write(fixedDeveloperBytes());
            dos.writeUTF(name == null ? "" : name);
            dos.writeLong(releaseDate == null ? 0L : releaseDate.toEpochDay());
            dos.writeUTF(String.join(GENRE_SEPARATOR, genres));
            dos.writeFloat(price);
            dos.writeInt(positiveRatings);
            dos.writeInt(negativeRatings);

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            // Não deve ocorrer: estamos escrevendo em memória (ByteArrayOutputStream).
            throw new RuntimeException("Falha ao serializar Game", e);
        }
    }

    /** Reconstrói um Game a partir do vetor de bytes gerado por toByteArray(). */
    public static Game fromByteArray(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             DataInputStream dis = new DataInputStream(bais)) {

            Game g = new Game();
            g.id = dis.readInt();

            byte[] devBytes = new byte[DEVELOPER_FIXED_LEN];
            dis.readFully(devBytes);
            g.developer = new String(devBytes, StandardCharsets.UTF_8).trim();

            g.name = dis.readUTF();
            long epochDay = dis.readLong();
            g.releaseDate = LocalDate.ofEpochDay(epochDay);

            String genresJoined = dis.readUTF();
            g.genres = genresJoined.isEmpty()
                    ? new ArrayList<>()
                    : new ArrayList<>(Arrays.asList(genresJoined.split(GENRE_SEPARATOR)));

            g.price = dis.readFloat();
            g.positiveRatings = dis.readInt();
            g.negativeRatings = dis.readInt();

            return g;
        } catch (IOException e) {
            throw new RuntimeException("Falha ao desserializar Game", e);
        }
    }

    /**
     * Extrai apenas o id de um vetor de bytes serializado, sem desserializar
     * o objeto inteiro. Usado pelo GameFile para localizar registros de forma
     * mais rápida durante a varredura sequencial.
     */
    public static int peekId(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }

    private byte[] fixedDeveloperBytes() {
        byte[] result = new byte[DEVELOPER_FIXED_LEN];
        Arrays.fill(result, (byte) ' ');
        if (developer != null) {
            byte[] src = developer.getBytes(StandardCharsets.UTF_8);
            int len = Math.min(src.length, DEVELOPER_FIXED_LEN);
            System.arraycopy(src, 0, result, 0, len);
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format(
                "#%d | %s | dev: %s | lançamento: %s | gêneros: %s | preço: R$ %.2f | +%d/-%d avaliações",
                id, name, developer, releaseDate, String.join(", ", genres),
                price, positiveRatings, negativeRatings);
    }
}
