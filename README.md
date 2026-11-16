# Planta de Base (Base Blueprint)

Mod para **Minecraft 1.21.1 (NeoForge)**. Um item de early game que constrói a base de uma vez:
um piso redondo ou quadrado (com paredes opcionais), do tamanho que você escolher, usando blocos
do seu inventário. O círculo segue a mesma regra dos geradores de círculo em pixel
(como o donatstudios PixelCircleGenerator).

## Receita
Sem forma: **Mesa de trabalho + Graveto**.

## Como usar
| Ação | O que faz |
|---|---|
| Clique direito no ar ou tecla **N** | Abre o menu: forma, largura, comprimento, piso (cheio / contorno / sem piso), altura das paredes. Tem prévia em pixels. Shift nos botões +/- muda de 5 em 5 |
| Shift + clique direito num bloco | Escolhe esse bloco como material |
| Bloco na mão secundária | Tem prioridade como material |
| Segurar a planta e mirar num bloco | Mostra o contorno da base no mundo (verde = blocos suficientes, vermelho = faltam) e a contagem abaixo da mira |
| Clique direito num bloco | Constrói a base centrada ali |
| Shift + clique direito no ar | Desfaz a última base e devolve os blocos |

- Você precisa ter **todos** os blocos no inventário, senão nada é construído e a mensagem diz quantos faltam. No criativo é de graça.
- Só ocupa espaços vazios ou substituíveis (ar, grama alta, neve). Se algum ponto estiver ocupado (tronco, folhas, terreno mais alto), a base **não é construída**: os pontos aparecem em vermelho na prévia para você limpar ou mudar de lugar. O servidor pode liberar construção parcial com `allowPartial`.
- Respeita proteção de spawn e mods de claim (cada bloco dispara o evento de colocação).
- Quem estiver em cima da área é levantado para cima do piso.
- O desfazer fica só na memória: some ao sair do mundo ou reiniciar o servidor.

## Configuração (`world/serverconfig/baseblueprint-server.toml`)
- `maxSize` (padrão 64): maior largura/comprimento.
- `maxWallHeight` (padrão 16): parede mais alta.
- `allowUndo` (padrão true).
- `allowPartial` (padrão false): constrói mesmo com pontos ocupados, deixando buracos neles.

## Build
```
./gradlew build                # jar em build/libs/baseblueprint-1.0.0.jar
./gradlew runGameTestServer    # testes in-game
./gradlew runClient            # cliente de desenvolvimento
```
A textura é editável no Pontilha: `art-src/base_blueprint.pontilha`.
