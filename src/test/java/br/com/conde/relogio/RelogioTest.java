package br.com.conde.relogio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.conde.relogio.Juiz.Relacao;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Os testes dos quatro RELOGIOS contra o fecho transitivo.
 *
 * <p>Cada relogio promete uma coisa diferente, e cada promessa e um teste diferente. O
 * vetorial promete acertar tudo; o de Lamport promete so que causa vem antes de efeito; o
 * hibrido promete isso e mais parecer um horario; o de parede nao promete nada.
 */
class RelogioTest {

    /**
     * A PROMESSA DO RELOGIO VETORIAL: ele acerta TODOS os pares, em toda execucao.
     *
     * <p>Nao e aproximacao. A comparacao componente a componente e a caracterizacao exata
     * da relacao de aconteceu-antes, e por isso o teste e de igualdade com o fecho e nao
     * de proximidade.
     */
    @Test
    @DisplayName("o relogio vetorial acerta todos os pares, em toda execucao")
    void oVetorialAcertaTudo() {
        for (int processos : new int[] {1, 2, 5, 10})
            for (long semente = 1; semente <= 8; semente++) {
                Execucao execucao = Execucao.sortear(processos, 15, 0.4, 50, 20, semente);
                long[][] fecho = Juiz.fecho(execucao);

                Juiz.Veredito veredito = Juiz.julgar(fecho, new Relogio.Vetorial(execucao), execucao);

                assertThat(veredito.fracaoCerta())
                        .as("%d processos, semente %d", processos, semente).isEqualTo(1.0);
                assertThat(veredito.acertos()).isEqualTo(veredito.pares());
            }
    }

    /**
     * A PROMESSA DE LAMPORT, que e uma so: se a aconteceu antes de b, entao o carimbo de a
     * e menor que o de b.
     *
     * <p>A volta NAO vale, e e esse o assunto do repositorio.
     */
    @Test
    @DisplayName("em Lamport, causa sempre tem carimbo menor que efeito, e a volta nao vale")
    void aPromessaDeLamport() {
        Execucao execucao = Execucao.sortear(6, 25, 0.4, 50, 20, 3);
        long[][] fecho = Juiz.fecho(execucao);
        Relogio.Lamport lamport = new Relogio.Lamport(execucao);

        int n = execucao.quantosEventos();
        int carimboMenorSemCausalidade = 0;

        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++) {
                if (a == b) continue;

                if (Juiz.aconteceuAntes(fecho, a, b))
                    assertThat(lamport.carimboDe(a)).as("%d antes de %d", a, b)
                            .isLessThan(lamport.carimboDe(b));

                if (lamport.carimboDe(a) < lamport.carimboDe(b) && !Juiz.aconteceuAntes(fecho, a, b))
                    carimboMenorSemCausalidade++;
            }

