package br.com.conde.relogio;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Os testes das TABELAS que o medidor imprime.
 *
 * <p>Toda frase do README que e um numero esta aqui como afirmacao. Texto nao quebra
 * quando deixa de ser verdade; teste quebra.
 */
class MedidasTest {

    private static final List<Medidas.LinhaOrdem> ORDEM = Medidas.aOrdem();
    private static final List<Medidas.LinhaInversao> INVERSAO = Medidas.aInversao();
    private static final List<Medidas.LinhaConcorrencia> CONCORRENCIA = Medidas.aConcorrencia();
    private static final List<Medidas.LinhaTamanho> TAMANHO = Medidas.oTamanho();
    private static final List<Medidas.LinhaEscala> ESCALA = Medidas.aEscala();

    // ───────────────────── a ordem ─────────────────────

    /**
     * O ACHADO PRINCIPAL: dos quatro relogios, UM acerta todos os pares, e e o que custa
     * um numero por processo.
     */
    @Test
    @DisplayName("so o vetorial acerta cem por cento, e os outros tres ficam em dois tercos")
    void aTabelaDaOrdem() {
        assertThat(ORDEM).hasSize(4);

        Medidas.LinhaOrdem vetorial = acharOrdem("vetorial");

        assertThat(vetorial.fracaoCerta()).isEqualTo(1.0);
        assertThat(vetorial.concorrentesOrdenados()).isZero();
        assertThat(vetorial.ordenadosPerdidos()).isZero();
        assertThat(vetorial.inversoes()).isZero();
        assertThat(vetorial.numerosPorEvento()).isEqualTo(Medidas.PROCESSOS);

        for (String nome : new String[] {"Lamport", "fisico", "hibrido"}) {
            Medidas.LinhaOrdem l = acharOrdem(nome);

            assertThat(l.fracaoCerta()).as(nome).isBetween(0.55, 0.75);
            assertThat(l.concorrentesOrdenados()).as(nome).isGreaterThan(9_000);
            assertThat(l.numerosPorEvento()).as(nome).isLessThanOrEqualTo(2);
        }
    }

    /**
     * E OS TRES ERROS SAO DE GRAVIDADES DIFERENTES.
     *
     * <p>Lamport e o hibrido so cometem o erro benigno: ordenar um par concorrente. O
     * relogio de parede comete os tres, e o terceiro e dizer que a resposta chegou antes
     * da pergunta.
     */
    @Test
    @DisplayName("so o relogio de parede inverte causa e efeito")
    void soOParedeInverte() {
        for (String nome : new String[] {"Lamport", "vetorial", "hibrido"})
            assertThat(acharOrdem(nome).inversoes()).as(nome).isZero();

        assertThat(acharOrdem("fisico").inversoes()).isPositive();

        // e Lamport nunca deixa de ver uma ordem que existe
        assertThat(acharOrdem("Lamport").ordenadosPerdidos()).isZero();
    }

    // ───────────────────── a inversao ─────────────────────

    /**
     * O LIMIAR: enquanto o desvio dos relogios for MENOR OU IGUAL ao atraso da rede, o
     * relogio de parede nao inverte nada. Acima disso, inverte.
     */
    @Test
    @DisplayName("o relogio de parede so comeca a inverter quando o desvio passa do atraso da rede")
    void aTabelaDaInversao() {
        assertThat(INVERSAO).hasSize(6 * 2);

        for (Medidas.LinhaInversao l : INVERSAO) {
            if (!l.relogio().equals("fisico")) continue;

            if (l.desvio() <= l.atrasoDaRede()) {
                assertThat(l.inversoes()).as("com desvio %d", l.desvio()).isZero();
                assertThat(l.piorInversaoEmPares()).as("com desvio %d", l.desvio()).isZero();
            } else {
                assertThat(l.inversoes()).as("com desvio %d", l.desvio()).isPositive();
                assertThat(l.piorInversaoEmPares()).as("com desvio %d", l.desvio()).isPositive();
            }
        }
    }

