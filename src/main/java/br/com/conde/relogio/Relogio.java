package br.com.conde.relogio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import br.com.conde.relogio.Execucao.Evento;
import br.com.conde.relogio.Execucao.Tipo;
import br.com.conde.relogio.Juiz.Relacao;

/**
 * UM RELOGIO: uma tentativa de descrever a causalidade de uma execucao com poucos bytes.
 *
 * <p>Todo relogio aqui carimba cada evento com alguma coisa e depois responde, olhando so
 * os dois carimbos, qual e a relacao entre dois eventos. Ele nao pode olhar a execucao: se
 * pudesse, seria o juiz.
 *
 * <p>Sao quatro, e eles respondem perguntas diferentes:
 *
 * <ul>
 *   <li>{@link Lamport} da um numero. Garante que causa vem antes de efeito, e nao
 *       consegue dizer que dois eventos sao concorrentes.
 *   <li>{@link Vetorial} da um numero por processo. Acerta TUDO, inclusive a
 *       concorrencia, e custa um numero por processo.
 *   <li>{@link Fisico} e o relogio de parede. Nao garante nada, e pode dizer que a
 *       resposta chegou antes da pergunta.
 *   <li>{@link Hibrido} junta os dois: um numero que parece um horario e nunca inverte
 *       causa e efeito.
 * </ul>
 */
public interface Relogio {

    /** O que este relogio acha da relacao entre os eventos {@code a} e {@code b}. */
    Relacao compara(int a, int b);

    /** Quantos numeros o relogio carimba em cada evento. */
    int numerosPorEvento();

    /** O nome, para as tabelas. */
    String nome();

    /**
     * O RELOGIO DE LAMPORT, de 1978: um contador por processo.
     *
     * <p>Cada evento soma um ao contador do processo. Um recebimento soma um ao MAIOR
     * entre o contador local e o que veio na mensagem.
     *
     * <p>Ele garante uma coisa e so uma: se a aconteceu antes de b, entao o carimbo de a
     * e menor que o de b. A volta NAO vale, e e esse o assunto do repositorio. Carimbo
     * menor nao quer dizer aconteceu antes: quer dizer "ou aconteceu antes, ou os dois
     * sao concorrentes e este foi o desempate que saiu".
     *
     * <p>Um numero por evento. E a resposta certa para quem quer um desempate qualquer e
     * a resposta errada para quem quer saber se dois eventos conflitam.
     */
    final class Lamport implements Relogio {

        private final long[] carimbo;

        public Lamport(Execucao execucao) {
            carimbo = new long[execucao.quantosEventos()];

            long[] contador = new long[execucao.processos()];

            for (Evento evento : execucao.eventos()) {
                long base = contador[evento.processo()];

                if (evento.tipo() == Tipo.RECEBIMENTO) base = Math.max(base, carimbo[evento.par()]);

                contador[evento.processo()] = base + 1;
                carimbo[evento.indice()] = base + 1;
            }
        }

        public long carimboDe(int evento) {
            return carimbo[evento];
        }

        @Override
        public Relacao compara(int a, int b) {
            if (carimbo[a] < carimbo[b]) return Relacao.ANTES;
            if (carimbo[a] > carimbo[b]) return Relacao.DEPOIS;

            /*
             * O EMPATE.
             *
             * Dois eventos com o mesmo carimbo de Lamport sao necessariamente
             * concorrentes: se um tivesse acontecido antes do outro, o carimbo teria
             * crescido. Entao o empate e a UNICA situacao em que este relogio consegue
             * afirmar concorrencia, e ele acerta sempre que afirma.
             *
             * E ele quase nunca afirma: com dez processos, os carimbos se espalham e o
             * empate fica raro. A tabela mede quao raro.
             */
            return Relacao.CONCORRENTE;
        }

        @Override
        public int numerosPorEvento() {
            return 1;
        }

        @Override
        public String nome() {
            return "Lamport";
        }
    }

    /**
     * O RELOGIO VETORIAL, de Fidge e Mattern, de 1988: um contador por PROCESSO, em cada
     * evento.
     *
     * <p>Cada processo soma um na propria posicao a cada evento. Um recebimento toma o
     * maximo, posicao por posicao, entre o vetor local e o que veio na mensagem.
     *
     * <p>E ele acerta TUDO: a aconteceu antes de b exatamente quando o vetor de a e menor
     * ou igual ao de b em todas as posicoes e diferente em alguma. Nao e aproximacao, e a
     * caracterizacao exata da relacao.
     *
     * <p>O preco e um numero por processo em cada evento e em cada mensagem. Com mil
     * processos, cada mensagem carrega mil numeros, e e por isso que quase ninguem usa.
     */
    final class Vetorial implements Relogio {

        private final int[][] carimbo;
        private final int processos;

        public Vetorial(Execucao execucao) {
            processos = execucao.processos();
            carimbo = new int[execucao.quantosEventos()][];

            int[][] atual = new int[processos][processos];

            for (Evento evento : execucao.eventos()) {
                int[] vetor = atual[evento.processo()];

                if (evento.tipo() == Tipo.RECEBIMENTO) {
                    int[] veioNaMensagem = carimbo[evento.par()];

                    for (int p = 0; p < processos; p++) vetor[p] = Math.max(vetor[p], veioNaMensagem[p]);
                }

                vetor[evento.processo()]++;
                carimbo[evento.indice()] = vetor.clone();
            }
        }

        public int[] carimboDe(int evento) {
            return carimbo[evento].clone();
        }

