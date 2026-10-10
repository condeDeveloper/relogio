package br.com.conde.relogio;

import java.util.ArrayList;
import java.util.List;

/**
 * As MEDIDAS, em pares de eventos, nunca em segundos.
 *
 * <p>Segundo nao e medida: muda com a maquina, com o que mais esta rodando e com o humor
 * do coletor de lixo, e a integracao continua roda em tres sistemas.
 *
 * <p>A unidade aqui e o PAR DE EVENTOS. O juiz responde todos os n vezes n menos um sobre
 * dois pares de cada execucao, sem amostragem, e cada relogio responde os mesmos.
 */
public final class Medidas {

    private Medidas() {
    }

    /** Quantos processos as execucoes das tabelas tem. */
    public static final int PROCESSOS = 6;

    /** Quantos eventos por processo. */
    public static final int EVENTOS = 40;

    /** A chance de um evento ser um envio de mensagem. */
    public static final double CHANCE_DE_ENVIAR = 0.35;

    /** Quanto uma mensagem demora para chegar, em milissegundos. */
    public static final long ATRASO_DA_REDE = 20;

    /** Quanto o relogio de parede de cada maquina esta fora, em milissegundos. */
    public static final long DESVIO = 50;

    // ───────────────────── 1. a ordem ─────────────────────

    public record LinhaOrdem(String relogio, long pares, long concorrentesDeVerdade,
                             double fracaoCerta, long concorrentesOrdenados,
                             long ordenadosPerdidos, long inversoes,
                             double fracaoInvertida, int numerosPorEvento) {
    }

    /**
     * O QUE CADA RELOGIO ACERTA E ERRA, em todos os pares de eventos.
     *
     * <p>Os tres tipos de erro sao de gravidades muito diferentes, e por isso sao tres
     * colunas e nao uma.
     *
     * <p>Ordenar um par CONCORRENTE e o erro de Lamport, e ele e benigno em muitos usos:
     * quem so quer um desempate qualquer nao se importa. INVERTER causa e efeito e dizer
     * que a resposta chegou antes da pergunta, e disso nao ha uso que salve.
     */
    public static List<LinhaOrdem> aOrdem() {
        List<LinhaOrdem> linhas = new ArrayList<>();
        Execucao execucao = Execucao.sortear(PROCESSOS, EVENTOS, CHANCE_DE_ENVIAR, DESVIO, ATRASO_DA_REDE, 1);
        long[][] fecho = Juiz.fecho(execucao);

        Relogio.todos().forEach((nome, fabrica) -> {
            Relogio relogio = fabrica.apply(execucao);
            Juiz.Veredito veredito = Juiz.julgar(fecho, relogio, execucao);

            linhas.add(new LinhaOrdem(nome, veredito.pares(), veredito.concorrentesDeVerdade(),
                    veredito.fracaoCerta(), veredito.concorrentesChamadosDeOrdenados(),
                    veredito.ordenadosChamadosDeConcorrentes(), veredito.inversoes(),
                    veredito.fracaoInvertida(), relogio.numerosPorEvento()));
        });

        return linhas;
    }

    // ───────────────────── 2. a inversao ─────────────────────

    public record LinhaInversao(long desvio, long atrasoDaRede, String relogio,
                                long inversoes, long pares, double fracaoInvertida,
                                long piorInversaoEmPares) {
    }

