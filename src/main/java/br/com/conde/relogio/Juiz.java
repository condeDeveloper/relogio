package br.com.conde.relogio;

import java.util.ArrayList;
import java.util.List;

import br.com.conde.relogio.Execucao.Evento;
import br.com.conde.relogio.Execucao.Tipo;

/**
 * O JUIZ: o FECHO TRANSITIVO do aconteceu-antes.
 *
 * <p>A relacao de causalidade nao precisa de relogio nenhum para existir. Ela sai das
 * tres regras de Lamport, de 1978: dois eventos do mesmo processo estao em ordem, um
 * envio esta antes do recebimento correspondente, e a relacao e transitiva.
 *
 * <p>Este juiz monta essa relacao inteira, por forca bruta, e depois pergunta a cada
 * relogio o que ele acha de cada par. Nao ha aproximacao nem amostragem: para n eventos,
 * ele responde todos os n vezes n menos um sobre dois pares.
 *
 * <p>A resposta certa para um par tem tres valores e nao dois. Um evento pode ter
 * acontecido antes do outro, depois, ou NENHUM DOS DOIS. Concorrente nao quer dizer
 * "simultaneo" nem "nao sei qual veio primeiro": quer dizer que a pergunta nao tem
 * resposta, porque nenhuma informacao foi de um para o outro.
 */
public final class Juiz {

    private Juiz() {
    }

    /** O teto de eventos que o fecho transitivo aceita montar. */
    public static final int TETO = 4_000;

    /** O que o juiz, ou um relogio, diz sobre um par de eventos. */
    public enum Relacao {
        ANTES, DEPOIS, CONCORRENTE
    }

    /**
     * O FECHO TRANSITIVO, em mascaras de bits.
     *
     * <p>Devolve, para cada evento, o conjunto dos eventos que aconteceram antes dele.
     *
     * <p>A conta e em ordem crescente de indice, e isso so vale porque TODA aresta de
     * causalidade vai de um indice menor para um maior: o evento anterior do mesmo
     * processo foi criado antes, e um envio foi criado antes do recebimento dele. Se
     * alguma aresta andasse para tras, o fecho sairia incompleto em silencio, e por isso
     * ha um teste so sobre isso.
     */
    public static long[][] fecho(Execucao execucao) {
        int n = execucao.quantosEventos();

        if (n > TETO)
            throw new IllegalArgumentException("o fecho so vai ate " + TETO + " eventos, e vieram " + n);

        int palavras = (n + 63) / 64;
        long[][] antes = new long[n][palavras];
        int[] anteriorNoProcesso = new int[execucao.processos()];

        java.util.Arrays.fill(anteriorNoProcesso, -1);

        for (Evento evento : execucao.eventos()) {
            int i = evento.indice();
            List<Integer> pais = new ArrayList<>();

            int anterior = anteriorNoProcesso[evento.processo()];

            if (anterior >= 0) pais.add(anterior);
            if (evento.tipo() == Tipo.RECEBIMENTO) pais.add(evento.par());

            for (int pai : pais) {
                if (pai >= i) throw new IllegalStateException("aresta para tras: " + pai + " para " + i);

                for (int w = 0; w < palavras; w++) antes[i][w] |= antes[pai][w];

                antes[i][pai >>> 6] |= 1L << (pai & 63);
            }

            anteriorNoProcesso[evento.processo()] = i;
        }

        return antes;
    }

    /** O evento {@code a} aconteceu antes do {@code b}, de verdade? */
    public static boolean aconteceuAntes(long[][] fecho, int a, int b) {
        return (fecho[b][a >>> 6] & 1L << (a & 63)) != 0;
    }

    /** A relacao VERDADEIRA entre dois eventos. */
    public static Relacao verdade(long[][] fecho, int a, int b) {
        if (aconteceuAntes(fecho, a, b)) return Relacao.ANTES;
        if (aconteceuAntes(fecho, b, a)) return Relacao.DEPOIS;

        return Relacao.CONCORRENTE;
    }

    /**
     * O VEREDITO sobre um relogio: o que ele acertou e, dos erros, de que tipo foram.
     *
     * @param pares quantos pares de eventos foram perguntados
     * @param acertos em quantos o relogio deu a resposta certa
     * @param concorrentesChamadosDeOrdenados pares que NAO tem ordem e o relogio ordenou
     * @param ordenadosChamadosDeConcorrentes pares que tem ordem e o relogio nao viu
     * @param inversoes pares em que o relogio inverteu CAUSA E EFEITO
     * @param concorrentesDeVerdade quantos dos pares sao concorrentes de verdade
     */
    public record Veredito(long pares, long acertos,
                           long concorrentesChamadosDeOrdenados,
                           long ordenadosChamadosDeConcorrentes,
                           long inversoes, long concorrentesDeVerdade) {

        public double fracaoCerta() {
            return pares == 0 ? 0 : (double) acertos / pares;
        }

        /** Que fracao dos pares CONCORRENTES o relogio ordenou sem ter direito. */
        public double fracaoDosConcorrentesOrdenada() {
            return concorrentesDeVerdade == 0 ? 0
                    : (double) concorrentesChamadosDeOrdenados / concorrentesDeVerdade;
        }

        /** Que fracao dos pares o relogio inverteu, que e o erro que nao tem desculpa. */
        public double fracaoInvertida() {
            return pares == 0 ? 0 : (double) inversoes / pares;
        }
    }

    /**
     * Julga um relogio contra o fecho transitivo, em TODOS os pares de eventos.
     *
     * <p>Os tres tipos de erro sao de gravidades muito diferentes, e por isso sao tres
     * colunas e nao uma.
     *
     * <p>Chamar de ordenado um par CONCORRENTE e o erro de Lamport, e ele e benigno em
     * muitos usos: quem so quer um desempate qualquer nao se importa. Nao ver a ordem de
     * um par ordenado e perder informacao. INVERTER causa e efeito e dizer que a
     * resposta chegou antes da pergunta, e disso nao ha uso que salve.
     */
    public static Veredito julgar(long[][] fecho, Relogio relogio, Execucao execucao) {
        int n = execucao.quantosEventos();
        long pares = 0;
        long acertos = 0;
        long ordenouConcorrente = 0;
        long perdeuOrdem = 0;
        long inverteu = 0;
        long concorrentes = 0;

        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++) {
                pares++;

                Relacao daVerdade = verdade(fecho, a, b);
                Relacao doRelogio = relogio.compara(a, b);

                if (daVerdade == Relacao.CONCORRENTE) concorrentes++;

                if (daVerdade == doRelogio) {
                    acertos++;
                    continue;
                }

                if (daVerdade == Relacao.CONCORRENTE) ordenouConcorrente++;
                else if (doRelogio == Relacao.CONCORRENTE) perdeuOrdem++;
                else inverteu++;
            }

        return new Veredito(pares, acertos, ordenouConcorrente, perdeuOrdem, inverteu, concorrentes);
    }
}