    @Test
    @DisplayName("quanto maior o desvio, mais inversoes, e o hibrido nao inverte em nenhum")
    void aInversaoCresceComODesvio() {
        double anterior = -1;

        for (Medidas.LinhaInversao l : INVERSAO)
            if (l.relogio().equals("fisico") && l.desvio() >= 50) {
                assertThat(l.fracaoInvertida()).as("com desvio %d", l.desvio())
                        .isGreaterThan(anterior);

                anterior = l.fracaoInvertida();
            }

        // com um segundo de desvio, quase oito por cento dos pares saem invertidos
        assertThat(acharInversao("fisico", 1_000).fracaoInvertida()).isGreaterThan(0.05);

        for (Medidas.LinhaInversao l : INVERSAO)
            if (l.relogio().equals("hibrido")) {
                assertThat(l.inversoes()).as("hibrido com desvio %d", l.desvio()).isZero();
                assertThat(l.piorInversaoEmPares()).as("hibrido com desvio %d", l.desvio()).isZero();
            }
    }

    // ───────────────────── a concorrencia ─────────────────────

    /**
     * O DENOMINADOR: com um processo so nao ha concorrencia nenhuma e Lamport acerta cem
     * por cento. A partir dai o acerto dele despenca junto com a concorrencia.
     */
    @Test
    @DisplayName("com um processo Lamport acerta tudo, e com oito ele acerta um quinto")
    void aTabelaDaConcorrencia() {
        assertThat(CONCORRENCIA).hasSize(4 * 3);
        assertThat(Medidas.SEMENTES).isGreaterThan(1);

        for (Medidas.LinhaConcorrencia l : CONCORRENCIA)
            if (l.processos() == 1) {
                assertThat(l.fracaoConcorrente()).as("chance %.2f", l.chanceDeEnviar()).isZero();
                assertThat(l.acertoDeLamport()).as("chance %.2f", l.chanceDeEnviar()).isEqualTo(1.0);
                assertThat(l.mensagensEntregues()).isZero();
            }

        Medidas.LinhaConcorrencia oito = acharConcorrencia(8, 0.05);

        assertThat(oito.fracaoConcorrente()).isGreaterThan(0.75);
        assertThat(oito.acertoDeLamport()).isLessThan(0.3);
    }

    /**
     * O ACHADO QUE ME CORRIGIU: com UMA execucao por linha, a tabela saia nao monotona na
     * chance de enviar, e eu quase escrevi um mecanismo para explicar isso.
     *
     * <p>As colunas do menor e do maior dizem o tamanho do espalhamento. Com quatro e oito
     * processos a queda e clara e maior do que ele; com dois processos as faixas se
     * sobrepoem e a diferenca nao e achado nenhum.
     */
    @Test
    @DisplayName("com quatro e oito processos mais mensagens dao menos concorrencia, e com dois as faixas se sobrepoem")
    void aQuedaSoEClaraComProcessosSuficientes() {
        for (int processos : new int[] {4, 8}) {
            Medidas.LinhaConcorrencia poucas = acharConcorrencia(processos, 0.05);
            Medidas.LinhaConcorrencia muitas = acharConcorrencia(processos, 0.9);

            assertThat(muitas.fracaoConcorrente()).as("com %d processos", processos)
                    .isLessThan(poucas.fracaoConcorrente());

            // e a queda e maior do que o espalhamento das duas linhas juntas
            assertThat(poucas.menorFracao()).as("com %d processos", processos)
                    .isGreaterThan(muitas.maiorFracao());
        }

        // com dois processos, as faixas de 0,35 e 0,90 se tocam
        Medidas.LinhaConcorrencia media = acharConcorrencia(2, 0.35);
        Medidas.LinhaConcorrencia alta = acharConcorrencia(2, 0.9);

        assertThat(alta.menorFracao()).isLessThan(media.maiorFracao());
    }

    @Test
    @DisplayName("as mensagens entregues crescem com a chance de enviar, em todo numero de processos")
    void asEntreguesCrescemComAChance() {
        for (int processos : new int[] {2, 4, 8}) {
            assertThat(acharConcorrencia(processos, 0.35).mensagensEntregues())
                    .as("com %d processos", processos)
                    .isGreaterThan(acharConcorrencia(processos, 0.05).mensagensEntregues());

            assertThat(acharConcorrencia(processos, 0.9).mensagensEntregues())
                    .as("com %d processos", processos)
                    .isGreaterThan(acharConcorrencia(processos, 0.35).mensagensEntregues());
        }
    }

    // ───────────────────── o tamanho ─────────────────────

