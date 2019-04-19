// <!--

blackImage = new Image();
blackImage.src = imagePath + "black.gif";
whiteImage = new Image();
whiteImage.src = imagePath + "white.gif";

statisticImage = new Image();
statisticImage.src = imagePath + "light_green.gif";
statisticHighlightImage = new Image();
statisticHighlightImage.src = imagePath + "stats_highlight.gif";
statisticBlankImage = new Image();
statisticBlankImage.src = imagePath + "stats_blank.gif";

dummyImage = new Image();
dummyImage.src = imagePath + "stats_blank.gif";

blackCaptureImage = new Image();
blackCaptureImage.src = imagePath + "black_nobg.gif";
whiteCaptureImage = new Image();
whiteCaptureImage.src = imagePath + "white_nobg.gif";

blankImage = new Image();
blankImage.src = imagePath + "blank.gif";
dotImage = new Image();
dotImage.src = imagePath + "dot.gif";

moveImages = [whiteImage, blackImage];
moveCaptureImages = [whiteCaptureImage, blackCaptureImage];

var game = 0;

var currentMove = 0;
var maxMove = currentMove;
var startMoves = 0;
var viewingSearchResults = 1;

var statisticsShowing = 1;

coordinateAlphas = ["A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"];

var ns4 = (document.layers) ? true : false;
var ie4 = (document.all) ? true : false;
var ns6 = ((navigator.vendor) && (navigator.vendor.indexOf("Netscape6"))) !== -1;

var highlightedMove = "";

numCaptures = [0, 0, 0];
capturedAt = [new Array(60), new Array(60), new Array(60)];
capturedMoves = [new Array(60), new Array(60), new Array(60)];

board = [new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19)];
moves = new Array(361);

var currentNumMatchedGames = 0;

//PENTE 1
//SPEED PENTE 2
//KERYO 3
//SPEED KERYO 4
//GOMOKU 5
//SPEED GOMOKU 6
//D-PENTE 7
//SPEED D-PENTE 8
//G-PENTE 9
//SPEED G-PENTE 10
//POOF PENTE 11
//SPEED POOF PENTE 12
function gameHasCaptures() {
  return (game !== 5 && game !== 6 && game !== 13 && game !== 14);
}
function gameHasTripleCaptures() {
  return (game === 3 || game === 4 || game === 17 || game === 18 || game === 25 || game === 26);
}

function initializeGame() {

    game = parseInt(document.data_form.game.value);
    let filter_data = getFilterData();
    currentNumMatchedGames = 100;
    setGame(document.data_form.moves.value);
    startMoves = currentMove;
    showStatistics();
}

function clearGame() {
    viewingSearchResults = 1;
    setGame('K10,');
}

function loadGame(moves) {
    viewingSearchResults = 0;
    setGame(moves);
}

function resetGame() {
    viewingSearchResults = 1;
    setGame(document.data_form.moves.value);
    showStatistics();
}

function isConnect6(game) {
    if (game === 13 || game === 14) {
        return true;
    }
    return false;
}
function isPoofPente(game) {
    if (game === 11 || game === 12) {
        return true;
    }
    return false;
}
function isDPente(game) {
    if (game === 17 || game === 18 || game === 7 || game === 8 || game === 19 || game === 20) {
        return true;
    }
    return false;
}

function setGame(moveStr) {

    for (let i = 0; i < 3; i++) {
       numCaptures[i] = 0;
    }
    for (let i = 0; i < 3; i++) {
        for (let j = 0; j < 60; j++) {
            capturedAt[i][j] = 0;
        }
    }
    for (let i = 0; i < 3; i++) {
        for (let j = 0; j < 60; j++) {
            capturedMoves[i][j] = 0;
        }
    }

    for (let i = 1; i < 3; i++) {
        for (let j = 0; j < 19; j++) {

            let i1 = p[i] + "c" + j;

            document[i1].src = dummyImage.src;
        }
    }

    for (let i = 0; i < 19; i++) {
        for (let j = 0; j < 19; j++) {

            if (board[i][j] === 1 ||
                board[i][j] === 2) {
                clearMove(getStrMove(j * 19 + i));
            }
            board[i][j] = 0;
        }
    }
    for (let i = 0; i < 361; i++) {
        moves[i] = 0;
    }

    currentMove = 0;
    let startMove = -1, endMove = 0;

    let stillMovesLeft = moveStr.length > 0;

    while (stillMovesLeft) {

        if (currentMove !== 0) {
            startMove = moveStr.indexOf(",", startMove + 1);
        }

        endMove = moveStr.indexOf(",", startMove + 1);
        if (endMove === (moveStr.length - 1)) {
            stillMovesLeft = false;
        }

        let move = moveStr.substring(startMove + 1, endMove);

        addMove(move);
    }

    maxMove = currentMove;
}