        // e a volta falha em milhares de pares: carimbo menor nao quer dizer antes
        assertThat(carimboMenorSemCausalidade).isGreaterThan(1_000);
    }

    /**
     * Lamport nunca INVERTE e nunca perde uma ordem: o unico erro dele e ordenar o que
     * nao tem ordem.
     */
    @Test
    @DisplayName("Lamport nunca inverte causa e efeito e nunca deixa de ver uma ordem")
    void lamportSoErraParaUmLado() {
        for (long semente = 1; semente <= 8; semente++) {
            Execucao execucao = Execucao.sortear(6, 25, 0.4, 100, 20, semente);
            long[][] fecho = Juiz.fecho(execucao);

            Juiz.Veredito veredito = Juiz.julgar(fecho, new Relogio.Lamport(execucao), execucao);

            assertThat(veredito.inversoes()).as("semente %d", semente).isZero();
            assertThat(veredito.ordenadosChamadosDeConcorrentes()).as("semente %d", semente).isZero();
            assertThat(veredito.concorrentesChamadosDeOrdenados()).as("semente %d", semente).isPositive();
        }
    }

    /**
     * O EMPATE DE LAMPORT e a unica situacao em que ele consegue afirmar concorrencia, e
     * quando afirma, acerta.
     *
     * <p>Dois eventos com o mesmo carimbo sao necessariamente concorrentes: se um tivesse
     * acontecido antes do outro, o carimbo teria crescido.
     */
    @Test
    @DisplayName("quando Lamport diz concorrente, ele esta certo")
    void oEmpateDeLamportNuncaErra() {
        Execucao execucao = Execucao.sortear(6, 25, 0.4, 50, 20, 3);
        long[][] fecho = Juiz.fecho(execucao);
        Relogio lamport = new Relogio.Lamport(execucao);

        int n = execucao.quantosEventos();
        int disseConcorrente = 0;

        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++)
                if (lamport.compara(a, b) == Relacao.CONCORRENTE) {
                    disseConcorrente++;

                    assertThat(Juiz.verdade(fecho, a, b)).as("%d e %d", a, b)
                            .isEqualTo(Relacao.CONCORRENTE);
                }

        assertThat(disseConcorrente).as("ele diz isso de vez em quando").isPositive();
    }

    /**
     * A PROMESSA DO HIBRIDO: nunca inverte causa e efeito, qualquer que seja o desvio dos
     * relogios de parede.
     */
    @Test
    @DisplayName("o hibrido nunca inverte causa e efeito, com desvio de ate dez segundos")
    void oHibridoNuncaInverte() {
        for (long desvio : new long[] {0, 50, 1_000, 10_000})
            for (long semente = 1; semente <= 5; semente++) {
                Execucao execucao = Execucao.sortear(5, 25, 0.4, desvio, 20, semente);
                long[][] fecho = Juiz.fecho(execucao);

                Juiz.Veredito veredito = Juiz.julgar(fecho, new Relogio.Hibrido(execucao), execucao);

                assertThat(veredito.inversoes())
                        .as("desvio %d, semente %d", desvio, semente).isZero();
            }
    }

    /**
     * E O QUE O HIBRIDO COMPRA com o segundo numero: o carimbo dele continua PARECIDO com
     * um horario, ao contrario do de Lamport.
     *
     * <p>Um carimbo hibrido pode ser lido como "mais ou menos tres da tarde"; um carimbo
     * de Lamport nao pode ser lido como nada.
     */
    @Test
    @DisplayName("o carimbo hibrido fica perto do horario de parede e o de Lamport nao")
    void oHibridoPareceUmHorario() {
        Execucao execucao = Execucao.sortear(5, 30, 0.4, 50, 20, 2);
        Relogio.Hibrido hibrido = new Relogio.Hibrido(execucao);

        for (Execucao.Evento evento : execucao.eventos()) {
            long[] carimbo = hibrido.carimboDe(evento.indice());

            // o hibrido nunca fica ATRAS do relogio de parede da propria maquina
            assertThat(carimbo[0]).as("evento %d", evento.indice())
                    .isGreaterThanOrEqualTo(evento.instanteFisico());

            // e nunca se afasta dele mais do que o maior desvio entre as maquinas
            assertThat(carimbo[0] - evento.instanteFisico()).as("evento %d", evento.indice())
                    .isLessThanOrEqualTo(200);
        }
    }

    /**
     * O RELOGIO DE PAREDE nao promete nada, e isso aparece: com desvio maior do que o
     * atraso da rede, ele diz que a resposta chegou antes da pergunta.
     */
    @Test
    @DisplayName("o relogio de parede inverte envio e recebimento quando o desvio passa do atraso da rede")
    void oParedeInverteComDesvioGrande() {
        Execucao execucao = Execucao.sortear(5, 30, 0.5, 1_000, 20, 1);
        long[][] fecho = Juiz.fecho(execucao);
        Relogio fisico = new Relogio.Fisico(execucao);

        Juiz.Veredito veredito = Juiz.julgar(fecho, fisico, execucao);

        assertThat(veredito.inversoes()).isPositive();
        assertThat(Medidas.inversoesDoParDireto(execucao, fisico))
                .as("pares envio e recebimento invertidos").isPositive();

        // e com desvio menor do que o atraso da rede ele nao inverte nada
        Execucao calma = Execucao.sortear(5, 30, 0.5, 5, 20, 1);

        assertThat(Juiz.julgar(Juiz.fecho(calma), new Relogio.Fisico(calma), calma).inversoes())
                .isZero();
    }

    @Test
    @DisplayName("os quatro relogios dizem o tamanho que ocupam e o nome que tem")
    void osQuatroSeApresentam() {
        Execucao execucao = Execucao.sortear(7, 10, 0.3, 50, 20, 1);

        assertThat(new Relogio.Lamport(execucao).numerosPorEvento()).isEqualTo(1);
        assertThat(new Relogio.Vetorial(execucao).numerosPorEvento()).isEqualTo(7);
        assertThat(new Relogio.Fisico(execucao).numerosPorEvento()).isEqualTo(1);
        assertThat(new Relogio.Hibrido(execucao).numerosPorEvento()).isEqualTo(2);

        Relogio.todos().forEach((nome, fabrica) ->
                assertThat(fabrica.apply(execucao).nome()).isEqualTo(nome));
    }

    /**
     * O VETORIAL de um processo so e um contador, e nesse caso ele e identico ao de
     * Lamport. Os dois acertam tudo, porque nao ha concorrencia nenhuma.
     */
    @Test
    @DisplayName("com um processo so os quatro relogios acertam tudo")
    void comUmProcessoTodosAcertam() {
        Execucao execucao = Execucao.sortear(1, 50, 0.5, 100, 20, 1);
        long[][] fecho = Juiz.fecho(execucao);

        Relogio.todos().forEach((nome, fabrica) ->
                assertThat(Juiz.julgar(fecho, fabrica.apply(execucao), execucao).fracaoCerta())
                        .as(nome).isEqualTo(1.0));
    }

    @Test
    @DisplayName("o carimbo vetorial de um evento soma o que ele sabe de cada processo")
    void oCarimboVetorialSoma() {
        Execucao execucao = Execucao.sortear(3, 20, 0.5, 50, 20, 4);
        Relogio.Vetorial vetorial = new Relogio.Vetorial(execucao);

        for (Execucao.Evento evento : execucao.eventos()) {
            int[] carimbo = vetorial.carimboDe(evento.indice());

            // a posicao do proprio processo e o numero do evento dentro dele
            assertThat(carimbo[evento.processo()]).as("evento %d", evento.indice())
                    .isEqualTo(evento.posicaoNoProcesso() + 1);

            // e nenhuma posicao anda para tras
            for (int p = 0; p < execucao.processos(); p++)
                assertThat(carimbo[p]).as("evento %d, posicao %d", evento.indice(), p)
                        .isNotNegative();
        }
    }
}