    /**
     * A INVERSAO DE CAUSA E EFEITO do relogio de parede, conforme o desvio cresce.
     *
     * <p>Com desvio zero, o relogio de parede acerta a ordem de todo par causal: ele e so
     * o tempo andando. A partir do momento em que o desvio entre duas maquinas passa do
     * atraso da rede, um recebimento pode sair com carimbo menor do que o envio.
     *
     * <p>O numero da esquerda e em milissegundos. Desvio de dezenas de milissegundos entre
     * maquinas e o normal, e mensagem dentro do mesmo centro de dados chega em menos de
     * um.
     *
     * <p>A ultima coluna conta as inversoes do PAR DIRETO: um envio e o recebimento dele,
     * que e o caso em que o erro e mais obvio e mais indefensavel.
     */
    public static List<LinhaInversao> aInversao() {
        List<LinhaInversao> linhas = new ArrayList<>();

        for (long desvio : new long[] {0, 5, 20, 50, 200, 1_000}) {
            Execucao execucao = Execucao.sortear(PROCESSOS, EVENTOS, CHANCE_DE_ENVIAR,
                    desvio, ATRASO_DA_REDE, 1);

            long[][] fecho = Juiz.fecho(execucao);

            for (String nome : new String[] {"fisico", "hibrido"}) {
                Relogio relogio = Relogio.todos().get(nome).apply(execucao);
                Juiz.Veredito veredito = Juiz.julgar(fecho, relogio, execucao);

                linhas.add(new LinhaInversao(desvio, ATRASO_DA_REDE, nome,
                        veredito.inversoes(), veredito.pares(), veredito.fracaoInvertida(),
                        inversoesDoParDireto(execucao, relogio)));
            }
        }

        return linhas;
    }

    /** Quantos pares envio-recebimento o relogio coloca na ordem errada. */
    static long inversoesDoParDireto(Execucao execucao, Relogio relogio) {
        long quantas = 0;

        for (Execucao.Evento evento : execucao.eventos())
            if (evento.tipo() == Execucao.Tipo.RECEBIMENTO
                    && relogio.compara(evento.par(), evento.indice()) != Juiz.Relacao.ANTES) quantas++;

        return quantas;
    }

    // ───────────────────── 3. a concorrencia ─────────────────────

    public record LinhaConcorrencia(int processos, double chanceDeEnviar, int sementes,
                                    double mensagensEntregues, double fracaoConcorrente,
                                    double menorFracao, double maiorFracao,
                                    double acertoDeLamport) {
    }

    /** Quantas execucoes diferentes a tabela da concorrencia junta por linha. */
    public static final int SEMENTES = 12;

    /**
     * QUANTA CONCORRENCIA HA DE VERDADE, que e o denominador de tudo.
     *
     * <p>O erro de Lamport e chamar de ordenado um par concorrente, e o tamanho desse erro
     * depende de quantos pares concorrentes existem. Com um processo so nao ha nenhum e
     * Lamport acerta tudo; com muitos processos e poucas mensagens, quase todo par e
     * concorrente.
     *
     * <p>Cada linha junta {@link #SEMENTES} execucoes diferentes, e as colunas do MENOR e
     * do MAIOR estao ali para dizer quanto a linha varia. Com UMA execucao por linha, a
     * tabela saia nao monotona na chance de enviar, e eu quase escrevi um mecanismo para
     * explicar isso. Era espalhamento de uma amostra so.
     *
     * <p>A ultima coluna mostra as duas pontas: Lamport acerta cem por cento quando nao ha
     * concorrencia e despenca junto com ela.
     */
    public static List<LinhaConcorrencia> aConcorrencia() {
        List<LinhaConcorrencia> linhas = new ArrayList<>();

        for (int processos : new int[] {1, 2, 4, 8})
            for (double chance : new double[] {0.05, 0.35, 0.9}) {
                double somaDaFracao = 0;
                double somaDoAcerto = 0;
                double somaDasEntregues = 0;
                double menor = 1;
                double maior = 0;

                for (long semente = 1; semente <= SEMENTES; semente++) {
                    Execucao execucao = Execucao.sortear(processos, 180 / Math.max(1, processos),
                            chance, DESVIO, ATRASO_DA_REDE, semente);

                    long[][] fecho = Juiz.fecho(execucao);
                    Juiz.Veredito veredito = Juiz.julgar(fecho, new Relogio.Lamport(execucao), execucao);

                    double fracao = veredito.pares() == 0 ? 0
                            : (double) veredito.concorrentesDeVerdade() / veredito.pares();

                    somaDaFracao += fracao;
                    somaDoAcerto += veredito.fracaoCerta();
                    somaDasEntregues += execucao.mensagensEntregues();
                    menor = Math.min(menor, fracao);
                    maior = Math.max(maior, fracao);
                }

                linhas.add(new LinhaConcorrencia(processos, chance, SEMENTES,
                        somaDasEntregues / SEMENTES, somaDaFracao / SEMENTES,
                        menor, maior, somaDoAcerto / SEMENTES));
            }

        return linhas;
    }

