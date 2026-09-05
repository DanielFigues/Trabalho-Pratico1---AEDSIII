package file;

import model.Game;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.Consumer;

/**
 * Gerencia o arquivo binário sequencial de jogos (CRUD Sequencial).
 *
 * Estrutura do arquivo (conforme especificação do TP1):
 *
 *   [Cabeçalho]  int  -> último id utilizado (4 bytes)
 *   [Registro 1] byte lápide (1) | int tamanho (4) | byte[tamanho] vetor de bytes
 *   [Registro 2] byte lápide (1) | int tamanho (4) | byte[tamanho] vetor de bytes
 *   ...
 *
 * Lápide: ' ' (espaço, 0x20) = registro válido ; '*' (0x2A) = registro removido.
 *
 * Operações de CRUD:
 *   - create(Game)         -> insere um novo registro no fim do arquivo
 *   - read(id)              -> varre o arquivo procurando o id informado
 *   - update(Game)          -> atualiza no próprio local (mesmo tamanho) ou
 *                              apaga (lápide) + reinsere no fim (tamanho mudou)
 *   - delete(id)             -> marca a lápide do registro como removido
 */
public class GameFile implements AutoCloseable {

    private static final byte LAPIDE_VALIDO = ' ';
    private static final byte LAPIDE_REMOVIDO = '*';
    public static final long HEADER_SIZE = 4L; // int = último id utilizado

    private final RandomAccessFile raf;

    public GameFile(String path) throws IOException {
        this.raf = new RandomAccessFile(path, "rw");
        if (raf.length() < HEADER_SIZE) {
            raf.seek(0);
            raf.writeInt(0); // nenhum id utilizado ainda
        }
    }

    // ---------------------------------------------------------------
    // Cabeçalho
    // ---------------------------------------------------------------

    public synchronized int getLastId() throws IOException {
        raf.seek(0);
        return raf.readInt();
    }

    private void setLastId(int id) throws IOException {
        raf.seek(0);
        raf.writeInt(id);
    }

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    /**
     * Insere um novo jogo no arquivo. Se o id do objeto for &lt;= 0, um novo id
     * sequencial é gerado automaticamente a partir do cabeçalho. Caso contrário
     * (ex.: carga inicial a partir do CSV, onde o appid da Steam já é o id),
     * o id informado é respeitado e o cabeçalho é atualizado se necessário.
     */
    public synchronized int create(Game game) throws IOException {
        int lastId = getLastId();
        if (game.getId() <= 0) {
            game.setId(lastId + 1);
        }
        if (game.getId() > lastId) {
            setLastId(game.getId());
        }
        appendRecord(game.toByteArray());
        return game.getId();
    }

    private void appendRecord(byte[] payload) throws IOException {
        raf.seek(raf.length());
        raf.writeByte(LAPIDE_VALIDO);
        raf.writeInt(payload.length);
        raf.write(payload);
    }

    // ---------------------------------------------------------------
    // READ
    // ---------------------------------------------------------------

    /** Varre o arquivo procurando o registro válido com o id informado. */
    public synchronized Game read(int id) throws IOException {
        raf.seek(HEADER_SIZE);
        long len = raf.length();
        while (raf.getFilePointer() < len) {
            long recordStart = raf.getFilePointer();
            byte lapide = raf.readByte();
            int tamanho = raf.readInt();
            if (lapide == LAPIDE_VALIDO) {
                byte[] idBytes = new byte[4];
                raf.readFully(idBytes);
                int recId = Game.peekId(idBytes);
                if (recId == id) {
                    byte[] resto = new byte[tamanho - 4];
                    raf.readFully(resto);
                    byte[] full = new byte[tamanho];
                    System.arraycopy(idBytes, 0, full, 0, 4);
                    System.arraycopy(resto, 0, full, 4, resto.length);
                    return Game.fromByteArray(full);
                } else {
                    raf.seek(recordStart + 1 + 4 + tamanho);
                }
            } else {
                raf.seek(recordStart + 1 + 4 + tamanho);
            }
        }
        return null;
    }

    /** Percorre todos os registros válidos do arquivo, aplicando a ação informada. */
    public synchronized void forEachValid(Consumer<Game> action) throws IOException {
        raf.seek(HEADER_SIZE);
        long len = raf.length();
        while (raf.getFilePointer() < len) {
            byte lapide = raf.readByte();
            int tamanho = raf.readInt();
            byte[] payload = new byte[tamanho];
            raf.readFully(payload);
            if (lapide == LAPIDE_VALIDO) {
                action.accept(Game.fromByteArray(payload));
            }
        }
    }

    // ---------------------------------------------------------------
    // UPDATE
    // ---------------------------------------------------------------

    /**
     * Atualiza um registro existente.
     * - Se o novo registro serializado tiver o MESMO tamanho do antigo, o
     *   conteúdo é sobrescrito no próprio local.
     * - Se o tamanho MUDAR, o registro antigo é removido (lápide) e o novo
     *   registro é escrito no fim do arquivo.
     * Retorna true se o registro foi encontrado e atualizado.
     */
    public synchronized boolean update(Game game) throws IOException {
        long offset = locate(game.getId());
        if (offset < 0) {
            return false;
        }
        raf.seek(offset + 1); // pula a lápide
        int tamanhoAntigo = raf.readInt();

        byte[] novoPayload = game.toByteArray();
        if (novoPayload.length == tamanhoAntigo) {
            raf.write(novoPayload); // mesmo tamanho: sobrescreve no lugar
        } else {
            raf.seek(offset);
            raf.writeByte(LAPIDE_REMOVIDO); // apaga o registro antigo
            appendRecord(novoPayload);       // escreve o novo no fim
        }
        return true;
    }

    // ---------------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------------

    /** Marca a lápide do registro com o id informado como removido. */
    public synchronized boolean delete(int id) throws IOException {
        long offset = locate(id);
        if (offset < 0) {
            return false;
        }
        raf.seek(offset);
        raf.writeByte(LAPIDE_REMOVIDO);
        return true;
    }

    /** Retorna o offset (posição da lápide) de um registro válido pelo id, ou -1. */
    private long locate(int id) throws IOException {
        raf.seek(HEADER_SIZE);
        long len = raf.length();
        while (raf.getFilePointer() < len) {
            long recordStart = raf.getFilePointer();
            byte lapide = raf.readByte();
            int tamanho = raf.readInt();
            if (lapide == LAPIDE_VALIDO) {
                byte[] idBytes = new byte[4];
                raf.readFully(idBytes);
                int recId = Game.peekId(idBytes);
                raf.seek(recordStart + 1 + 4 + tamanho);
                if (recId == id) {
                    return recordStart;
                }
            } else {
                raf.seek(recordStart + 1 + 4 + tamanho);
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------
    // Estatísticas / utilidades
    // ---------------------------------------------------------------

    public synchronized int[] stats() throws IOException {
        int validos = 0, removidos = 0;
        raf.seek(HEADER_SIZE);
        long len = raf.length();
        while (raf.getFilePointer() < len) {
            byte lapide = raf.readByte();
            int tamanho = raf.readInt();
            raf.seek(raf.getFilePointer() + tamanho);
            if (lapide == LAPIDE_VALIDO) validos++; else removidos++;
        }
        return new int[]{validos, removidos};
    }

    public long length() throws IOException {
        return raf.length();
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
