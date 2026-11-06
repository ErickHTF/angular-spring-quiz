# Comparativo de desempenho: Fresh + Deno × Angular + Spring

Slides: [`comparativo.pdf`](comparativo.pdf) ·
[`comparativo.pptx`](comparativo.pptx). Coleta de 12/10/2026, 24 execuções,
3 pares por carga. Dados brutos e protocolo no repositório
[freshQuiz](https://github.com/ErickHTF/freshQuiz).

## O que foi comparado

Arquiteturas completas, não só frameworks de interface:

- **Fresh + Deno (SSR):** um processo Deno renderiza HTML, atende a API e o SSE.
- **Angular + Spring (SPA):** Nginx serve o build estático; Spring (JVM) atende
  a API e o SSE.

A carga são clientes HTTP/SSE mais 2 navegadores (host e 1 jogador). 100
jogadores não equivalem a 100 navegadores.

## Resultados (médias por carga)

| Jogadores | Implementação    | p95 HTTP ms | CPU servidor % | RSS servidor MiB | Falhas % |
| --------- | ---------------- | ----------: | -------------: | ---------------: | -------: |
| 30        | Fresh + Deno     |          80 |            3,2 |              107 |        0 |
| 30        | Angular + Spring |         303 |            3,0 |              174 |        0 |
| 60        | Fresh + Deno     |         172 |           14,8 |              118 |        0 |
| 60        | Angular + Spring |         480 |            6,4 |              203 |        0 |
| 100       | Fresh + Deno     |       7.641 |           61,4 |              279 |     2,75 |
| 100       | Angular + Spring |       1.080 |           16,7 |              223 |     0,06 |

No cliente, a SPA baixa ~331 KiB contra ~68 KiB, mas o carregamento e a CPU do
Chromium ficaram próximos nas duas.

## Leitura

- Até 60 jogadores, Fresh + Deno responde mais rápido e usa menos memória.
- Em 100, o Deno satura um núcleo (p95 de CPU 102–103%) e enfileira. O app roda
  em um único `deno serve`, sem `--parallel`; a JVM usa várias threads.
- O SSR não explica a diferença: só duas páginas são renderizadas por execução.
  O que pesa é API e SSE, igual nas duas.
- Resultado exploratório: 3 pares por carga, tudo na mesma máquina.

## Próximos testes

- Repetir 100 jogadores com `deno serve --parallel`.
- Limitar CPU e rede dos navegadores para medir o custo da SPA no cliente.