        @Override
        public Relacao compara(int a, int b) {
            boolean algumMenor = false;
            boolean algumMaior = false;

            for (int p = 0; p < processos; p++) {
                if (carimbo[a][p] < carimbo[b][p]) algumMenor = true;
                if (carimbo[a][p] > carimbo[b][p]) algumMaior = true;
            }

            if (algumMenor && !algumMaior) return Relacao.ANTES;
            if (algumMaior && !algumMenor) return Relacao.DEPOIS;

            return Relacao.CONCORRENTE;
        }

        @Override
        public int numerosPorEvento() {
            return processos;
        }

        @Override
        public String nome() {
            return "vetorial";
        }
    }

    /**
     * O RELOGIO DE PAREDE, que e o que todo mundo usa sem pensar.
     *
     * <p>Carimba cada evento com o que a maquina DAQUELE processo achava que eram as
     * horas. Nao ha coordenacao nenhuma: cada maquina tem o seu desvio.
     *
     * <p>Ele nao garante nada. Quando o desvio entre duas maquinas e maior do que o atraso
     * da rede, um recebimento pode sair com carimbo MENOR do que o envio, e o relogio diz
     * que a resposta chegou antes da pergunta.
     *
     * <p>Esse erro nao e teorico nem raro. Desvio de dezenas de milissegundos entre
     * maquinas e o normal, e mensagem dentro do mesmo centro de dados chega em menos de
     * um. A tabela da inversao mede.
     */
    final class Fisico implements Relogio {

        private final long[] carimbo;

        public Fisico(Execucao execucao) {
            carimbo = new long[execucao.quantosEventos()];

            for (Evento evento : execucao.eventos()) carimbo[evento.indice()] = evento.instanteFisico();
        }

        public long carimboDe(int evento) {
            return carimbo[evento];
        }

        @Override
        public Relacao compara(int a, int b) {
            if (carimbo[a] < carimbo[b]) return Relacao.ANTES;
            if (carimbo[a] > carimbo[b]) return Relacao.DEPOIS;

            return Relacao.CONCORRENTE;
        }

        @Override
        public int numerosPorEvento() {
            return 1;
        }

        @Override
        public String nome() {
            return "fisico";
        }
    }

    /**
     * O RELOGIO HIBRIDO, de Kulkarni e outros, de 2014: o de parede com um contador de
     * desempate colado.
     *
     * <p>Guarda dois numeros. O primeiro e o maior horario de parede ja visto, do proprio
     * relogio ou de alguma mensagem. O segundo e um contador que so anda quando o primeiro
     * nao andou.
     *
     * <p>Com isso ele nunca inverte causa e efeito, como o de Lamport, e continua PARECIDO
     * com um horario, ao contrario do de Lamport. Um carimbo hibrido pode ser lido como
     * "mais ou menos tres da tarde" e um carimbo de Lamport nao.
     *
     * <p>O que ele NAO faz e detectar concorrencia, pelo mesmo motivo que o de Lamport nao
     * faz: dois numeros nao cabem a informacao de quem soube o que.
     */
    final class Hibrido implements Relogio {

        private final long[] fisico;
        private final long[] contador;

        public Hibrido(Execucao execucao) {
            int n = execucao.quantosEventos();

            fisico = new long[n];
            contador = new long[n];

            long[] ultimoFisico = new long[execucao.processos()];
            long[] ultimoContador = new long[execucao.processos()];

            for (Evento evento : execucao.eventos()) {
                int p = evento.processo();
                long agora = evento.instanteFisico();
                long anteriorFisico = ultimoFisico[p];
                long anteriorContador = ultimoContador[p];

                long daMensagemFisico = 0;
                long daMensagemContador = 0;

                if (evento.tipo() == Tipo.RECEBIMENTO) {
                    daMensagemFisico = fisico[evento.par()];
                    daMensagemContador = contador[evento.par()];
                }

                long maior = Math.max(agora, Math.max(anteriorFisico, daMensagemFisico));

                long novoContador;

                if (maior == anteriorFisico && maior == daMensagemFisico)
                    novoContador = Math.max(anteriorContador, daMensagemContador) + 1;
                else if (maior == anteriorFisico) novoContador = anteriorContador + 1;
                else if (maior == daMensagemFisico) novoContador = daMensagemContador + 1;
                else novoContador = 0;

                ultimoFisico[p] = maior;
                ultimoContador[p] = novoContador;

                fisico[evento.indice()] = maior;
                contador[evento.indice()] = novoContador;
            }
        }

        public long[] carimboDe(int evento) {
            return new long[] {fisico[evento], contador[evento]};
        }

        @Override
        public Relacao compara(int a, int b) {
            if (fisico[a] != fisico[b]) return fisico[a] < fisico[b] ? Relacao.ANTES : Relacao.DEPOIS;
            if (contador[a] != contador[b]) return contador[a] < contador[b] ? Relacao.ANTES : Relacao.DEPOIS;

            return Relacao.CONCORRENTE;
        }

        @Override
        public int numerosPorEvento() {
            return 2;
        }

        @Override
        public String nome() {
            return "hibrido";
        }
    }

    /** Os quatro, na ordem em que as tabelas os mostram. */
    static Map<String, Function<Execucao, Relogio>> todos() {
        Map<String, Function<Execucao, Relogio>> mapa = new LinkedHashMap<>();

        mapa.put("Lamport", Lamport::new);
        mapa.put("vetorial", Vetorial::new);
        mapa.put("fisico", Fisico::new);
        mapa.put("hibrido", Hibrido::new);

        return mapa;
    }
}
