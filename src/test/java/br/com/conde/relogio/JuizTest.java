package br.com.conde.relogio;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.conde.relogio.Execucao.Evento;
import br.com.conde.relogio.Execucao.Tipo;
import br.com.conde.relogio.Juiz.Relacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Os testes do JUIZ e da EXECUCAO.
 *
 * <p>O juiz e a peca de que todas as tabelas dependem, e ele nao tem com que ser
 * comparado alem das tres regras de Lamport escritas a mao. Este arquivo escreve
 * execucoes pequenas inteiras e afirma a relacao de cada par.
 */
class JuizTest {

    /**
     * A EXECUCAO MINIMA que tem concorrencia: dois processos, um evento cada, nenhuma
     * mensagem.
     */
    @Test
    @DisplayName("dois eventos em processos diferentes sem mensagem nenhuma sao concorrentes")
    void aConcorrenciaMinima() {
        Execucao execucao = Execucao.sortear(2, 1, 0, 0, 10, 1);
        long[][] fecho = Juiz.fecho(execucao);

        assertThat(execucao.quantosEventos()).isEqualTo(2);
        assertThat(execucao.eventos().get(0).processo())
                .isNotEqualTo(execucao.eventos().get(1).processo());
        assertThat(Juiz.verdade(fecho, 0, 1)).isEqualTo(Relacao.CONCORRENTE);
    }

    @Test
    @DisplayName("dois eventos no mesmo processo estao em ordem, pela ordem em que aconteceram")
    void aOrdemDentroDoProcesso() {
        Execucao execucao = Execucao.sortear(1, 5, 0, 0, 10, 1);
        long[][] fecho = Juiz.fecho(execucao);

        for (int a = 0; a < 5; a++)
            for (int b = a + 1; b < 5; b++) {
                assertThat(Juiz.verdade(fecho, a, b)).as("%d e %d", a, b).isEqualTo(Relacao.ANTES);
                assertThat(Juiz.verdade(fecho, b, a)).as("%d e %d", b, a).isEqualTo(Relacao.DEPOIS);
            }
    }

    /**
     * A SEGUNDA REGRA: um envio aconteceu antes do recebimento correspondente.
     *
     * <p>Em TODA execucao, de TODO tamanho. Se alguma vez nao valer, o gerador de
     * execucoes esta entregando mensagens antes de envia-las, e todas as tabelas estao
     * medindo um universo impossivel.
     */
    @Test
    @DisplayName("todo envio aconteceu antes do recebimento dele, em toda execucao")
    void oEnvioVemAntesDoRecebimento() {
        for (int processos : new int[] {2, 4, 8})
            for (long semente = 1; semente <= 10; semente++) {
                Execucao execucao = Execucao.sortear(processos, 20, 0.5, 50, 20, semente);
                long[][] fecho = Juiz.fecho(execucao);

                for (Evento evento : execucao.eventos())
                    if (evento.tipo() == Tipo.RECEBIMENTO) {
                        assertThat(evento.par()).as("semente %d", semente).isGreaterThanOrEqualTo(0);
                        assertThat(Juiz.verdade(fecho, evento.par(), evento.indice()))
                                .as("envio %d e recebimento %d", evento.par(), evento.indice())
                                .isEqualTo(Relacao.ANTES);
                    }
            }
    }

    /**
     * O TEMPO VERDADEIRO anda para a frente, e os carimbos de parede de um MESMO processo
     * tambem.
     *
     * <p>A primeira versao do gerador nao tinha tempo verdadeiro nenhum: cada processo
     * tinha um contador proprio e eu os comparava como se fossem o mesmo relogio. Com
     * isso, uma mensagem podia ser entregue antes de ter sido enviada, e a tabela mostrava
     * o relogio de parede invertendo causa e efeito com desvio ZERO.
     */
    @Test
    @DisplayName("com desvio zero o carimbo de parede respeita toda a causalidade")
    void comDesvioZeroOParedeNaoInverte() {
        for (long semente = 1; semente <= 10; semente++) {
            Execucao execucao = Execucao.sortear(5, 30, 0.4, 0, 20, semente);
            long[][] fecho = Juiz.fecho(execucao);
            Relogio fisico = new Relogio.Fisico(execucao);

            Juiz.Veredito veredito = Juiz.julgar(fecho, fisico, execucao);

            assertThat(veredito.inversoes()).as("semente %d", semente).isZero();
        }
    }

    @Test
    @DisplayName("os carimbos de parede de um mesmo processo so crescem")
    void oCarimboDeUmProcessoSoCresce() {
        Execucao execucao = Execucao.sortear(5, 30, 0.4, 100, 20, 3);
        List<List<Integer>> porProcesso = execucao.porProcesso();

        for (int p = 0; p < execucao.processos(); p++) {
            List<Integer> eventos = porProcesso.get(p);

            for (int i = 1; i < eventos.size(); i++)
                assertThat(execucao.eventos().get(eventos.get(i)).instanteFisico())
                        .as("processo %d, evento %d", p, i)
                        .isGreaterThan(execucao.eventos().get(eventos.get(i - 1)).instanteFisico());
        }
    }