    // ───────────────────── 4. o tamanho ─────────────────────

    public record LinhaTamanho(String relogio, int processos, int numerosPorEvento,
                               long bytesPorMensagem, double fracaoCerta, long inversoes) {
    }

    /**
     * O TAMANHO de cada relogio, e o que ele compra.
     *
     * <p>Um numero por evento, e ele erra a concorrencia. Um numero por PROCESSO por
     * evento, e ele acerta tudo. Dois numeros, e ele nao inverte causa e efeito mas
     * continua sem ver concorrencia.
     *
     * <p>A coluna dos bytes por mensagem e a que decide na pratica: o carimbo nao fica so
     * no evento, ele viaja em toda mensagem.
     */
    public static List<LinhaTamanho> oTamanho() {
        List<LinhaTamanho> linhas = new ArrayList<>();

        for (int processos : new int[] {4, 16, 64}) {
            Execucao execucao = Execucao.sortear(processos, Math.max(4, 240 / processos),
                    CHANCE_DE_ENVIAR, DESVIO, ATRASO_DA_REDE, 3);

            long[][] fecho = Juiz.fecho(execucao);

            Relogio.todos().forEach((nome, fabrica) -> {
                Relogio relogio = fabrica.apply(execucao);
                Juiz.Veredito veredito = Juiz.julgar(fecho, relogio, execucao);

                linhas.add(new LinhaTamanho(nome, processos, relogio.numerosPorEvento(),
                        8L * relogio.numerosPorEvento(), veredito.fracaoCerta(),
                        veredito.inversoes()));
            });
        }

        return linhas;
    }

    // ───────────────────── 5. a escala ─────────────────────

    public record LinhaEscala(int processos, long bytesDoVetorial, long bytesDoLamport,
                              double acertoDoVetorial, double acertoDoLamport,
                              double fracaoConcorrente) {
    }

    /**
     * O QUE ACONTECE QUANDO OS PROCESSOS CRESCEM.
     *
     * <p>O relogio vetorial acerta cem por cento em qualquer numero de processos, e o
     * tamanho dele cresce em linha reta com eles. O de Lamport fica do mesmo tamanho e
     * piora.
     *
     * <p>As duas colunas nao se cruzam nunca: nao existe um numero de processos a partir
     * do qual valha a pena trocar um pelo outro. E uma escolha entre duas coisas
     * diferentes, e nao um ponto de equilibrio.
     */
    public static List<LinhaEscala> aEscala() {
        List<LinhaEscala> linhas = new ArrayList<>();

        for (int processos : new int[] {2, 4, 8, 16, 32}) {
            Execucao execucao = Execucao.sortear(processos, Math.max(4, 320 / processos),
                    CHANCE_DE_ENVIAR, DESVIO, ATRASO_DA_REDE, 4);

            long[][] fecho = Juiz.fecho(execucao);

            Relogio vetorial = new Relogio.Vetorial(execucao);
            Relogio lamport = new Relogio.Lamport(execucao);

            Juiz.Veredito doVetorial = Juiz.julgar(fecho, vetorial, execucao);
            Juiz.Veredito doLamport = Juiz.julgar(fecho, lamport, execucao);

            linhas.add(new LinhaEscala(processos, 8L * processos, 8,
                    doVetorial.fracaoCerta(), doLamport.fracaoCerta(),
                    doLamport.pares() == 0 ? 0
                            : (double) doLamport.concorrentesDeVerdade() / doLamport.pares()));
        }

        return linhas;
    }
}
