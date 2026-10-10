package br.com.conde.relogio;

import java.util.ArrayList;
import java.util.List;

/**
 * UMA EXECUCAO DISTRIBUIDA: processos que fazem coisas e trocam mensagens.
 *
 * <p>Cada evento e uma de tres coisas. Um evento LOCAL, que so acontece. Um ENVIO, que
 * manda uma mensagem. Um RECEBIMENTO, que recebe uma mensagem enviada antes.
 *
 * <p>Isso define a relacao de ACONTECEU ANTES de Lamport, de 1978, e ela nao precisa de
 * relogio nenhum para existir:
 *
 * <ul>
 *   <li>dois eventos no mesmo processo estao em ordem, pela ordem em que aconteceram;
 *   <li>um envio aconteceu antes do recebimento correspondente;
 *   <li>e a relacao e transitiva.
 * </ul>
 *
 * <p>Dois eventos que nao estao nessa relacao, nem num sentido nem no outro, sao
 * CONCORRENTES. Nao e que nao se saiba qual veio primeiro: e que a pergunta nao tem
 * resposta, porque nenhuma informacao foi de um para o outro.
 *
 * <p>Esta classe e o FATO. Os relogios sao tentativas de descrever este fato com poucos
 * bytes, e o juiz compara cada tentativa com ele.
 */
public final class Execucao {

    /** O que um evento e. */
    public enum Tipo {
        LOCAL, ENVIO, RECEBIMENTO
    }

    /**
     * Um evento da execucao.
     *
     * @param indice a posicao deste evento na lista global, so para identifica-lo
     * @param processo em qual processo ele aconteceu
     * @param posicaoNoProcesso o quantos-esimo evento daquele processo ele e
     * @param tipo local, envio ou recebimento
     * @param par o indice do envio correspondente, num recebimento, ou menos um
     * @param instanteFisico o que o relogio de parede DAQUELA maquina marcava
     */
    public record Evento(int indice, int processo, int posicaoNoProcesso, Tipo tipo,
                         int par, long instanteFisico) {
    }

    private final int processos;
    private final List<Evento> eventos;

    private Execucao(int processos, List<Evento> eventos) {
        this.processos = processos;
        this.eventos = List.copyOf(eventos);
    }

    public int processos() {
        return processos;
    }

    public List<Evento> eventos() {
        return eventos;
    }

    public int quantosEventos() {
        return eventos.size();
    }

    /**
     * Quantas MENSAGENS foram de fato entregues.
     *
     * <p>E este o numero que cria causalidade, e nao a chance de enviar. Um processo que
     * tem mensagem para receber RECEBE em vez de enviar, entao subir a chance de enviar
     * acima de um certo ponto so enche a fila: as entregas nao crescem junto.
     */
    public int mensagensEntregues() {
        int quantas = 0;

        for (Evento evento : eventos) if (evento.tipo() == Tipo.RECEBIMENTO) quantas++;

        return quantas;
    }

    /**
     * Os eventos de cada processo, na ordem em que aconteceram nele.
     *
     * <p>E a primeira das tres regras do aconteceu-antes, materializada.
     */
    public List<List<Integer>> porProcesso() {
        List<List<Integer>> saida = new ArrayList<>();

        for (int p = 0; p < processos; p++) saida.add(new ArrayList<>());

        for (Evento evento : eventos) saida.get(evento.processo()).add(evento.indice());

        return saida;
    }