function getCurrentMoveStr() {

    let moveStr = "";

    for (let i = 0; i < currentMove; i++) {
        moveStr += getStrMove(moves[i]) + ",";
    }

    return moveStr;
}

function highlightMove(move) {

    if (document[move].src === statisticImage.src) {

        let currentPlayer = currentMove % 2;
        if (isConnect6(game)) {
            currentPlayer = Math.floor((currentMove + 1)/2)%2;
        }
        document[move].src = moveImages[currentPlayer].src;

        highlightedMove = move;
    }
}

function unHighlightMove(move) {

    if (highlightedMove === move &&
        (document[move].src === moveImages[0].src ||
         document[move].src === moveImages[1].src)) {

        document[move].src = statisticImage.src;
        highlightedMove = "";
    }
}

function highlightStat(stat) {

    // get the image differently in ns and ie
    let image = (ns4) ? document['statsDiv'].document[stat] : document[stat];

    image.src = statisticHighlightImage.src;
}
function unHighlightStat(stat) {

    // get the image differently in ns and ie
    let image = (ns4) ? document['statsDiv'].document[stat] : document[stat];

    image.src = statisticBlankImage.src;
}

function addMoveInternal(intMove) {

    // put move in move list
    moves[currentMove] = intMove;

    // put move on internal board
    let x = intMove % 19;
    let y = Math.floor(intMove / 19);
    let currentPlayer = currentMove % 2 + 1;
        if (isConnect6(game)) {
            currentPlayer = Math.floor((currentMove+4)/2)%2 + 1;
        }
    board[x][y] = currentPlayer;
}

function addMove(move) {

    if (highlightedMove === move ||
        (document[move].src !== moveImages[0].src &&
         document[move].src !== moveImages[1].src))
    {

        hideStatistics();

        let intMove = getIntMove(move);
        addMoveInternal(intMove);

        // put move on image board
        let currentPlayer = currentMove++ % 2;
        if (isConnect6(game)) {
            currentPlayer = Math.floor((currentMove + 4)/2)%2;
        }
        document[move].src = moveImages[currentPlayer].src;

        maxMove = currentMove;

        highlightedMove = "";

        if (isPoofPente(game) || game === 25 || game === 26) {
            detectPoofCapture(intMove, false);
            if (game === 25 || game === 26) {
                detectKeryoPoofCapture(intMove, false);
            }
        }
        if (gameHasCaptures()) {
            removeCaptures(intMove, false);
        }
        
        if (shouldShowSearchStats()) {
            showStatistics();
        }
    }
}


function printBoard() {

    let boardStr = "";
    for (let i = 0; i < 19; i++) {
        for (let j = 0; j < 19; j++) {
            boardStr += board[j][i] + " ";
        }
        boardStr += "\n";
    }

    alert(boardStr);
}

dx = [-1, 0, 1, -1, 1, -1, 0, 1];
dy = [-1, -1, -1, 0, 0, 1, 1, 1];

p = ["", "w", "b"];

