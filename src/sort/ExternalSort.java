package sort;

import file.GameFile;
import model.Game;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Ordenação Externa por Intercalação Balanceada (Balanced Multiway Merge Sort).
 *
 * Parâmetros exigidos pelo enunciado:
 *   - numeroCaminhos: quantidade de arquivos temporários ("caminhos") usados
 *     tanto na fase de distribuição quanto na fase de intercalação.
 *   - maxRegistrosEmMemoria: tamanho máximo (em registros) de cada sub-lista
 *     ordenada em memória primária durante a fase de distribuição.
 *
 * Funcionamento:
 *   Fase 1 (Distribuição): lê os registros VÁLIDOS do arquivo sequencial de
 *   origem em blocos de até maxRegistrosEmMemoria, ordena cada bloco em
 *   memória (por id) e grava cada bloco ordenado ("run") alternadamente em um
 *   dos numeroCaminhos arquivos temporários.
 *
 *   Fase 2 (Intercalação): a cada rodada, agrupa 1 run de cada um dos
 *   numeroCaminhos arquivos de entrada, faz a intercalação (k-way merge) das
 *   runs do grupo, e grava o resultado (uma única run maior) em um dos
 *   numeroCaminhos arquivos de saída da próxima geração, alternadamente.
 *   Repete-se até restar apenas 1 run.
 *
 *   Como efeito colateral, registros marcados como removidos (lápide) e
 *   espaços de registros atualizados que mudaram de tamanho são eliminados:
 *   apenas os registros válidos participam da ordenação, então o arquivo de
 *   destino sai compactado ("sem espaços em branco").
 */
public class ExternalSort {

    private static final Comparator<Game> BY_ID = Comparator.comparingInt(Game::getId);

    /**
     * Executa a ordenação externa.
     *
     * @param source                arquivo binário de origem (sequencial, pode ter registros removidos)
     * @param destPath              caminho do novo arquivo binário, já ordenado e compactado
     * @param numeroCaminhos        quantidade de arquivos temporários (>= 2)
     * @param maxRegistrosEmMemoria tamanho máximo de cada bloco ordenado em memória (>= 1)
     */
    public static void sort(GameFile source, String destPath, int numeroCaminhos, int maxRegistrosEmMemoria) throws IOException {
        if (numeroCaminhos < 2) throw new IllegalArgumentException("número de caminhos deve ser >= 2");
        if (maxRegistrosEmMemoria < 1) throw new IllegalArgumentException("registros em memória deve ser >= 1");

        Path tmpDir = Files.createTempDirectory("tp1_extsort_");
        try {
            // ---------- Fase 1: Distribuição ----------
            File[] files = new File[numeroCaminhos];
            List<List<Integer>> runSizes = new ArrayList<>();
            for (int i = 0; i < numeroCaminhos; i++) {
                files[i] = new File(tmpDir.toFile(), "caminho_" + i + "_gen0.bin");
                runSizes.add(new ArrayList<>());
            }

            int totalValidos = distribuir(source, files, runSizes, maxRegistrosEmMemoria);
            System.out.println("  [Ordenação Externa] " + totalValidos + " registros válidos distribuídos em "
                    + totalRuns(runSizes) + " runs iniciais, usando " + numeroCaminhos + " caminhos.");

            if (totalValidos == 0) {
                writeFinalEmpty(destPath);
                return;
            }

            // ---------- Fase 2: Intercalação (rodadas) até restar 1 run ----------
            int rodada = 1;
            while (totalRuns(runSizes) > 1) {
                File[] outFiles = new File[numeroCaminhos];
                List<List<Integer>> outRunSizes = new ArrayList<>();
                for (int i = 0; i < numeroCaminhos; i++) {
                    outFiles[i] = new File(tmpDir.toFile(), "caminho_" + i + "_gen" + rodada + ".bin");
                    outRunSizes.add(new ArrayList<>());
                }

                intercalarRodada(files, runSizes, outFiles, outRunSizes, numeroCaminhos);

                // limpa geração anterior
                for (File f : files) f.delete();

                files = outFiles;
                runSizes = outRunSizes;
                System.out.println("  [Ordenação Externa] rodada " + rodada + " concluída -> "
                        + totalRuns(runSizes) + " run(s) restante(s).");
                rodada++;
            }

            // ---------- Finalização: grava resultado ordenado no destino ----------
            File runFinal = null;
            for (int i = 0; i < numeroCaminhos; i++) {
                if (!runSizes.get(i).isEmpty()) {
                    runFinal = files[i];
                    break;
                }
            }
            gravarResultadoFinal(runFinal, destPath);

            for (File f : files) f.delete();
        } finally {
            deleteDirRecursive(tmpDir.toFile());
        }
    }

    private static int totalRuns(List<List<Integer>> runSizes) {
        int t = 0;
        for (List<Integer> l : runSizes) t += l.size();
        return t;
    }

    // ---------------------------------------------------------------
    // Fase 1: Distribuição
    // ---------------------------------------------------------------