    /**
     * O TAMANHO: o vetorial custa oito bytes por PROCESSO em cada mensagem, e os outros
     * custam oito ou dezesseis.
     */
    @Test
    @DisplayName("com 64 processos o carimbo vetorial pesa 512 bytes por mensagem e os outros pesam 8 ou 16")
    void aTabelaDoTamanho() {
        assertThat(TAMANHO).hasSize(3 * 4);

        for (Medidas.LinhaTamanho l : TAMANHO) {
            if (l.relogio().equals("vetorial")) {
                assertThat(l.numerosPorEvento()).as("com %d processos", l.processos())
                        .isEqualTo(l.processos());
                assertThat(l.bytesPorMensagem()).as("com %d processos", l.processos())
                        .isEqualTo(8L * l.processos());
                assertThat(l.fracaoCerta()).as("com %d processos", l.processos()).isEqualTo(1.0);
            } else {
                assertThat(l.bytesPorMensagem()).as("%s com %d processos", l.relogio(), l.processos())
                        .isLessThanOrEqualTo(16);
            }
        }

        assertThat(acharTamanho("vetorial", 64).bytesPorMensagem()).isEqualTo(512);
        assertThat(acharTamanho("Lamport", 64).bytesPorMensagem()).isEqualTo(8);
    }

    // ───────────────────── a escala ─────────────────────

    /**
     * AS DUAS COLUNAS NAO SE CRUZAM NUNCA.
     *
     * <p>O vetorial acerta cem por cento em qualquer numero de processos e cresce em linha
     * reta. O de Lamport fica do mesmo tamanho e piora: de 97,5% com dois processos para
     * 15,5% com trinta e dois.
     *
     * <p>Nao existe um numero de processos a partir do qual valha a pena trocar um pelo
     * outro. E uma escolha entre duas coisas diferentes, e nao um ponto de equilibrio.
     */
    @Test
    @DisplayName("o vetorial acerta cem por cento em toda escala e o Lamport cai de 97% para 15%")
    void aTabelaDaEscala() {
        assertThat(ESCALA).hasSize(5);
        assertThat(ESCALA).allSatisfy(l -> {
            assertThat(l.acertoDoVetorial()).as("com %d processos", l.processos()).isEqualTo(1.0);
            assertThat(l.bytesDoVetorial()).as("com %d processos", l.processos())
                    .isEqualTo(8L * l.processos());
            assertThat(l.bytesDoLamport()).isEqualTo(8);
        });

        Medidas.LinhaEscala dois = ESCALA.get(0);
        Medidas.LinhaEscala trintaEDois = ESCALA.get(ESCALA.size() - 1);

        assertThat(dois.processos()).isEqualTo(2);
        assertThat(trintaEDois.processos()).isEqualTo(32);

        assertThat(dois.acertoDoLamport()).isCloseTo(0.975, within(0.02));
        assertThat(trintaEDois.acertoDoLamport()).isLessThan(0.2);

        // e o acerto de Lamport cai monotonamente com os processos
        for (int i = 1; i < ESCALA.size(); i++)
            assertThat(ESCALA.get(i).acertoDoLamport())
                    .as("de %d para %d processos", ESCALA.get(i - 1).processos(), ESCALA.get(i).processos())
                    .isLessThan(ESCALA.get(i - 1).acertoDoLamport());
    }

    /**
     * E A SOMA FECHA: o que Lamport erra e exatamente a concorrencia que ele nao ve, tirando
     * os empates que ele acerta.
     */
    @Test
    @DisplayName("o erro de Lamport e a concorrencia que ele nao viu, e a soma fecha")
    void oErroDeLamportEAConcorrencia() {
        for (Medidas.LinhaEscala l : ESCALA)
            assertThat(l.acertoDoLamport() + l.fracaoConcorrente()).as("com %d processos", l.processos())
                    .isBetween(1.0, 1.08);
    }

    // ───────────────────── achadores ─────────────────────

    private static Medidas.LinhaOrdem acharOrdem(String relogio) {
        return ORDEM.stream().filter(l -> l.relogio().equals(relogio)).findFirst().orElseThrow();
    }

    private static Medidas.LinhaInversao acharInversao(String relogio, long desvio) {
        return INVERSAO.stream()
                .filter(l -> l.relogio().equals(relogio) && l.desvio() == desvio)
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaConcorrencia acharConcorrencia(int processos, double chance) {
        return CONCORRENCIA.stream()
                .filter(l -> l.processos() == processos && Math.abs(l.chanceDeEnviar() - chance) < 1e-9)
                .findFirst()
                .orElseThrow();
    }

    private static Medidas.LinhaTamanho acharTamanho(String relogio, int processos) {
        return TAMANHO.stream()
                .filter(l -> l.relogio().equals(relogio) && l.processos() == processos)
                .findFirst()
                .orElseThrow();
    }
}
