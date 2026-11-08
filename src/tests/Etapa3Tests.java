package tests;

import compress.Compressor;
import compress.Huffman;
import compress.LZW;
import search.BoyerMoore;
import search.KMP;
import search.PatternMatcher;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Testes automáticos da Etapa 3 (sem biblioteca externa, rodam com "java").
 *
 * Como executar (a partir da pasta raiz do projeto):
 *   javac -d out $(find src -name "*.java")
 *   java -cp out tests.Etapa3Tests [caminho/do/arquivo.bin]
 *
 * 1) Compressão: Huffman e LZW precisam devolver, byte a byte, o arquivo original
 *    em vários casos de borda (vazio, 1 byte, um só símbolo, dados aleatórios que
 *    forçam o LZW a reiniciar o dicionário, etc.) e no arquivo real da base.
 * 2) Casamento de padrões: KMP e Boyer-Moore precisam achar exatamente as mesmas
 *    ocorrências que um "oráculo" simples (força bruta, usado SÓ aqui para conferir).
 */
public class Etapa3Tests {

    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws IOException {
        testCompression(args.length > 0 ? args[0] : "data/steam.bin");
        testPatternMatching();

        System.out.println();
        System.out.println(checks + " verificações, " + failures + " falha(s).");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void check(boolean ok, String description) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("  FALHOU: " + description);
        }
    }

    // ---------------------------------------------------------------
    // Compressão
    // ---------------------------------------------------------------

    private static void testCompression(String realFile) throws IOException {
        System.out.println("== Compressão (ida e volta) ==");
        Random rnd = new Random(42);

        List<String> names = new ArrayList<>();
        List<byte[]> inputs = new ArrayList<>();

        names.add("arquivo vazio");
        inputs.add(new byte[0]);
        names.add("1 byte");
        inputs.add(new byte[]{7});
        names.add("1000 bytes iguais (um só símbolo)");
        inputs.add(new byte[1000]);
        names.add("texto repetitivo (caso KwKwK do LZW)");
        inputs.add("aaaaaaaaaaaaaaaaaaaaabababababababababaaaaaaaaaaaaa".getBytes(StandardCharsets.UTF_8));
        names.add("todos os 256 valores de byte");
        byte[] allBytes = new byte[256 * 4];
        for (int i = 0; i < allBytes.length; i++) allBytes[i] = (byte) i;
        inputs.add(allBytes);

        byte[] random = new byte[3_000_000]; // aleatório: enche o dicionário do LZW várias vezes (CLEAR)
        rnd.nextBytes(random);
        names.add("3 MB aleatórios (dicionário LZW enche e reinicia)");
        inputs.add(random);

        byte[] skewed = new byte[2_000_000]; // poucos símbolos, distribuição desigual
        for (int i = 0; i < skewed.length; i++) skewed[i] = (byte) (rnd.nextInt(10) < 7 ? 0 : rnd.nextInt(5));
        names.add("2 MB com distribuição desigual");
        inputs.add(skewed);

        File real = new File(realFile);
        if (real.exists()) {
            names.add("arquivo real da base (" + real.getName() + ")");
            inputs.add(Files.readAllBytes(real.toPath()));
        } else {
            System.out.println("  (arquivo " + realFile + " não encontrado; teste com dados reais ignorado)");
        }

        Compressor[] algorithms = { new Huffman(), new LZW() };
        for (Compressor algorithm : algorithms) {
            for (int i = 0; i < inputs.size(); i++) {
                byte[] original = inputs.get(i);
                byte[] packed = algorithm.compress(original);
                byte[] restored = algorithm.decompress(packed);
                boolean ok = Arrays.equals(original, restored);
                check(ok, algorithm.getName() + " - " + names.get(i));
                System.out.printf("  %-8s %-55s %,11d -> %,11d bytes  %s%n", algorithm.getName(), names.get(i),
                        original.length, packed.length, ok ? "OK" : "FALHOU");
            }
        }

        // Um compactado de um algoritmo não pode ser aceito pelo outro, e dados truncados são recusados.
        byte[] huff = new Huffman().compress("teste de assinatura".getBytes(StandardCharsets.UTF_8));
        byte[] lzw = new LZW().compress("teste de assinatura".getBytes(StandardCharsets.UTF_8));
        check(throwsIllegalArgument(() -> new LZW().decompress(huff)), "LZW recusa arquivo Huffman");
        check(throwsIllegalArgument(() -> new Huffman().decompress(lzw)), "Huffman recusa arquivo LZW");
        check(throwsIllegalArgument(() -> new LZW().decompress(Arrays.copyOf(lzw, lzw.length - 2))),
                "LZW recusa arquivo truncado");
        check(throwsIllegalArgument(() -> new Huffman().decompress(Arrays.copyOf(huff, huff.length - 2))),
                "Huffman recusa arquivo truncado");
        System.out.println("  recusa de arquivos inválidos/truncados verificada");
    }

    private static boolean throwsIllegalArgument(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    // ---------------------------------------------------------------
    // Casamento de padrões
    // ---------------------------------------------------------------

    private static void testPatternMatching() {
        System.out.println("== Casamento de padrões (KMP e Boyer-Moore x oráculo) ==");
        Random rnd = new Random(7);
        int cases = 0;

        // Alfabetos pequenos geram muitas ocorrências parciais e sobrepostas (o caso mais difícil).
        int[] alphabets = {2, 3, 4, 26, 256};
        for (int alphabet : alphabets) {
            for (int round = 0; round < 400; round++) {
                byte[] text = new byte[rnd.nextInt(200)];
                for (int i = 0; i < text.length; i++) text[i] = (byte) rnd.nextInt(alphabet);

                byte[] pattern;
                if (text.length > 0 && rnd.nextBoolean()) {
                    // padrão tirado do próprio texto: garante que existe pelo menos uma ocorrência
                    int len = 1 + rnd.nextInt(Math.min(8, text.length));
                    int start = rnd.nextInt(text.length - len + 1);
                    pattern = Arrays.copyOfRange(text, start, start + len);
                } else {
                    pattern = new byte[1 + rnd.nextInt(8)];
                    for (int i = 0; i < pattern.length; i++) pattern[i] = (byte) rnd.nextInt(alphabet);
                }
                cases++;
                compareWithOracle(text, pattern);
            }
        }

        // Casos fixos de borda.
        compareWithOracle("aaaaaaaa".getBytes(), "aaa".getBytes());          // ocorrências sobrepostas
        compareWithOracle("abcabcabcabd".getBytes(), "abcabd".getBytes());   // falha parcial no final
        compareWithOracle("abc".getBytes(), "abcd".getBytes());              // padrão maior que o texto
        compareWithOracle(new byte[0], "a".getBytes());                       // texto vazio
        compareWithOracle("anpanman".getBytes(), "pan".getBytes());
        compareWithOracle("São João da Boa Vista".getBytes(StandardCharsets.UTF_8),
                "João".getBytes(StandardCharsets.UTF_8));                     // acentos (UTF-8)
        cases += 6;
        System.out.println("  " + cases + " combinações texto/padrão conferidas");
    }

    /** Confere que KMP e Boyer-Moore encontram exatamente as mesmas posições que o oráculo. */
    private static void compareWithOracle(byte[] text, byte[] pattern) {
        List<Integer> expected = oracle(text, pattern);
        PatternMatcher[] matchers = { new KMP(pattern), new BoyerMoore(pattern) };
        for (PatternMatcher m : matchers) {
            List<Integer> found = m.findAll(text);
            check(expected.equals(found), m.getName() + " texto=" + Arrays.toString(text)
                    + " padrão=" + Arrays.toString(pattern) + " esperado=" + expected + " obtido=" + found);
        }
    }

    /** Oráculo: procura todas as posições comparando o padrão em cada alinhamento (só para teste). */
    private static List<Integer> oracle(byte[] text, byte[] pattern) {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i + pattern.length <= text.length; i++) {
            boolean same = true;
            for (int j = 0; j < pattern.length && same; j++) {
                same = text[i + j] == pattern[j];
            }
            if (same) positions.add(i);
        }
        return positions;
    }
}