    private static int distribuir(GameFile source, File[] files, List<List<Integer>> runSizes, int maxRegistrosEmMemoria) throws IOException {
        DataOutputStream[] outs = new DataOutputStream[files.length];
        for (int i = 0; i < files.length; i++) {
            outs[i] = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(files[i])));
        }

        List<Game> buffer = new ArrayList<>(maxRegistrosEmMemoria);
        int[] totalValidos = {0};
        int[] outIndex = {0};

        source.forEachValid(g -> {
            buffer.add(g);
            totalValidos[0]++;
            if (buffer.size() >= maxRegistrosEmMemoria) {
                flushRun(buffer, outs, runSizes, outIndex);
            }
        });
        if (!buffer.isEmpty()) {
            flushRun(buffer, outs, runSizes, outIndex);
        }

        for (DataOutputStream out : outs) out.close();
        return totalValidos[0];
    }

    private static void flushRun(List<Game> buffer, DataOutputStream[] outs, List<List<Integer>> runSizes, int[] outIndex) {
        try {
            buffer.sort(BY_ID);
            int idx = outIndex[0];
            DataOutputStream out = outs[idx];
            for (Game g : buffer) {
                byte[] payload = g.toByteArray();
                out.writeInt(payload.length);
                out.write(payload);
            }
            runSizes.get(idx).add(buffer.size());
            outIndex[0] = (idx + 1) % outs.length;
            buffer.clear();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------------
    // Fase 2: Intercalação
    // ---------------------------------------------------------------

    private static void intercalarRodada(File[] inFiles, List<List<Integer>> inRunSizes,
                                          File[] outFiles, List<List<Integer>> outRunSizes,
                                          int numeroCaminhos) throws IOException {
        DataInputStream[] ins = new DataInputStream[numeroCaminhos];
        List<ArrayDeque<Integer>> filas = new ArrayList<>(numeroCaminhos);
        for (int i = 0; i < numeroCaminhos; i++) {
            ins[i] = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(inFiles[i])));
            filas.add(new ArrayDeque<>(inRunSizes.get(i)));
        }
        DataOutputStream[] outs = new DataOutputStream[numeroCaminhos];
        for (int i = 0; i < numeroCaminhos; i++) {
            outs[i] = new DataOutputStream(new java.io.BufferedOutputStream(new FileOutputStream(outFiles[i])));
        }

        int outIndex = 0;
        boolean algumaFilaTemRun = true;
        while (algumaFilaTemRun) {
            int[] tamanhosGrupo = new int[numeroCaminhos];
            algumaFilaTemRun = false;
            for (int i = 0; i < numeroCaminhos; i++) {
                Integer tam = filas.get(i).pollFirst();
                tamanhosGrupo[i] = (tam == null) ? 0 : tam;
                if (tam != null) algumaFilaTemRun = true;
            }
            if (!algumaFilaTemRun) break;

            int totalMerged = mergeGrupo(ins, tamanhosGrupo, outs[outIndex]);
            outRunSizes.get(outIndex).add(totalMerged);
            outIndex = (outIndex + 1) % numeroCaminhos;
        }

        for (DataInputStream in : ins) in.close();
        for (DataOutputStream out : outs) out.close();
    }

    /** Intercala (k-way merge) uma run de cada arquivo de entrada (tamanho indicado em tamanhosGrupo[i]). */
    private static int mergeGrupo(DataInputStream[] ins, int[] tamanhosGrupo, DataOutputStream out) throws IOException {
        int numeroCaminhos = ins.length;
        int[] restantes = tamanhosGrupo.clone();

        PriorityQueue<HeapNode> heap = new PriorityQueue<>(Comparator.comparingInt(n -> n.game.getId()));
        for (int i = 0; i < numeroCaminhos; i++) {
            if (restantes[i] > 0) {
                Game g = lerRegistro(ins[i]);
                heap.add(new HeapNode(i, g));
                restantes[i]--;
            }
        }

        int total = 0;
        while (!heap.isEmpty()) {
            HeapNode node = heap.poll();
            byte[] payload = node.game.toByteArray();
            out.writeInt(payload.length);
            out.write(payload);
            total++;

            int i = node.fileIndex;
            if (restantes[i] > 0) {
                Game g = lerRegistro(ins[i]);
                heap.add(new HeapNode(i, g));
                restantes[i]--;
            }
        }
        return total;
    }

    private static Game lerRegistro(DataInputStream in) throws IOException {
        int tamanho = in.readInt();
        byte[] payload = new byte[tamanho];
        in.readFully(payload);
        return Game.fromByteArray(payload);
    }

    private static class HeapNode {
        final int fileIndex;
        final Game game;
        HeapNode(int fileIndex, Game game) { this.fileIndex = fileIndex; this.game = game; }
    }

    // ---------------------------------------------------------------
    // Finalização
    // ---------------------------------------------------------------

    private static void gravarResultadoFinal(File runFinal, String destPath) throws IOException {
        File destFile = new File(destPath);
        if (destFile.exists()) destFile.delete();

        try (GameFile dest = new GameFile(destPath);
             DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new FileInputStream(runFinal)))) {
            while (true) {
                Game g;
                try {
                    g = lerRegistro(in);
                } catch (EOFException eof) {
                    break;
                }
                dest.create(g);
            }
        }
    }

    private static void writeFinalEmpty(String destPath) throws IOException {
        File destFile = new File(destPath);
        if (destFile.exists()) destFile.delete();
        try (GameFile dest = new GameFile(destPath)) {
            // apenas cria o cabeçalho vazio
        }
    }

    private static void deleteDirRecursive(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File c : children) c.delete();
        }
        dir.delete();
    }
}
