# relogio

Relógios lógicos do zero em Java 21: Lamport, vetorial, híbrido e o relógio de parede,
contra a causalidade de verdade.

O juiz é o **fecho transitivo do aconteceu-antes**. Essa relação não precisa de relógio
nenhum para existir: ela sai das três regras de Lamport, de 1978. Dois eventos do mesmo
processo estão em ordem, um envio está antes do recebimento dele, e a relação é
transitiva.

O juiz monta essa relação inteira, por força bruta, e depois pergunta a cada relógio o
que ele acha de cada par. Não há amostragem: para n eventos, ele responde todos os
n(n−1)/2 pares.

A resposta certa tem **três** valores e não dois. Um evento pode ter acontecido antes do
outro, depois, ou nenhum dos dois. Concorrente não quer dizer "simultâneo" nem "não sei
qual veio primeiro": quer dizer que a pergunta **não tem resposta**, porque nenhuma
informação foi de um para o outro.

## Um de quatro acerta

```
$ mvn exec:java -Dexec.args="ordem"

  relogio           pares concorrentes de verdade   fracao certa   concorrentes ordenados        ordem nao vista    inversoes   fracao invertida numeros por evento
  Lamport           28680                  10394         0.6516                     9992                      0            0           0.000000                  1
  vetorial          28680                  10394         1.0000                        0                      0            0           0.000000                  6
  fisico            28680                  10394         0.6377                    10376                      0           14           0.000488                  1
  hibrido           28680                  10394         0.6382                    10376                      0            0           0.000000                  2
```

Os três erros são de gravidades muito diferentes, e por isso são três colunas e não uma.

**Ordenar um par concorrente** é o erro de Lamport, e ele é benigno em muitos usos: quem
só quer um desempate qualquer não se importa. **Não ver uma ordem que existe** é perder
informação. **Inverter causa e efeito** é dizer que a resposta chegou antes da pergunta, e
disso não há uso que salve.

Só o relógio de parede comete os três.

## O que mais bytes compram

```
$ mvn exec:java -Dexec.args="tamanho"

  relogio         processos   numeros por evento     bytes por mensagem   fracao certa    inversoes
  Lamport                 4                    1                      8         0.8462            0
  vetorial                4                    4                     32         1.0000            0
  Lamport                16                    1                      8         0.2619            0
  vetorial               16                   16                    128         1.0000            0
  Lamport                64                    1                      8         0.1623            0
  vetorial               64                   64                    512         1.0000            0
```

O carimbo não fica só no evento: ele viaja em **toda mensagem**. Com 64 processos, cada
mensagem carrega 512 bytes só de relógio, e é por isso que quase ninguém usa vetorial.

```
$ mvn exec:java -Dexec.args="escala"

     processos    bytes do vetorial     bytes do Lamport     acerto do vetorial      acerto do Lamport     fracao concorrente
             2                   16                    8                 1.0000                 0.9754                 0.0272
             8                   64                    8                 1.0000                 0.5959                 0.4186
            32                  256                    8                 1.0000                 0.1549                 0.8953
```

As duas colunas **não se cruzam nunca**. Não existe um número de processos a partir do
qual valha a pena trocar um pelo outro: é uma escolha entre duas coisas diferentes, e não
um ponto de equilíbrio.

E a última coluna explica a penúltima. O erro de Lamport é exatamente a concorrência que
ele não vê: acerto mais fração concorrente dá um, nas cinco linhas.

## O relógio de parede diz que a resposta chegou antes da pergunta

```
$ mvn exec:java -Dexec.args="inversao"

    desvio em ms   atraso da rede relogio           inversoes        pares   fracao invertida inversoes de envio e recebimento
               0               20 fisico                    0        28920           0.000000                          0
               5               20 fisico                    0        28680           0.000000                          0
              20               20 fisico                    0        28680           0.000000                          0
              50               20 fisico                   14        28680           0.000488                          8
             200               20 fisico                  245        28680           0.008543                         14
            1000               20 fisico                 2260        28680           0.078801                         19
            1000               20 hibrido                   0        28680           0.000000                          0
```

O limiar é exatamente onde se espera: enquanto o desvio entre as máquinas for **menor ou
igual** ao atraso da rede, nenhuma inversão. Acima disso, elas começam.