function removeCaptures(intMove, internal) {

   let currentPlayer = (currentMove - 1) % 2 + 1;
        // if (isConnect6(game)) {
        //     currentPlayer = Math.floor((currentMove - 1)/2)%2 + 1;
        // }
   let otherPlayer = 3 - currentPlayer;
   let x = intMove % 19;
   let y = Math.floor(intMove / 19);

   for (let d = 0; d < 8; d++) {
       let c1 = x + dx[d];
       let c2 = y + dy[d];
       let c3 = c1 + dx[d];
       let c4 = c2 + dy[d];
       let c5 = c3 + dx[d];
       let c6 = c4 + dy[d];
       let c7 = c5 + dx[d];
       let c8 = c6 + dy[d];

       if (c5 >= 0 && c5 < 19 && c6 >= 0 && c6 < 19) {

           if (board[c1][c2] === otherPlayer &&
               board[c3][c4] === otherPlayer &&
               board[c5][c6] === currentPlayer) {

               capturedAt[currentPlayer]
                         [numCaptures[currentPlayer]] = (currentMove - 1);
               capturedAt[currentPlayer]
                         [numCaptures[currentPlayer] + 1] = (currentMove - 1);
               capturedMoves[currentPlayer]
                            [numCaptures[currentPlayer]] = c1 + c2 * 19;
               capturedMoves[currentPlayer]
                            [numCaptures[currentPlayer] + 1] = c3 + c4 * 19;


               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   let i1 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i1].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;
               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   let i2 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i2].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;

               board[c1][c2] = 0;
               board[c3][c4] = 0;

               if (!internal) {
                   clearMove(getStrMove(c1 + c2 * 19));
                   clearMove(getStrMove(c3 + c4 * 19));
               }
           }
       }
       if (gameHasTripleCaptures() &&
           c7 >= 0 && c7 < 19 && c8 >= 0 && c8 < 19) {
           
           if (board[c1][c2] === otherPlayer &&
               board[c3][c4] === otherPlayer &&
               board[c5][c6] === otherPlayer &&
               board[c7][c8] === currentPlayer) {
           
               capturedAt[currentPlayer]
                         [numCaptures[currentPlayer]] = (currentMove - 1);
               capturedAt[currentPlayer]
                         [numCaptures[currentPlayer] + 1] = (currentMove - 1);
               capturedAt[currentPlayer]
                         [numCaptures[currentPlayer] + 2] = (currentMove - 1);
               capturedMoves[currentPlayer]
                            [numCaptures[currentPlayer]] = c1 + c2 * 19;
               capturedMoves[currentPlayer]
                            [numCaptures[currentPlayer] + 1] = c3 + c4 * 19;
               capturedMoves[currentPlayer]
                            [numCaptures[currentPlayer] + 2] = c5 + c6 * 19;

               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   let i1 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i1].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;
               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   let i2 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i2].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;
               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   let i3 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i3].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;

               board[c1][c2] = 0;
               board[c3][c4] = 0;
               board[c5][c6] = 0;

               if (!internal) {
                   clearMove(getStrMove(c1 + c2 * 19));
                   clearMove(getStrMove(c3 + c4 * 19));
                   clearMove(getStrMove(c5 + c6 * 19));
               }
           }
       }
    }
}

