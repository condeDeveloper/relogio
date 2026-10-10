package br.com.conde.relogio;

import java.util.Locale;

/**
 * O MEDIDOR: imprime as tabelas que o README mostra e que os testes conferem.
 *
 * <p>Toda tabela daqui tem um teste do lado que afirma os mesmos numeros. Texto nao
 * quebra quando deixa de ser verdade; teste quebra.
 */
public final class Medidor {

    private Medidor() {
    }

    public static void main(String[] args) {
        String comando = args.length > 0 ? args[0] : "tudo";

        if (comando.equals("ordem") || comando.equals("tudo")) ordem();
        if (comando.equals("inversao") || comando.equals("tudo")) inversao();
        if (comando.equals("concorrencia") || comando.equals("tudo")) concorrencia();
        if (comando.equals("tamanho") || comando.equals("tudo")) tamanho();
        if (comando.equals("escala") || comando.equals("tudo")) escala();
    }

    private static void ordem() {
        System.out.println();
        System.out.println("O QUE CADA RELOGIO ACERTA E ERRA, em todos os pares de eventos de uma execucao.");
        System.out.println();
        System.out.printf("  %-12s %10s %22s %14s %24s %22s %12s %18s %18s%n",
                "relogio", "pares", "concorrentes de verdade", "fracao certa",
                "concorrentes ordenados", "ordem nao vista", "inversoes", "fracao invertida",
                "numeros por evento");

        for (var l : Medidas.aOrdem())
            System.out.printf(Locale.ROOT, "  %-12s %10d %22d %14.4f %24d %22d %12d %18.6f %18d%n",
                    l.relogio(), l.pares(), l.concorrentesDeVerdade(), l.fracaoCerta(),
                    l.concorrentesOrdenados(), l.ordenadosPerdidos(), l.inversoes(),
                    l.fracaoInvertida(), l.numerosPorEvento());

        System.out.println();
    }

    private static void inversao() {
        System.out.println();
        System.out.println("A INVERSAO DE CAUSA E EFEITO, com a rede levando "
                + Medidas.ATRASO_DA_REDE + " ms.");
        System.out.println();
        System.out.printf("  %14s %16s %-12s %14s %12s %18s %26s%n",
                "desvio em ms", "atraso da rede", "relogio", "inversoes", "pares",
                "fracao invertida", "inversoes de envio e recebimento");

        for (var l : Medidas.aInversao())
            System.out.printf(Locale.ROOT, "  %14d %16d %-12s %14d %12d %18.6f %26d%n",
                    l.desvio(), l.atrasoDaRede(), l.relogio(), l.inversoes(), l.pares(),
                    l.fracaoInvertida(), l.piorInversaoEmPares());

        System.out.println();
    }

    private static void concorrencia() {
        System.out.println();
        System.out.println("QUANTA CONCORRENCIA HA DE VERDADE, que e o denominador do erro de Lamport.");
        System.out.println();
        System.out.printf("  %12s %18s %10s %22s %22s %12s %12s %22s%n",
                "processos", "chance de enviar", "sementes", "mensagens entregues",
                "fracao concorrente", "a menor", "a maior", "acerto de Lamport");

        for (var l : Medidas.aConcorrencia())
            System.out.printf(Locale.ROOT, "  %12d %18.2f %10d %22.1f %22.4f %12.4f %12.4f %22.4f%n",
                    l.processos(), l.chanceDeEnviar(), l.sementes(), l.mensagensEntregues(),
                    l.fracaoConcorrente(), l.menorFracao(), l.maiorFracao(), l.acertoDeLamport());

        System.out.println();
    }

    private static void tamanho() {
        System.out.println();
        System.out.println("O TAMANHO de cada relogio, e o que ele compra.");
        System.out.println();
        System.out.printf("  %-12s %12s %20s %22s %14s %12s%n",
                "relogio", "processos", "numeros por evento", "bytes por mensagem",
                "fracao certa", "inversoes");

        for (var l : Medidas.oTamanho())
            System.out.printf(Locale.ROOT, "  %-12s %12d %20d %22d %14.4f %12d%n",
                    l.relogio(), l.processos(), l.numerosPorEvento(), l.bytesPorMensagem(),
                    l.fracaoCerta(), l.inversoes());

        System.out.println();
    }

    private static void escala() {
        System.out.println();
        System.out.println("O QUE ACONTECE QUANDO OS PROCESSOS CRESCEM.");
        System.out.println();
        System.out.printf("  %12s %20s %20s %22s %22s %22s%n",
                "processos", "bytes do vetorial", "bytes do Lamport",
                "acerto do vetorial", "acerto do Lamport", "fracao concorrente");

        for (var l : Medidas.aEscala())
            System.out.printf(Locale.ROOT, "  %12d %20d %20d %22.4f %22.4f %22.4f%n",
                    l.processos(), l.bytesDoVetorial(), l.bytesDoLamport(),
                    l.acertoDoVetorial(), l.acertoDoLamport(), l.fracaoConcorrente());

        System.out.println();
    }
}