E não é um caso teórico. Desvio de dezenas de milissegundos entre máquinas é o normal, e
mensagem dentro do mesmo centro de dados chega em menos de um. Com um segundo de desvio,
**8% dos pares saem invertidos** e 19 pares envio-recebimento saem ao contrário.

O híbrido resolve isso com um segundo número: nunca inverte, e o carimbo dele continua
parecido com um horário, ao contrário do de Lamport.

## Como rodar

```
mvn test
mvn exec:java -Dexec.args="tudo"
```

Os comandos do medidor são `ordem`, `inversao`, `concorrencia`, `tamanho`, `escala` e
`tudo`.

## O que as medidas me corrigiram

**A tabela da inversão mostrava o relógio de parede invertendo causa e efeito com desvio
ZERO.**

Com desvio zero, o relógio de parede **é** o tempo verdadeiro. Ele não pode inverter nada,
e mesmo assim a primeira versão da tabela mostrava 660 inversões e 22 pares
envio-recebimento ao contrário.

O gerador de execuções não tinha tempo verdadeiro nenhum. Cada processo tinha um contador
próprio e eu decidia quando entregar uma mensagem comparando o contador do remetente com o
do destinatário, como se fossem o mesmo relógio. Com isso, uma mensagem podia ser entregue
**antes de ter sido enviada**, e as tabelas estavam medindo um universo impossível.

Passou a haver um relógio só, o do universo, que anda para a frente. O que cada máquina
marca é esse tempo mais o desvio dela, e o desvio é o erro do relógio, não do universo.

O número que denunciou não foi um número grande: foi um número que deveria ser zero.

**Eu quase escrevi um mecanismo para explicar o espalhamento de uma amostra só.**

A tabela da concorrência mede quanta concorrência existe de verdade, conforme o número de
processos e a chance de enviar mensagens. A expectativa é clara: mensagem cria
causalidade, então mais mensagens deveriam dar menos concorrência.

Com **uma** execução por linha, a tabela saiu não monótona: com dois processos, subir a
chance de enviar de 0,35 para 0,90 fazia a concorrência **subir** de 0,035 para 0,110. Eu
escrevi uma explicação, apaguei, escrevi outra, e as duas estavam erradas.

```
$ mvn exec:java -Dexec.args="concorrencia"

     processos   chance de enviar   sementes    mensagens entregues     fracao concorrente      a menor      a maior      acerto de Lamport
             2               0.35         12                   46.5                 0.0513       0.0371       0.0705                 0.9534
             2               0.90         12                   87.0                 0.0940       0.0431       0.1998                 0.9111
             4               0.05         12                    9.1                 0.5717       0.4260       0.6399                 0.4429
             4               0.90         12                   88.3                 0.1879       0.1432       0.2796                 0.8238
             8               0.05         12                    7.9                 0.8163       0.7402       0.8520                 0.2160
             8               0.90         12                   87.9                 0.4105       0.3390       0.5087                 0.6133
```

Com doze execuções por linha e as colunas do menor e do maior, dá para ver o que estava
acontecendo. Com quatro e oito processos a queda é real e maior do que o espalhamento. Com
dois processos, a faixa de 0,90 vai de 0,043 a 0,200 e a de 0,35 vai de 0,037 a 0,071: as
duas **se tocam**, e a diferença entre as médias não é achado nenhum.

A correção não foi no gerador nem no juiz. Foi no número de execuções, e em pôr na tabela
as duas colunas que dizem quanto ela varia.

**O escoamento das mensagens que sobraram inventava concorrência.**

No fim da execução sobram mensagens enviadas e não entregues, e elas precisam de um par.
A primeira versão despejava: percorria processo por processo e punha todos os recebimentos
que faltavam, em sequência, no fim da lista.

Recebimentos de processos diferentes, empilhados um atrás do outro sem tempo passando
entre eles, são todos concorrentes entre si. O escoamento passou a ser o mesmo laço da
execução com os envios desligados.

Medido depois, o efeito era pequeno nas tabelas que eu tinha: dois eventos a mais numa
execução de 240. O conserto não mudou nenhum número. Fica porque a tabela de concorrência
é justamente a que mede isso, e um gerador que inventa concorrência não serve para medir
concorrência, mesmo que hoje invente pouca.

## Licença

MIT.