function putBackCaptures() {

    let currentPlayer = currentMove  % 2 + 1;
        // if (isConnect6(game)) {
        //     currentPlayer = Math.floor((currentMove - 1)/2)%2 + 1;
        // }
    let otherPlayer = 3 - currentPlayer;

    while (capturedAt[currentPlayer][numCaptures[currentPlayer] - 1] === currentMove) {

        let back = capturedMoves[currentPlayer][(numCaptures[currentPlayer] - 1)];

        let x = back % 19;
        let y = Math.floor(back / 19);
        board[x][y] = otherPlayer;

        document[getStrMove(back)].src = moveImages[otherPlayer - 1].src;

        numCaptures[currentPlayer]--;
        
        // don't un-display captures if they go over the limit
        if (numCaptures[currentPlayer] < 19) {
            let i1 = p[otherPlayer] + "c" + numCaptures[currentPlayer];
            document[i1].src = dummyImage.src;
        }
    }

    if (isPoofPente(game) || game === 25 || game === 26) {
      let first = true;
      while (capturedAt[otherPlayer][numCaptures[otherPlayer] - 1] === currentMove) {

          let back = capturedMoves[otherPlayer][(numCaptures[otherPlayer] - 1)];

          let x = back % 19;
          let y = Math.floor(back / 19);
          if (!first) {
            board[x][y] = currentPlayer;
            document[getStrMove(back)].src = moveImages[currentPlayer - 1].src;
          }
          first = false;


          numCaptures[otherPlayer]--;
          
          // don't un-display captures if they go over the limit
          if (numCaptures[otherPlayer] < 19) {
              let i1 = p[currentPlayer] + "c" + numCaptures[otherPlayer];
              document[i1].src = dummyImage.src;
          }
      }
    }
}
function detectKeryoPoofCapture(intMove, internal) {
    let myColor = (currentMove - 1) % 2 + 1;
    let opponentColor = 3 - myColor;
    let i = intMove % 19;
    let j = Math.floor(intMove / 19);
    let poofed = false;
    if (((i-3) > -1) && ((i+1) < 19)) { // left
        if (board[i-1][j] === myColor && board[i-2][j] === myColor) {
            if ((board[i-3][j] === opponentColor) && (board[i+1][j] === opponentColor)) {
                board[i-2][j] = 0;
                board[i-1][j] = 0;
                board[i][j] = 0;

                let x = i-1, y = j;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i-2; y = j;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((i-3) > -1) && ((j-3) > -1) && ((i+1) < 19) && ((j+1) < 19)) { // up left
        if (board[i-1][j-1] === myColor && board[i-2][j-2] === myColor) {
            if ((board[i-3][j-3] === opponentColor) && (board[i+1][j+1] === opponentColor)) {
                board[i-2][j-2] = 0;
                board[i-1][j-1] = 0;
                board[i][j] = 0;

                let x = i-1, y = j-1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i-2; y = j-2;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((j-3) > -1) && ((j+1) < 19)) { // up
        if (board[i][j-1] === myColor && board[i][j-2] === myColor) {
            if ((board[i][j-3] === opponentColor) && (board[i][j+1] === opponentColor)) {
                board[i][j-2] = 0;
                board[i][j-1] = 0;
                board[i][j] = 0;

                let x = i, y = j-1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i; y = j-2;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-3) > -1) && ((i+3) < 19) && ((j+1) < 19)) { // up right
        if (board[i+1][j-1] === myColor && board[i+2][j-2] === myColor) {
            if ((board[i-1][j+1] === opponentColor) && (board[i+3][j-3] === opponentColor)) {
                board[i+2][j-2] = 0;
                board[i+1][j-1] = 0;
                board[i][j] = 0;

                let x = i+1, y = j-1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i+2; y = j-2;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((i+3) < 19) && ((i-1) > -1)) { // right
        if (board[i+1][j] === myColor && board[i+2][j] === myColor) {
            if ((board[i+3][j] === opponentColor) && (board[i-1][j] === opponentColor)) {
                board[i+2][j] = 0;
                board[i+1][j] = 0;
                board[i][j] = 0;

                let x = i+1, y = j;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i+2; y = j;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-1) > -1) && ((i+3) < 19) && ((j+3) < 19)) { // down right
        if (board[i+1][j+1] === myColor && board[i+2][j+2] === myColor) {
            if ((board[i-1][j-1] === opponentColor) && (board[i+3][j+3] === opponentColor)) {
                board[i+2][j+2] = 0;
                board[i+1][j+1] = 0;
                board[i][j] = 0;

                let x = i+1, y = j+1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i+2; y = j+2;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((j+2) < 19) && ((j-1) > -1)) { // down
        if (board[i][j+1] === myColor && board[i][j+2] === myColor) {
            if ((board[i][j-1] === opponentColor) && (board[i][j+3] === opponentColor)) {
                board[i][j+1] = 0;
                board[i][j+2] = 0;
                board[i][j] = 0;
                
                let x = i, y = j+1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i; y = j+2;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((i-3) > -1) && ((j-1) > -1) && ((i+1) < 19) && ((j+3) < 19)) { // down left
        if (board[i-1][j+1] === myColor && board[i-2][j+2] === myColor) {
            if ((board[i+1][j-1] === opponentColor) && (board[i-3][j+3] === opponentColor)) {
                board[i-2][j+2] = 0;
                board[i-1][j+1] = 0;
                board[i][j] = 0;

                let x = i-1, y = j+1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i-2; y = j+2;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }

    // 4 directions with center of 3 stones placed to poof
    if (((i-2) > -1) && ((i+2) < 19)) { // horizontal
        if (board[i-1][j] === myColor && board[i+1][j] === myColor) {
            if ((board[i-2][j] === opponentColor) && (board[i+2][j] === opponentColor)) {
                board[i+1][j] = 0;
                board[i-1][j] = 0;
                board[i][j] = 0;

                let x = i-1, y = j;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i+1; y = j;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-2) > -1) && ((i+2) < 19) && ((j+2) < 19)) { // up left
        if (board[i-1][j-1] === myColor && board[i+1][j+1] === myColor) {
            if ((board[i-2][j-2] === opponentColor) && (board[i+2][j+2] === opponentColor)) {
                board[i+1][j+1] = 0;
                board[i-1][j-1] = 0;
                board[i][j] = 0;

                let x = i-1, y = j-1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i+1; y = j+1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((j-2) > -1) && ((j+2) < 19)) { // vertical
        if (board[i][j-1] === myColor && board[i][j+1] === myColor) {
            if ((board[i][j-2] === opponentColor) && (board[i][j+2] === opponentColor)) {
                board[i][j+1] = 0;
                board[i][j-1] = 0;
                board[i][j] = 0;

                let x = i, y = j+1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i; y = j-1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;

                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-2) > -1) && ((i+2) < 19) && ((j+2) < 19)) { // up right
        if (board[i+1][j-1] === myColor && board[i+1][j-1] === myColor) {
            if ((board[i-2][j+2] === opponentColor) && (board[i+2][j-2] === opponentColor)) {
                board[i+1][j-1] = 0;
                board[i-1][j+1] = 0;    
                board[i][j] = 0;

                let x = i-1, y = j+1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }
                x = i+1; y = j-1;
                capturedAt[opponentColor]
                    [numCaptures[opponentColor]] = (currentMove - 1);
                capturedMoves[opponentColor]
                    [numCaptures[opponentColor]] = x + y * 19;
                // don't display captures if they go over the limit
                if (numCaptures[opponentColor] < 19) {
                    let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                    document[i1].src = moveCaptureImages[myColor - 1].src;
                }
                numCaptures[opponentColor]++;
                if (!internal) {
                    clearMove(getStrMove(x + y * 19));
                }

                poofed = true;
            }
        }
    }

    if (poofed) {
        capturedAt[opponentColor]
            [numCaptures[opponentColor]] = (currentMove - 1);
        capturedMoves[opponentColor]
            [numCaptures[opponentColor]] = i + j * 19;
        // don't display captures if they go over the limit
        if (numCaptures[opponentColor] < 19) {
            let i2 = p[myColor] + "c" + numCaptures[opponentColor] + "";
            document[i2].src = moveCaptureImages[myColor - 1].src;
        }

        numCaptures[opponentColor]++;
        if (!internal) {
            clearMove(getStrMove(i + j * 19));
        }
    }
}
function detectPoofCapture(intMove, internal) {
   let myColor = (currentMove - 1) % 2 + 1;
   let opponentColor = 3 - myColor;
   let i = intMove % 19;
   let j = Math.floor(intMove / 19);

    let poofed = false;
    if (((i-2) > -1) && ((i+1) < 19)) {
        if (board[i-1][j] === myColor) {
            if ((board[i-2][j] === opponentColor) && (board[i+1][j] === opponentColor)) {
                board[i-1][j] = 0;
                board[i][j] = 0;

               let x = i-1, y = j;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-2) > -1) && ((i+1) < 19) && ((j+1) < 19)) {
        if (board[i-1][j-1] === myColor) {
            if ((board[i-2][j-2] === opponentColor) && (board[i+1][j+1] === opponentColor)) {
                board[i-1][j-1] = 0;
                board[i][j] = 0;

               let x = i-1, y = j-1;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    if (((j-2) > -1) && ((j+1) < 19)) {
        if (board[i][j-1] === myColor) {
            if ((board[i][j-2] === opponentColor) && (board[i][j+1] === opponentColor)) {
                board[i][j-1] = 0;
                board[i][j] = 0;

               let x = i, y = j-1;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-2) > -1) && ((i+2) < 19) && ((j+1) < 19)) {
        if (board[i+1][j-1] === myColor) {
            if ((board[i-1][j+1] === opponentColor) && (board[i+2][j-2] === opponentColor)) {
                board[i+1][j-1] = 0;
                board[i][j] = 0;

               let x = i+1, y = j-1;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    if (((i+2) < 19) && ((i-1) > -1)) {
        if (board[i+1][j] === myColor) {
            if ((board[i+2][j] === opponentColor) && (board[i-1][j] === opponentColor)) {
                board[i+1][j] = 0;
                board[i][j] = 0;

               let x = i+1, y = j;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-1) > -1) && ((i+2) < 19) && ((j+2) < 19)) {
        if (board[i+1][j+1] === myColor) {
            if ((board[i-1][j-1] === opponentColor) && (board[i+2][j+2] === opponentColor)) {
                board[i+1][j+1] = 0;
                board[i][j] = 0;

               let x = i+1, y = j+1;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    if (((j+2) < 19) && ((j-1) > -1)) {
        if (board[i][j+1] === myColor) {
            if ((board[i][j-1] === opponentColor) && (board[i][j+2] === opponentColor)) {
                board[i][j+1] = 0;
                board[i][j] = 0;

               let x = i, y = j+1;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-1) > -1) && ((i+1) < 19) && ((j+2) < 19)) {
        if (board[i-1][j+1] === myColor) {
            if ((board[i+1][j-1] === opponentColor) && (board[i-2][j+2] === opponentColor)) {
                board[i-1][j+1] = 0;
                board[i][j] = 0;

               let x = i-1, y = j+1;
               capturedAt[opponentColor]
                         [numCaptures[opponentColor]] = (currentMove - 1);
               capturedMoves[opponentColor]
                            [numCaptures[opponentColor]] = x + y * 19;
               // don't display captures if they go over the limit
               if (numCaptures[opponentColor] < 19) {
                   let i1 = p[myColor] + "c" + numCaptures[opponentColor] + "";
                   document[i1].src = moveCaptureImages[myColor - 1].src;
               }
               numCaptures[opponentColor]++;

               if (!internal) {
                   clearMove(getStrMove(x + y * 19));
               }

                poofed = true;
            }
        }
    }
    
    if (poofed) {
           capturedAt[opponentColor]
                     [numCaptures[opponentColor]] = (currentMove - 1);
           capturedMoves[opponentColor]
                        [numCaptures[opponentColor]] = i + j * 19;
           // don't display captures if they go over the limit
           if (numCaptures[opponentColor] < 19) {
               let i2 = p[myColor] + "c" + numCaptures[opponentColor] + "";
               document[i2].src = moveCaptureImages[myColor - 1].src;
           }

           numCaptures[opponentColor]++;
           if (!internal) {
               clearMove(getStrMove(i + j * 19));
           }
    }
}


function getIntMove(move) {

    let alpha = move.charAt(0);
    let x = 0;
    let y = 0;

    for (let i = 0; i < coordinateAlphas.length; i++) {
        if (alpha === coordinateAlphas[i]) {
            x = i;
            break;
        }
    }

    y = 19 - move.substring(1);

    return y * 19 + x;
}

function getStrMove(move) {

    let x = move % 19;
    let y = 19 - Math.floor(move / 19);

    return coordinateAlphas[x] + y;
}

function backMove() {

    if (currentMove !== 1 || (isDPente(game) && currentMove > 0)) {

        hideStatistics();

        let intMove = moves[--currentMove];
        let x = intMove % 19;
        let y = Math.floor(intMove / 19);
        board[x][y] = 0;

        clearMove(getStrMove(intMove));


        if (gameHasCaptures()) {
            putBackCaptures();
        }

        if (shouldShowSearchStats()) {
            showStatistics();
        }
    }
}

function forwardMove() {

    if (currentMove < maxMove) {

        hideStatistics();

        let intMove = moves[currentMove];

        let currentPlayer = currentMove++ % 2;
        if (isConnect6(game)) {
            currentPlayer = Math.floor((currentMove + 4)/2)%2;
        }
        document[getStrMove(intMove)].src = moveImages[currentPlayer].src;

        let x = intMove % 19;
        let y = Math.floor(intMove / 19);
        board[x][y] = currentPlayer + 1;

        if (isPoofPente(game) || game === 25 || game === 26) {
            detectPoofCapture(intMove, false);
            if (game === 25 || game === 26) {
                detectKeryoPoofCapture(intMove, false);
            }
        }

        if (gameHasCaptures()) {
            removeCaptures(intMove, false);
        }

        if (shouldShowSearchStats()) {
            showStatistics();
        }
    }
}

function firstMove() {

    for (let i = currentMove; i > 1; i--) {
        backMove();
    }
}

function lastMove() {

    for (let i = currentMove; i < maxMove; i++) {
        forwardMove();
    }
}

function hideStatistics() {

    if (statisticsShowing) {

        let startMove, endMove;

        for (let i = 0; i < numResults * 3; i++) {
 
            if (i === 0) {
                startMove = -1;
            }
            else {
                startMove = results.indexOf(",", startMove + 1);
            }

            endMove = results.indexOf(",", startMove + 1);
            if (endMove === -1) {
                endMove = results.length;
            }

            if (i % 3 === 0) {
                let move = results.substring(startMove + 1, endMove);
                clearMove(move);
            }
        }

        hide('statsDiv');
        hide('gamesDiv');

        statisticsShowing = 0;
    }
}

function shouldShowSearchStats() {
    let at = true;

    if (!statisticsShowing && 
        viewingSearchResults &&
        currentMove === startMoves) {

        let startMove = -1, endMove = 0;
        let stillMovesLeft = true;
        let i = 0;
        let moveStr = document.data_form.moves.value;

        while (stillMovesLeft) {

            if (i++ !== 0) {
                startMove = moveStr.indexOf(",", startMove + 1);
            }

            endMove = moveStr.indexOf(",", startMove + 1);
            if (endMove === (moveStr.length - 1)) {
                stillMovesLeft = false;
            }

            let move = moveStr.substring(startMove + 1, endMove);

            if (move !== getStrMove(moves[i - 1])) {
                at = false;
                break;
            }

            if (i > startMoves) {
                stillMovesLeft = false;
            }
        }
    }
    else {
        at = false;
    }

    return at;
}

function showStatistics() {

    let startMove, endMove;

    for (let i = 0; i < numResults * 3; i++) {
 
        if (i === 0) {
            startMove = -1;
        }
        else {
            startMove = results.indexOf(",", startMove + 1);
        }

        endMove = results.indexOf(",", startMove + 1);
        if (endMove === -1) {
            endMove = results.length;
        }

        if (i % 3 === 0) {
            let move = results.substring(startMove + 1, endMove);
            resetMove(move);
        }
    }

    show('statsDiv');
    show('gamesDiv');

    statisticsShowing = 1;
}

function resetMove(move) {

    document[move].src = statisticImage.src;
}

function clearMove(move) {

    if (move === 'G7' || move === 'G13' || move === 'N7' || move === 'N13') {
        document[move].src = dotImage.src;
    }
    else {
        document[move].src = blankImage.src;
    }
}


function show(id) {

    if (ns4) {
        document.layers[id].visibility = "show";
    }
    else if (ie4) {
        document.all[id].style.visibility = "visible";
    }
    else if (ns6) {
        document.getElementById(id).style.visibility = "visible";
    }
}

function hide(id) {

    if (ns4) {
        document.layers[id].visibility = "hide";
    }
    else if (ie4) {
        document.all[id].style.visibility = "hidden";
    }
    else if (ns6) {
        document.getElementById(id).style.visibility = "hidden";
    }
}


function loadTxt(gid) {
    document.loadGameForm.game_id.value=gid;
    document.loadGameForm.submit();
}


function getFilterData() {
    return (ns4) ? document['gamesDiv'].document.filter_data : document.filter_data;
}

// called when clicking the "search" button
function search() {

    let filter_data = getFilterData();

    filter_data.startGameNum.value = 0;
    filter_data.endGameNum = 100;

    submitForm();
}
function search2() {
    let filter_data = getFilterData();


    filter_data.startGameNum.value = 0;
    filter_data.endGameNum = 100;

    submitForm2();
}

function sortResults(sortOrder) {

    document.data_form.results_order.value = sortOrder;
    submitForm();
}

function changeNumMatchedGames() {

    let filter_data = getFilterData();
    newNumMatchedGames = 100;

    if (newNumMatchedGames !== currentNumMatchedGames) {
        submitForm();
    }
}

function downloadGames(num) {

    let filter_data = getFilterData();

    // store the existing parameters so they can be restored later    
    let old_response_format = document.data_form.response_format.value;
    let old_submit_form_action = document.submit_form.action;    
    let old_start_game_num = filter_data.startGameNum.value;

    // set the reponse format and action to indicate we want a zip file
    document.data_form.response_format.value = "org.pente.gameDatabase.ZipFileGameStorerSearchResponseStream";
    document.submit_form.action += ".zip";
    filter_data.startGameNum.value = (num - 1) * 100;

    // submit the form to get the zip file
    submitForm();

    // restore original parameters
    document.data_form.response_format.value = old_response_format;
    document.submit_form.action = old_submit_form_action;
    filter_data.startGameNum.value = old_start_game_num;
}

function changeDownloads(num) {

    document.data_form.zippedPartNumParam.value = num;
    submitForm();
}

function submitForm2() {

    let filter_data = getFilterData();

    currentMove = drawUntilMove;
    let moves = "moves=" + escape(getCurrentMoveStr());
    let response = "response_format=" + escape(document.data_form.response_format.value);
    let sortOrder = "results_order=" + escape(document.data_form.results_order.value);
    let responseParams = "zippedPartNumParam=" + escape(document.data_form.zippedPartNumParam.value);
    responseParams = "response_params=" + escape(responseParams);

    let startGameNum = "start_game_num=" + filter_data.startGameNum.value;
    let endGameNum = 100;
    endGameNum += Math.floor(filter_data.startGameNum.value);
    endGameNum = "end_game_num=" + endGameNum;

    let game = "game=" + escape(gameStr);
    let site = "site=" + escape("All Sites");
    let event = "event=" + escape("All Events");
    let round = "round=" + escape("All Rounds");
    let section = "section=" + escape("All Sections");

    let player1Name = "player_1_name=" + escape(document.filter_options_data.player_1_name.value);
    let player2Name = "player_2_name=" + escape(document.filter_options_data.player_2_name.value);

    let select = document.filter_options_data.selectWinner;
    let winner = "winner=0";

    let filterData = startGameNum + "&" + endGameNum + "&" + player1Name + "&" + player2Name + "&" +
                     game + "&" + site + "&" + event + "&" + round + "&" + section + "&" + winner;


    filterData = "filter_data=" + escape(filterData);

    document.submit_form.format_data.value = moves + "&" + 
                                             response + "&" + 
                                             responseParams + "&" + 
                                             sortOrder + "&" + 
                                             filterData;

    document.submit_form.submit();
}

function submitForm() {

    let filter_data = getFilterData();

    let moves = "moves=" + escape(getCurrentMoveStr());
    let response = "response_format=" + escape(document.data_form.response_format.value);
    let sortOrder = "results_order=" + escape(document.data_form.results_order.value);
    let responseParams = "zippedPartNumParam=" + escape(document.data_form.zippedPartNumParam.value);
    responseParams = "response_params=" + escape(responseParams);

    let startGameNum = "start_game_num=" + filter_data.startGameNum.value;
    let endGameNum = 100;
    endGameNum += Math.floor(filter_data.startGameNum.value);
    endGameNum = "end_game_num=" + endGameNum;

    let game = "game=" + escape(getSelectValue(GAME_NAME));
    let site = "site=" + escape(getSelectValue(SITE_NAME));
    let event = "event=" + escape(getSelectValue(EVENT_NAME));
    let round = "round=" + escape(getSelectValue(ROUND_NAME));
    let section = "section=" + escape(getSelectValue(SECTION_NAME));

    let player1Name = "player_1_name=" + escape(document.filter_options_data.player_1_name.value);
    let player2Name = "player_2_name=" + escape(document.filter_options_data.player_2_name.value);

    let select = document.filter_options_data.selectWinner;
    let winner = "winner=" + select[select.selectedIndex].value;

    let afterDateStr = document.filter_options_data.after_date.value;
    let beforeDateStr = document.filter_options_data.before_date.value;
    let afterDate;
    let beforeDate;
    let afterDateValid = false;
    let beforeDateValid = false;

    let aboveRatingP1 = document.filter_options_data.p1_rating_above.value;
    let aboveRatingP2 = document.filter_options_data.p2_rating_above.value;

    let excludeTimeouts = document.filter_options_data.exclude_timeouts.checked;
    let p1OrP2 = document.filter_options_data.p1_or_p2.checked;

    let liveOnly = (document.filter_options_data.typeSelect.value === "live");
    let tbOnly = (document.filter_options_data.typeSelect.value === "turn_based");

    if (isValidDate(afterDateStr)) {
        afterDate = getDate(afterDateStr);
        afterDateValid = true;
    }
    if (isValidDate(beforeDateStr)) {
        beforeDate = getDate(beforeDateStr);
        beforeDateValid = true;
    }

    if (!afterDateValid || !beforeDateValid) {
        alert("Invalid date specified, dates must be formatted MM/DD/YYYY");
        return false;
    }

    if (afterDate !== null && afterDateValid) {
        afterDate.setDate(afterDate.getDate() + 1);
    }

    if (afterDate !== null && beforeDate !== null && afterDate >= beforeDate) {
        alert("After date must be before the before date");
        return false;
    }

    let filterData = startGameNum + "&" + endGameNum + "&" + player1Name + "&" + player2Name + "&" +
                     game + "&" + site + "&" + event + "&" + round + "&" + section + "&" + winner;

    if (afterDate !== null) {
        filterData += "&" + "after_date=" + escape(getDateStr(afterDate));
    }
    if (beforeDate !== null) {
        filterData += "&" + "before_date=" + escape(getDateStr(beforeDate));
    }

    if (aboveRatingP1>0) {
        filterData += "&" + "p1_rating_above=" + aboveRatingP1;
    }
    if (aboveRatingP2>0) {
        filterData += "&" + "p2_rating_above=" + aboveRatingP2;
    }
    filterData += "&" + "exclude_timeout=" + excludeTimeouts;
    filterData += "&" + "p1_or_p2=" + p1OrP2;
    if (liveOnly) {
        filterData += "&only_live=yes";
    }
    if (tbOnly) {
        filterData += "&only_turn_based=yes";
    }

    filterData = "filter_data=" + escape(filterData);

    document.submit_form.format_data.value = moves + "&" + 
                                             response + "&" + 
                                             responseParams + "&" + 
                                             sortOrder + "&" + 
                                             filterData;

    document.submit_form.submit();
}


function isValidDate(dateStr) {
    if (dateStr.length === 0) {
        return true;
    }
    else if (dateStr.length < 10) {
        return false;
    }
    else {
        let date = getDate(dateStr);
        return date !== null;
    }
}

function getDate(dateStr) {

    let date = new Date(dateStr.substr(6),
                        dateStr.substr(0, 2) - 1,
                        dateStr.substr(3, 2));

    if (date === "NaN" || date === null) {
        return null;
    }
    let year = date.getYear();
    if (year < 1899) {
        year += 1900;
    }

    if (year == dateStr.substr(6) &&
        date.getMonth() == (dateStr.substr(0, 2) - 1) &&
        date.getDate() == dateStr.substr(3, 2)) {
        return date;
    }

    return null;
}

function getDateStr(date) {

   if (date === null || date === "NaN" || date === "undefined") {
       return "";
   }
 
   let year = date.getYear();

   if (year < 1899) {
       year += 1900;
   }

   return (date.getMonth() + 1) + "/" +
          date.getDate() + "/" + 
          year;
}

function nextGames() {

    let filter_data = getFilterData();

    let startNumGames = Math.floor(filter_data.startGameNum.value);
    let numGames = 100; // no longer selectable
    startNumGames += numGames;
    filter_data.startGameNum.value = startNumGames;
    submitForm();
}

function prevGames() {

    let filter_data = getFilterData();

    let startNumGames = Math.floor(filter_data.startGameNum.value);
    let numGames = 100;
    startNumGames -= numGames;
    if (startNumGames < 0) {
        startNumGames = 0;
    }
    filter_data.startGameNum.value = startNumGames;
    submitForm();
}

// -->