    /**
     * MONTA uma execucao sorteada, com semente fixa.
     *
     * <p>A integracao continua roda em tres sistemas, e "o relogio de Lamport errou 38%
     * dos pares" com execucoes diferentes em cada sistema nao e uma medida.
     *
     * @param desvioDoRelogio quanto o relogio de parede de cada maquina esta adiantado
     *     ou atrasado, em milissegundos
     * @param atrasoDaRede quanto uma mensagem demora para chegar, em milissegundos
     */
    public static Execucao sortear(int processos, int eventosPorProcesso, double chanceDeEnviar,
                                   long desvioDoRelogio, long atrasoDaRede, long semente) {

        if (processos < 1) throw new IllegalArgumentException("precisa de ao menos um processo");
        if (eventosPorProcesso < 1) throw new IllegalArgumentException("precisa de ao menos um evento");

        Sorteio sorteio = new Sorteio(semente);

        /*
         * O TEMPO VERDADEIRO.
         *
         * Ha um relogio so, o do universo, e ele anda para a frente e nunca para tras.
         * O que cada maquina MARCA e esse tempo mais o desvio dela, e esse desvio e o
         * erro do relogio, nao do universo.
         *
         * A primeira versao disto nao tinha tempo verdadeiro nenhum: cada processo tinha
         * um contador proprio e eu os comparava como se fossem o mesmo relogio. Com isso,
         * uma mensagem podia ser entregue antes de ter sido enviada, e a tabela mostrava
         * o relogio de parede invertendo causa e efeito com desvio ZERO. Com desvio zero
         * o relogio de parede E o tempo verdadeiro, e ele nao pode inverter nada.
         */
        long agora = 0;

        long[] desvio = new long[processos];

        for (int p = 0; p < processos; p++)
            desvio[p] = desvioDoRelogio == 0 ? 0 : sorteio.ate(2 * desvioDoRelogio + 1) - desvioDoRelogio;

        int[] quantosNoProcesso = new int[processos];
        List<Evento> eventos = new ArrayList<>();

        // mensagens a caminho, por processo de destino: o indice do envio e quando chega
        List<List<long[]>> aCaminho = new ArrayList<>();

        for (int p = 0; p < processos; p++) aCaminho.add(new ArrayList<>());

        for (int passo = 0; passo < eventosPorProcesso * processos; passo++) {
            agora += 1 + sorteio.ate(10);

            int processo = sorteio.ate(processos);

            // entregar primeiro o que ja chegou: um recebimento so pode acontecer depois
            // de o atraso da rede ter passado NO TEMPO VERDADEIRO
            List<long[]> fila = aCaminho.get(processo);
            int entregavel = -1;

            for (int i = 0; i < fila.size(); i++)
                if (fila.get(i)[1] <= agora) {
                    entregavel = i;
                    break;
                }

            if (entregavel >= 0) {
                long[] mensagem = fila.remove(entregavel);

                eventos.add(new Evento(eventos.size(), processo, quantosNoProcesso[processo]++,
                        Tipo.RECEBIMENTO, (int) mensagem[0], agora + desvio[processo]));

                continue;
            }

            boolean envia = processos > 1 && sorteio.fracao() < chanceDeEnviar;

            eventos.add(new Evento(eventos.size(), processo, quantosNoProcesso[processo]++,
                    envia ? Tipo.ENVIO : Tipo.LOCAL, -1, agora + desvio[processo]));

            if (envia) {
                int destino = sorteio.ate(processos - 1);

                if (destino >= processo) destino++;

                aCaminho.get(destino).add(new long[] {eventos.size() - 1, agora + atrasoDaRede});
            }
        }

        /*
         * O ESCOAMENTO: continuar rodando, so entregando, ate nenhum envio ficar sem par.
         *
         * Isto e o MESMO laco de cima com os envios desligados, e nao um despejo em
         * bloco. A primeira versao despejava: percorria processo por processo e punha
         * todos os recebimentos que faltavam, em sequencia, no fim da lista.
         *
         * Esse bloco no fim inventava concorrencia que a execucao nao tinha. Recebimentos
         * de processos diferentes, empilhados um atras do outro sem tempo passando entre
         * eles, sao todos concorrentes entre si, e com chance de envio alta eles eram
         * centenas. A tabela da concorrencia saia NAO MONOTONA: mandar mais mensagens
         * aumentava a concorrencia medida, quando mensagem e justamente o que cria
         * causalidade.
         */
        while (temMensagemACaminho(aCaminho)) {
            agora += 1 + sorteio.ate(10);

            int processo = sorteio.ate(processos);
            List<long[]> fila = aCaminho.get(processo);
            int entregavel = -1;

            for (int i = 0; i < fila.size(); i++)
                if (fila.get(i)[1] <= agora) {
                    entregavel = i;
                    break;
                }

            if (entregavel < 0) continue;

            long[] mensagem = fila.remove(entregavel);

            eventos.add(new Evento(eventos.size(), processo, quantosNoProcesso[processo]++,
                    Tipo.RECEBIMENTO, (int) mensagem[0], agora + desvio[processo]));
        }

        return new Execucao(processos, eventos);
    }

    private static boolean temMensagemACaminho(List<List<long[]>> aCaminho) {
        for (List<long[]> fila : aCaminho) if (!fila.isEmpty()) return true;

        return false;
    }

    /**
     * O CONGRUENTE LINEAR do repositorio, com o descarte dos bits de baixo.
     *
     * <p>O deslocamento de 32 bits tira os bits que alternam par e impar a cada passo.
     * Sem ele, o sorteio de qual processo age alternaria em xadrez, os processos se
     * revezariam em ordem fixa e a execucao sorteada seria uma execucao em turnos
     * disfarcada. Em turnos ha muito menos concorrencia, e a tabela mediria outra coisa.
     */
    public static final class Sorteio {

        private long estado;

        public Sorteio(long semente) {
            this.estado = semente == 0 ? 1 : semente;
        }

        public int proximo() {
            estado = estado * 6364136223846793005L + 1442695040888963407L;

            return (int) (estado >>> 32);
        }

        public int ate(long limite) {
            if (limite <= 0) throw new IllegalArgumentException("o limite tem de ser positivo");

            return (int) (Integer.toUnsignedLong(proximo()) % limite);
        }

        public double fracao() {
            return Integer.toUnsignedLong(proximo()) / 4294967296.0;
        }
    }
}
