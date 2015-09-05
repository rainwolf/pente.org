var penteColor = "#FDDEA3";
var keryPenteColor = "#BAFDA3";
var gomokuColor = "#A3FDEB";
var dPenteColor = "#A3CDFD";
var gPenteColor = "#AEA3FD";
var poofPenteColor = "#EDA3FD";
var connect6Color = "#EDA3FD";
var boatPenteColor = "#25BAFF";
var abstractBoard = [[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
                    [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]];
var coordinateLetters = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T'];


function drawGame() {
    for (var i = 0; i < 19; i++) {
        for (var j = 0; j < 19; j++) {
            if (abstractBoard[i][j] > 0) {
                drawStone(i, j, (abstractBoard[i][j] == 2));
            } 
        }
    }
    drawCaptures();
}
function detectPenteCapture(abstractBoard, i, j, myColor) {
    var opponentColor = 1 + (myColor % 2);
    if ((i-3) > -1) {
        if (abstractBoard[i-3][j] == myColor) {
            if ((abstractBoard[i-1][j] == opponentColor) && (abstractBoard[i-2][j] == opponentColor)) {
                abstractBoard[i-1][j] = 0;
                abstractBoard[i-2][j] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i-3) > -1) && ((j-3) > -1)) {
        if (abstractBoard[i-3][j-3] == myColor) {
            if ((abstractBoard[i-1][j-1] == opponentColor) && (abstractBoard[i-2][j-2] == opponentColor)) {
                abstractBoard[i-1][j-1] = 0;
                abstractBoard[i-2][j-2] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if ((j-3) > -1) {
        if (abstractBoard[i][j-3] == myColor) {
            if ((abstractBoard[i][j-1] == opponentColor) && (abstractBoard[i][j-2] == opponentColor)) {
                abstractBoard[i][j-1] = 0;
                abstractBoard[i][j-2] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i+3) < 19) && ((j-3) > -1)) {
        if (abstractBoard[i+3][j-3] == myColor) {
            if ((abstractBoard[i+1][j-1] == opponentColor) && (abstractBoard[i+2][j-2] == opponentColor)) {
                abstractBoard[i+1][j-1] = 0;
                abstractBoard[i+2][j-2] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if ((i+3) < 19) {
        if (abstractBoard[i+3][j] == myColor) {
            if ((abstractBoard[i+1][j] == opponentColor) && (abstractBoard[i+2][j] == opponentColor)) {
                abstractBoard[i+1][j] = 0;
                abstractBoard[i+2][j] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i+3) < 19) && ((j+3) < 19)) {
        if (abstractBoard[i+3][j+3] == myColor) {
            if ((abstractBoard[i+1][j+1] == opponentColor) && (abstractBoard[i+2][j+2] == opponentColor)) {
                abstractBoard[i+1][j+1] = 0;
                abstractBoard[i+2][j+2] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if ((j+3) < 19) {
        if (abstractBoard[i][j+3] == myColor) {
            if ((abstractBoard[i][j+1] == opponentColor) && (abstractBoard[i][j+2] == opponentColor)) {
                abstractBoard[i][j+1] = 0;
                abstractBoard[i][j+2] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i-3) > -1) && ((j+3) < 19)) {
        if (abstractBoard[i-3][j+3] == myColor) {
            if ((abstractBoard[i-1][j+1] == opponentColor) && (abstractBoard[i-2][j+2] == opponentColor)) {
                abstractBoard[i-1][j+1] = 0;
                abstractBoard[i-2][j+2] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
}
function detectKeryoPenteCapture(abstractBoard, i, j, myColor) {
    var opponentColor = 1 + (myColor % 2);
    if ((i-4) > -1) {
        if (abstractBoard[i-4][j] == myColor) {
            if ((abstractBoard[i-1][j] == opponentColor) && (abstractBoard[i-2][j] == opponentColor) && (abstractBoard[i-3][j] == opponentColor)) {
                abstractBoard[i-1][j] = 0;
                abstractBoard[i-2][j] = 0;
                abstractBoard[i-3][j] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i-4) > -1) && ((j-4) > -1)) {
        if (abstractBoard[i-4][j-4] == myColor) {
            if ((abstractBoard[i-1][j-1] == opponentColor) && (abstractBoard[i-2][j-2] == opponentColor) && (abstractBoard[i-3][j-3] == opponentColor)) {
                abstractBoard[i-1][j-1] = 0;
                abstractBoard[i-2][j-2] = 0;
                abstractBoard[i-3][j-3] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if ((j-4) > -1) {
        if (abstractBoard[i][j-4] == myColor) {
            if ((abstractBoard[i][j-1] == opponentColor) && (abstractBoard[i][j-2] == opponentColor) && (abstractBoard[i][j-3] == opponentColor)) {
                abstractBoard[i][j-1] = 0;
                abstractBoard[i][j-2] = 0;
                abstractBoard[i][j-3] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i+4) < 19) && ((j-4) > -1)) {
        if (abstractBoard[i+4][j-4] == myColor) {
            if ((abstractBoard[i+1][j-1] == opponentColor) && (abstractBoard[i+2][j-2] == opponentColor) && (abstractBoard[i+3][j-3] == opponentColor)) {
                abstractBoard[i+1][j-1] = 0;
                abstractBoard[i+2][j-2] = 0;
                abstractBoard[i+3][j-3] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if ((i+4) < 19) {
        if (abstractBoard[i+4][j] == myColor) {
            if ((abstractBoard[i+1][j] == opponentColor) && (abstractBoard[i+2][j] == opponentColor) && (abstractBoard[i+3][j] == opponentColor)) {
                abstractBoard[i+1][j] = 0;
                abstractBoard[i+2][j] = 0;
                abstractBoard[i+3][j] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i+4) < 19) && ((j+4) < 19)) {
        if (abstractBoard[i+4][j+4] == myColor) {
            if ((abstractBoard[i+1][j+1] == opponentColor) && (abstractBoard[i+2][j+2] == opponentColor) && (abstractBoard[i+3][j+3] == opponentColor)) {
                abstractBoard[i+1][j+1] = 0;
                abstractBoard[i+2][j+2] = 0;
                abstractBoard[i+3][j+3] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if ((j+4) < 19) {
        if (abstractBoard[i][j+4] == myColor) {
            if ((abstractBoard[i][j+1] == opponentColor) && (abstractBoard[i][j+2] == opponentColor) && (abstractBoard[i][j+3] == opponentColor)) {
                abstractBoard[i][j+1] = 0;
                abstractBoard[i][j+2] = 0;
                abstractBoard[i][j+3] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i-4) > -1) && ((j+4) < 19)) {
        if (abstractBoard[i-4][j+4] == myColor) {
            if ((abstractBoard[i-1][j+1] == opponentColor) && (abstractBoard[i-2][j+2] == opponentColor) && (abstractBoard[i-3][j+3] == opponentColor)) {
                abstractBoard[i-1][j+1] = 0;
                abstractBoard[i-2][j+2] = 0;
                abstractBoard[i-3][j+3] = 0;
                if (opponentColor == 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
}
function detectPoof(abstractBoard, i, j, myColor) {
    var opponentColor = 1 + (myColor % 2);
    var poofed = false;
    if (((i-2) > -1) && ((i+1) < 19)) {
        if (abstractBoard[i-1][j] == myColor) {
            if ((abstractBoard[i-2][j] == opponentColor) && (abstractBoard[i+1][j] == opponentColor)) {
                abstractBoard[i-1][j] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-2) > -1) && ((i+1) < 19) && ((j+1) < 19)) {
        if (abstractBoard[i-1][j-1] == myColor) {
            if ((abstractBoard[i-2][j-2] == opponentColor) && (abstractBoard[i+1][j+1] == opponentColor)) {
                abstractBoard[i-1][j-1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((j-2) > -1) && ((j+1) < 19)) {
        if (abstractBoard[i][j-1] == myColor) {
            if ((abstractBoard[i][j-2] == opponentColor) && (abstractBoard[i][j+1] == opponentColor)) {
                abstractBoard[i][j-1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-2) > -1) && ((i+2) < 19) && ((j+1) < 19)) {
        if (abstractBoard[i+1][j-1] == myColor) {
            if ((abstractBoard[i-1][j+1] == opponentColor) && (abstractBoard[i+2][j-2] == opponentColor)) {
                abstractBoard[i+1][j-1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i+2) < 19) && ((i-1) > -1)) {
        if (abstractBoard[i+1][j] == myColor) {
            if ((abstractBoard[i+2][j] == opponentColor) && (abstractBoard[i-1][j] == opponentColor)) {
                abstractBoard[i+1][j] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-1) > -1) && ((i+2) < 19) && ((j+2) < 19)) {
        if (abstractBoard[i+1][j+1] == myColor) {
            if ((abstractBoard[i-1][j-1] == opponentColor) && (abstractBoard[i+2][j+2] == opponentColor)) {
                abstractBoard[i+1][j+1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((j+2) < 19) && ((j-1) > -1)) {
        if (abstractBoard[i][j+1] == myColor) {
            if ((abstractBoard[i][j-1] == opponentColor) && (abstractBoard[i][j+2] == opponentColor)) {
                abstractBoard[i][j+1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-1) > -1) && ((i+1) < 19) && ((j+2) < 19)) {
        if (abstractBoard[i-1][j+1] == myColor) {
            if ((abstractBoard[i+1][j-1] == opponentColor) && (abstractBoard[i-2][j+2] == opponentColor)) {
                abstractBoard[i-1][j+1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor == 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    
    if (poofed) {
        if (myColor == 1) {
            ++whiteCaptures;
        } else {
            ++blackCaptures;
        }
    }

}
function resetAbstractBoard(abstractBoard) {
    for (var i = 0; i < 19; i++) {
        for (var j = 0; j < 19; j++) {
            abstractBoard[i][j] = 0;
        }
    }
}


function replayGomokuGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
    }
}
function replayPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (rated && (moves.length == 2)) {
        for(var i = 7; i < 12; ++i)
            for(var j = 7; j < 12; ++j)
                if (abstractBoard[i][j] == 0) {
                    abstractBoard[i][j] = -1;
                }
    }
}
function replayKeryoPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
        detectKeryoPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (rated && (moves.length == 2)) {
        for(var i = 7; i < 12; ++i) {
            for(var j = 7; j < 12; ++j) {
                if (abstractBoard[i][j] == 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
    }
}
function replayConnect6Game(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = (((i % 4) == 0) || ((i % 4) == 3)) ? 1 : 2;
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
    }
}
function replayGPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (moves.length == 2) {
        for(var i = 7; i < 12; i++) {
            for(var j = 7; j < 12; j++) {
                if (abstractBoard[i][j] == 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
        for(var i = 1; i < 3; i++) {
            if (abstractBoard[9][11 + i] == 0) {
                abstractBoard[9][11 + i] = -1;
            }
            if (abstractBoard[9][7 - i] == 0) {
                abstractBoard[9][7 - i] = -1;
            }
            if (abstractBoard[11 + i][9] == 0) {
                abstractBoard[11 + i][9] = -1;
            }
            if (abstractBoard[7 - i][9] == 0) {
                abstractBoard[7 - i][9] = -1;
            }
        }
    }
}
function replayPoofPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPoof(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (rated && (moves.length == 2)) {
        for(var i = 7; i < 12; ++i) {
            for(var j = 7; j < 12; ++j) {
                if (abstractBoard[i][j] == 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
    }
}