    /**
     * TODA ARESTA DE CAUSALIDADE anda para a frente nos indices, e o fecho transitivo
     * depende disso.
     *
     * <p>Se alguma andasse para tras, o fecho sairia incompleto EM SILENCIO: a conta e em
     * ordem crescente de indice e um pai de indice maior simplesmente nao seria visto.
     */
    @Test
    @DisplayName("toda aresta de causalidade vai de um indice menor para um maior")
    void asArestasAndamParaAFrente() {
        for (long semente = 1; semente <= 10; semente++) {
            Execucao execucao = Execucao.sortear(6, 30, 0.5, 50, 20, semente);
            int[] anterior = new int[execucao.processos()];

            java.util.Arrays.fill(anterior, -1);

            for (Evento evento : execucao.eventos()) {
                assertThat(anterior[evento.processo()]).as("anterior de %d", evento.indice())
                        .isLessThan(evento.indice());

                if (evento.tipo() == Tipo.RECEBIMENTO)
                    assertThat(evento.par()).as("envio de %d", evento.indice())
                            .isLessThan(evento.indice());

                anterior[evento.processo()] = evento.indice();
            }
        }
    }

    /**
     * O FECHO E TRANSITIVO: se a esta antes de b e b esta antes de c, entao a esta antes
     * de c.
     *
     * <p>E a terceira regra, e ela e a unica que nao e local: as outras duas se veem num
     * evento so, e esta precisa de tres.
     */
    @Test
    @DisplayName("o fecho e transitivo em todos os trios de eventos")
    void oFechoEhTransitivo() {
        Execucao execucao = Execucao.sortear(4, 15, 0.5, 50, 20, 7);
        long[][] fecho = Juiz.fecho(execucao);
        int n = execucao.quantosEventos();

        for (int a = 0; a < n; a++)
            for (int b = 0; b < n; b++) {
                if (!Juiz.aconteceuAntes(fecho, a, b)) continue;

                for (int c = 0; c < n; c++)
                    if (Juiz.aconteceuAntes(fecho, b, c))
                        assertThat(Juiz.aconteceuAntes(fecho, a, c))
                                .as("%d antes de %d antes de %d", a, b, c).isTrue();
            }
    }

    @Test
    @DisplayName("nenhum evento aconteceu antes de si mesmo")
    void nenhumEventoPrecedeASiMesmo() {
        Execucao execucao = Execucao.sortear(4, 20, 0.5, 50, 20, 11);
        long[][] fecho = Juiz.fecho(execucao);

        for (int i = 0; i < execucao.quantosEventos(); i++)
            assertThat(Juiz.aconteceuAntes(fecho, i, i)).as("evento %d", i).isFalse();
    }

    @Test
    @DisplayName("o fecho recusa uma execucao grande demais em vez de estourar a memoria")
    void oTetoDoFecho() {
        assertThatThrownBy(() -> Juiz.fecho(Execucao.sortear(10, 1_000, 0.3, 0, 10, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventos");
    }

    @Test
    @DisplayName("a execucao recusa zero processos ou zero eventos")
    void osParametrosInvalidos() {
        assertThatThrownBy(() -> Execucao.sortear(0, 10, 0.3, 0, 10, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Execucao.sortear(2, 0, 0.3, 0, 10, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a mesma semente da a mesma execucao e todo envio tem par")
    void aExecucaoERepetivel() {
        Execucao uma = Execucao.sortear(4, 20, 0.5, 50, 20, 5);
        Execucao outra = Execucao.sortear(4, 20, 0.5, 50, 20, 5);

        assertThat(uma.eventos()).isEqualTo(outra.eventos());
        assertThat(Execucao.sortear(4, 20, 0.5, 50, 20, 6).eventos()).isNotEqualTo(uma.eventos());

        // cada envio e recebido exatamente uma vez
        Set<Integer> recebidos = new HashSet<>();
        int envios = 0;

        for (Evento evento : uma.eventos()) {
            if (evento.tipo() == Tipo.ENVIO) envios++;
            if (evento.tipo() == Tipo.RECEBIMENTO) assertThat(recebidos.add(evento.par())).isTrue();
        }

        assertThat(recebidos).hasSize(envios);
        assertThat(uma.mensagensEntregues()).isEqualTo(envios);
    }

    /**
     * O DESLOCAMENTO DE 32 BITS: sem ele, o sorteio de qual processo age alternaria em
     * xadrez, os processos se revezariam em ordem fixa e a execucao sorteada seria uma
     * execucao em TURNOS disfarcada. Em turnos ha muito menos concorrencia.
     */
    @Test
    @DisplayName("o sorteio de qual processo age nao se reveza em xadrez")
    void oSorteioNaoSeReveza() {
        Execucao.Sorteio sorteio = new Execucao.Sorteio(123);
        int repetiu = 0;
        int anterior = sorteio.ate(2);

        for (int i = 0; i < 2_000; i++) {
            int agora = sorteio.ate(2);

            if (agora == anterior) repetiu++;

            anterior = agora;
        }

        // num sorteio de verdade, metade das vezes o mesmo processo sai de novo
        assertThat((double) repetiu / 2_000).isBetween(0.45, 0.55);
    }

    @Test
    @DisplayName("a semente zero nao trava o sorteio")
    void aSementeZeroNaoTrava() {
        Execucao.Sorteio sorteio = new Execucao.Sorteio(0);
        Set<Integer> vistos = new HashSet<>();

        for (int i = 0; i < 100; i++) vistos.add(sorteio.proximo());

        assertThat(vistos).hasSizeGreaterThan(90);

        assertThatThrownBy(() -> new Execucao.Sorteio(1).ate(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
