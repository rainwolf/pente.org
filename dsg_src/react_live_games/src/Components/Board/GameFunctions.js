let penteColor = "#FDDEA3";
let keryPenteColor = "#BAFDA3";
let gomokuColor = "#A3FDEB";
let dPenteColor = "#A3CDFD";
let gPenteColor = "#AEA3FD";
let poofPenteColor = "#EDA3FD";
let connect6Color = "#EDA3FD";
let boatPenteColor = "#25BAFF";
let dkeryoPenteColor = "#FFA500";
let goColor = "#FAC832";

let abstractBoard = [[0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
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
let coordinateLetters = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T'];

let whiteCaptures = 0, blackCaptures = 0;
// let c6Move1 = -1, c6Move2 = -1, dPenteMove1 = -1, dPenteMove2 = -1, dPenteMove3 = -1, dPenteMove4 = -1;

let goGroupsByPlayerAndID = {1: {}, 2: {}}, goStoneGroupIDsByPlayer = {1: {}, 2: {}};
let koMove = -1;
let suicideAllowed = false;
let goTerritoryByPlayer = {1: [], 2: []};
let territoryDrawn = false;
let passMove = 361;
let handicapPass = passMove + 1;

let goDeadStonesByPlayer = {1: [], 2: []};


// function leftRight(e) {
//
//     e = e || window.event;
//
//     if (document.getElementById("message") === document.activeElement) {
//         return;
//     }
//
//     if (e.keyCode == '37' && typeof goBack === "function") {
//         goBack();
//     } else if (e.keyCode == '39' && typeof goForward === "function") {
//         goForward();
//     }
//
// }


function replayGoGame(abstractBoard, movesList, until) {
    passMove = gridSize*gridSize;
    resetAbstractBoard(abstractBoard);
    goGroupsByPlayerAndID = {1: {}, 2: {}};
    goStoneGroupIDsByPlayer = {1: {}, 2: {}};
    goDeadStonesByPlayer = {1: [], 2: []};
    let p1DeadStones = goDeadStonesByPlayer[1], p2DeadStones = goDeadStonesByPlayer[2];
    koMove = -1;
    let hasPass = false, doublePass = false;
    for (let i = 0; i < Math.min(movesList.length, until); i++) {
        let move = movesList[i];
        if (move === passMove) {
            if (hasPass) {
                doublePass = true;
            } else {
                hasPass = true;
            }
        } else {
            hasPass = false;
        }
        if (move !== passMove && !doublePass) {
            let color = 2 - (i%2);
            abstractBoard[move % gridSize][Math.floor(move / gridSize)] = color;
            addGoMove(move, 3-color);
        } else if (doublePass && move !== passMove) {
            let pos = getPosition(move);
            if (pos === 1) {
                p2DeadStones.push(move);
            } else if (pos === 2) {
                p1DeadStones.push(move);
            }
            setPosition(move, 0);
        }
    }
}

function addGoMove(move, currentPlayer) {
    if (move >= passMove) {
        return;
    }
    let opponent = 3 - currentPlayer;
    let groupsByID = goGroupsByPlayerAndID[currentPlayer];
    let stoneGroupIDs = goStoneGroupIDsByPlayer[currentPlayer];

    settleGroups(move, groupsByID, stoneGroupIDs);
    groupsByID = goGroupsByPlayerAndID[opponent];
    stoneGroupIDs = goStoneGroupIDsByPlayer[opponent];
    makeCaptures(move, groupsByID, stoneGroupIDs, opponent);

    if (suicideAllowed === true) {
        groupsByID = goGroupsByPlayerAndID[currentPlayer];
        stoneGroupIDs = goStoneGroupIDsByPlayer[currentPlayer];
        let moveGroupID = stoneGroupIDs[move];
        let moveGroup = groupsByID[moveGroupID];
        if (!groupHasLiberties(moveGroup)) {
            if (currentPlayer !== 1) {
                whiteCaptures += moveGroup.size();
            } else {
                blackCaptures += moveGroup.size();
            }
            captureGroup(moveGroupID, groupsByID, stoneGroupIDs);
        }
    }
}

function makeCaptures(move, groupsByID, stoneGroupIDs, colorToCapture) {
    let captures = 0;
    if (move%gridSize !== 0) {
        let neighborStone = move - 1;
        let neighborStoneGroupID = stoneGroupIDs[neighborStone];
        captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
    }
    if (move%gridSize !== gridSize - 1) {
        neighborStone = move + 1;
        neighborStoneGroupID = stoneGroupIDs[neighborStone];
        captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
    }
    if (Math.floor(move/gridSize) !== 0) {
        neighborStone = move - gridSize;
        neighborStoneGroupID = stoneGroupIDs[neighborStone];
        captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
    }
    if (Math.floor(move/gridSize) !== gridSize - 1) {
        neighborStone = move + gridSize;
        neighborStoneGroupID = stoneGroupIDs[neighborStone];
        captures = getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID);
    }
    if (captures !== 1) {
        koMove = -1;
    }
    if (colorToCapture === 1) {
        blackCaptures += captures;
    } else {
        whiteCaptures += captures;
    }
}

function getCaptures(move, groupsByID, stoneGroupIDs, captures, neighborStone, neighborStoneGroupID) {
    if (neighborStoneGroupID === undefined) {
        return captures;
    }
    let newCaptures = captures;
    let neighborStoneGroup = groupsByID[neighborStoneGroupID];
    if (neighborStoneGroup !== undefined) {
        if (groupHasLiberties(neighborStoneGroup) === false) {
            if (koMove < 0 && neighborStoneGroup.length === 1 && checkKo(move)) {
                koMove = neighborStone;
            } else {
                koMove = -1;
            }
            newCaptures = captures + neighborStoneGroup.length;
            captureGroup(neighborStoneGroupID, groupsByID, stoneGroupIDs);
        }
    }
    return newCaptures;
}
function checkKo(move) {
    let position = getPosition(move);
    if (move%gridSize !== 0) {
        let neighborStone = move - 1;
        let neighborPosition = getPosition(neighborStone);
        if (position !== 3 - neighborPosition) {
            return false;
        }
    }
    if (move%gridSize !== gridSize - 1) {
        neighborStone = move + 1;
        neighborPosition = getPosition(neighborStone);
        if (position !== 3 - neighborPosition) {
            return false;
        }
    }
    if (Math.floor(move/gridSize) !== 0) {
        neighborStone = move - gridSize;
        neighborPosition = getPosition(neighborStone);
        if (position !== 3 - neighborPosition) {
            return false;
        }
    }
    if (Math.floor(move/gridSize) !== gridSize - 1) {
        neighborStone = move + gridSize;
        neighborPosition = getPosition(neighborStone);
        if (position !== 3 - neighborPosition) {
            return false;
        }
    }
    return true;
}
function captureGroup(groupID, groupsByID, stoneGroupIDs) {
    let group = groupsByID[groupID];
    for(let i = 0; i < group.length; ++i) {
        setPosition(group[i], 0);
        delete stoneGroupIDs[group[i]];
    }
    delete groupsByID[groupID];
}
function groupHasLiberties(group) {
    for (let i = 0; i < group.length; i++) {
        if (stoneHasLiberties(group[i]) === true) {
            return true;
        }
    }
    return false;
}

function stoneHasLiberties(stone) {
    if (stone%gridSize !== 0) {
        let neighborStone = stone - 1;
        let position = getPosition(neighborStone);
        if (position !== 1 && position !== 2) {
            return true;
        }
    }
    if (stone%gridSize !== gridSize - 1) {
        neighborStone = stone + 1;
        position = getPosition(neighborStone);
        if (position !== 1 && position !== 2) {
            return true;
        }
    }
    if (Math.floor(stone/gridSize) !== 0) {
        neighborStone = stone - gridSize;
        position = getPosition(neighborStone);
        if (position !== 1 && position !== 2) {
            return true;
        }
    }
    if (Math.floor(stone/gridSize) !== gridSize - 1) {
        neighborStone = stone + gridSize;
        position = getPosition(neighborStone);
        if (position !== 1 && position !== 2) {
            return true;
        }
    }
    return false;
}

function getMoveCoord(move) {
    let letter = coordinateLetters[move%gridSize];
    let number = gridSize-Math.floor(move/gridSize);
    return letter + number;
}
function getPosition(move) {
    return abstractBoard[move%gridSize][Math.floor(move/gridSize)];
}
function setPosition(move, val) {
    abstractBoard[move%gridSize][Math.floor(move/gridSize)] = val;
}

function settleGroups(move, groupsByID, stoneGroupIDs) {
    let newGroup = [];
    newGroup.push(move);
    groupsByID[move] = newGroup;
    stoneGroupIDs[move] = move;

    if (move%gridSize !== 0) {
        let neighborStone = move - 1;
        let neighborStoneGroupID = stoneGroupIDs[neighborStone];
        if (neighborStoneGroupID !== undefined) {
            mergeGroups(move, neighborStoneGroupID, groupsByID, stoneGroupIDs);
        }
    }
    if (move%gridSize !== gridSize - 1) {
        neighborStone = move + 1;
        neighborStoneGroupID = stoneGroupIDs[neighborStone];
        if (neighborStoneGroupID !== undefined) {
            mergeGroups(stoneGroupIDs[move], neighborStoneGroupID, groupsByID, stoneGroupIDs);
        }
    }
    if (Math.floor(move/gridSize) !== 0) {
        neighborStone = move - gridSize;
        neighborStoneGroupID = stoneGroupIDs[neighborStone];
        if (neighborStoneGroupID !== undefined) {
            mergeGroups(stoneGroupIDs[move], neighborStoneGroupID, groupsByID, stoneGroupIDs);
        }
    }
    if (Math.floor(move/gridSize) !== gridSize - 1) {
        neighborStone = move + gridSize;
        neighborStoneGroupID = stoneGroupIDs[neighborStone];
        if (neighborStoneGroupID !== undefined) {
            mergeGroups(stoneGroupIDs[move], neighborStoneGroupID, groupsByID, stoneGroupIDs);
        }
    }
}
function mergeGroups(group1, group2, groupsByID, stoneGroupIDs) {
    if (group1 === group2) {
        return;
    }
    let oldGroup, newGroup;
    let oldGroupID, newGroupID;
    if (group1 < group2) {
        oldGroup = groupsByID[group1];
        newGroup = groupsByID[group2];
        oldGroupID = group1;
        newGroupID = group2;
    } else {
        oldGroup = groupsByID[group2];
        newGroup = groupsByID[group1];
        oldGroupID = group2;
        newGroupID = group1;
    }
    for(let i = 0; i < oldGroup.length; ++i) {
        newGroup.push(oldGroup[i]);
        stoneGroupIDs[oldGroup[i]] = newGroupID;
    }
    delete groupsByID[oldGroupID];
}

function getEmptyNeighbour(move) {
    if (move%gridSize !== 0) {
        let neighborStone = move - 1;
        if (getPosition(neighborStone) === 0) {
            return neighborStone;
        }
    }
    if (move%gridSize !== gridSize - 1) {
        neighborStone = move + 1;
        if (getPosition(neighborStone) === 0) {
            return neighborStone;
        }
    }
    if (Math.floor(move/gridSize) !== 0) {
        neighborStone = move - gridSize;
        if (getPosition(neighborStone) === 0) {
            return neighborStone;
        }
    }
    if (Math.floor(move/gridSize) !== gridSize - 1) {
        neighborStone = move + gridSize;
        if (getPosition(neighborStone) === 0) {
            return neighborStone;
        }
    }
    return -1;
}

function floodFillWorker(move, value) {
    setPosition(move, value);
    let neighbourStone = getEmptyNeighbour(move);
    while (neighbourStone !== -1) {
        floodFillWorker(neighbourStone, value);
        neighbourStone = getEmptyNeighbour(move);
    }
}
function resetGoBeforeFlood() {
    for (let i = 0; i < gridSize; i++ ) {
        for (let j = 0; j < gridSize; j++ ) {
            if (abstractBoard[i][j] !== 1 && abstractBoard[i][j] !== 2) {
                abstractBoard[i][j] = 0;
            }
        }
    }
}
function floodPlayer(player) {
    for (let move = 0; move < gridSize*gridSize; move++) {
        if (getPosition(move) === 3-player) {
            let neighbourStone = getEmptyNeighbour(move);
            while (neighbourStone > -1) {
                floodFillWorker(neighbourStone, player + 2);
                neighbourStone = getEmptyNeighbour(move);
            }
        }
    }
}
function getMovesForValue(value) {
    let result = [];
    for (let j = 0; j < gridSize; j++ ) {
        for (let i = 0; i < gridSize; i++ ) {
            if (abstractBoard[i][j] === value) {
                result.push(j*gridSize+i);
            }
        }
    }
    return result;
}
function getTerritories() {
    goTerritoryByPlayer = {1: [], 2: []};
    resetGoBeforeFlood();
    floodPlayer(1);
    let p1Territory = getMovesForValue(3);
    resetGoBeforeFlood();
    floodPlayer(2);
    let p2Territory = getMovesForValue(4);
    resetGoBeforeFlood();
    let i = 0, j = 0;
    while (i < p1Territory.length && j < p2Territory.length) {
        let p1Stone = p1Territory[i], p2Stone = p2Territory[j];
        if (p1Stone === p2Stone) {
            p1Territory.splice(i, 1);
            p2Territory.splice(j, 1);
        } else {
            if (p1Stone < p2Stone) {
                i += 1;
            } else {
                j += 1;
            }
        }
    }
    goTerritoryByPlayer[1] = p1Territory;
    goTerritoryByPlayer[2] = p2Territory;
}


function detectPenteCapture(abstractBoard, i, j, myColor) {
    let opponentColor = 1 + (myColor % 2);
    if ((i-3) > -1) {
        if (abstractBoard[i-3][j] === myColor) {
            if ((abstractBoard[i-1][j] === opponentColor) && (abstractBoard[i-2][j] === opponentColor)) {
                abstractBoard[i-1][j] = 0;
                abstractBoard[i-2][j] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i-3) > -1) && ((j-3) > -1)) {
        if (abstractBoard[i-3][j-3] === myColor) {
            if ((abstractBoard[i-1][j-1] === opponentColor) && (abstractBoard[i-2][j-2] === opponentColor)) {
                abstractBoard[i-1][j-1] = 0;
                abstractBoard[i-2][j-2] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if ((j-3) > -1) {
        if (abstractBoard[i][j-3] === myColor) {
            if ((abstractBoard[i][j-1] === opponentColor) && (abstractBoard[i][j-2] === opponentColor)) {
                abstractBoard[i][j-1] = 0;
                abstractBoard[i][j-2] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i+3) < 19) && ((j-3) > -1)) {
        if (abstractBoard[i+3][j-3] === myColor) {
            if ((abstractBoard[i+1][j-1] === opponentColor) && (abstractBoard[i+2][j-2] === opponentColor)) {
                abstractBoard[i+1][j-1] = 0;
                abstractBoard[i+2][j-2] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if ((i+3) < 19) {
        if (abstractBoard[i+3][j] === myColor) {
            if ((abstractBoard[i+1][j] === opponentColor) && (abstractBoard[i+2][j] === opponentColor)) {
                abstractBoard[i+1][j] = 0;
                abstractBoard[i+2][j] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i+3) < 19) && ((j+3) < 19)) {
        if (abstractBoard[i+3][j+3] === myColor) {
            if ((abstractBoard[i+1][j+1] === opponentColor) && (abstractBoard[i+2][j+2] === opponentColor)) {
                abstractBoard[i+1][j+1] = 0;
                abstractBoard[i+2][j+2] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if ((j+3) < 19) {
        if (abstractBoard[i][j+3] === myColor) {
            if ((abstractBoard[i][j+1] === opponentColor) && (abstractBoard[i][j+2] === opponentColor)) {
                abstractBoard[i][j+1] = 0;
                abstractBoard[i][j+2] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
    if (((i-3) > -1) && ((j+3) < 19)) {
        if (abstractBoard[i-3][j+3] === myColor) {
            if ((abstractBoard[i-1][j+1] === opponentColor) && (abstractBoard[i-2][j+2] === opponentColor)) {
                abstractBoard[i-1][j+1] = 0;
                abstractBoard[i-2][j+2] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 2;
                } else {
                    blackCaptures += 2;
                }
            }
        }
    }
}
function detectKeryoPenteCapture(abstractBoard, i, j, myColor) {
    let opponentColor = 1 + (myColor % 2);
    if ((i-4) > -1) {
        if (abstractBoard[i-4][j] === myColor) {
            if ((abstractBoard[i-1][j] === opponentColor) && (abstractBoard[i-2][j] === opponentColor) && (abstractBoard[i-3][j] === opponentColor)) {
                abstractBoard[i-1][j] = 0;
                abstractBoard[i-2][j] = 0;
                abstractBoard[i-3][j] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i-4) > -1) && ((j-4) > -1)) {
        if (abstractBoard[i-4][j-4] === myColor) {
            if ((abstractBoard[i-1][j-1] === opponentColor) && (abstractBoard[i-2][j-2] === opponentColor) && (abstractBoard[i-3][j-3] === opponentColor)) {
                abstractBoard[i-1][j-1] = 0;
                abstractBoard[i-2][j-2] = 0;
                abstractBoard[i-3][j-3] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if ((j-4) > -1) {
        if (abstractBoard[i][j-4] === myColor) {
            if ((abstractBoard[i][j-1] === opponentColor) && (abstractBoard[i][j-2] === opponentColor) && (abstractBoard[i][j-3] === opponentColor)) {
                abstractBoard[i][j-1] = 0;
                abstractBoard[i][j-2] = 0;
                abstractBoard[i][j-3] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i+4) < 19) && ((j-4) > -1)) {
        if (abstractBoard[i+4][j-4] === myColor) {
            if ((abstractBoard[i+1][j-1] === opponentColor) && (abstractBoard[i+2][j-2] === opponentColor) && (abstractBoard[i+3][j-3] === opponentColor)) {
                abstractBoard[i+1][j-1] = 0;
                abstractBoard[i+2][j-2] = 0;
                abstractBoard[i+3][j-3] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if ((i+4) < 19) {
        if (abstractBoard[i+4][j] === myColor) {
            if ((abstractBoard[i+1][j] === opponentColor) && (abstractBoard[i+2][j] === opponentColor) && (abstractBoard[i+3][j] === opponentColor)) {
                abstractBoard[i+1][j] = 0;
                abstractBoard[i+2][j] = 0;
                abstractBoard[i+3][j] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i+4) < 19) && ((j+4) < 19)) {
        if (abstractBoard[i+4][j+4] === myColor) {
            if ((abstractBoard[i+1][j+1] === opponentColor) && (abstractBoard[i+2][j+2] === opponentColor) && (abstractBoard[i+3][j+3] === opponentColor)) {
                abstractBoard[i+1][j+1] = 0;
                abstractBoard[i+2][j+2] = 0;
                abstractBoard[i+3][j+3] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if ((j+4) < 19) {
        if (abstractBoard[i][j+4] === myColor) {
            if ((abstractBoard[i][j+1] === opponentColor) && (abstractBoard[i][j+2] === opponentColor) && (abstractBoard[i][j+3] === opponentColor)) {
                abstractBoard[i][j+1] = 0;
                abstractBoard[i][j+2] = 0;
                abstractBoard[i][j+3] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
    if (((i-4) > -1) && ((j+4) < 19)) {
        if (abstractBoard[i-4][j+4] === myColor) {
            if ((abstractBoard[i-1][j+1] === opponentColor) && (abstractBoard[i-2][j+2] === opponentColor) && (abstractBoard[i-3][j+3] === opponentColor)) {
                abstractBoard[i-1][j+1] = 0;
                abstractBoard[i-2][j+2] = 0;
                abstractBoard[i-3][j+3] = 0;
                if (opponentColor === 1) {
                    whiteCaptures += 3;
                } else {
                    blackCaptures += 3;
                }
            }
        }
    }
}
function detectPoof(abstractBoard, i, j, myColor) {
    let opponentColor = 1 + (myColor % 2);
    let poofed = false;
    if (((i-2) > -1) && ((i+1) < 19)) {
        if (abstractBoard[i-1][j] === myColor) {
            if ((abstractBoard[i-2][j] === opponentColor) && (abstractBoard[i+1][j] === opponentColor)) {
                abstractBoard[i-1][j] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-2) > -1) && ((i+1) < 19) && ((j+1) < 19)) {
        if (abstractBoard[i-1][j-1] === myColor) {
            if ((abstractBoard[i-2][j-2] === opponentColor) && (abstractBoard[i+1][j+1] === opponentColor)) {
                abstractBoard[i-1][j-1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((j-2) > -1) && ((j+1) < 19)) {
        if (abstractBoard[i][j-1] === myColor) {
            if ((abstractBoard[i][j-2] === opponentColor) && (abstractBoard[i][j+1] === opponentColor)) {
                abstractBoard[i][j-1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-2) > -1) && ((i+2) < 19) && ((j+1) < 19)) {
        if (abstractBoard[i+1][j-1] === myColor) {
            if ((abstractBoard[i-1][j+1] === opponentColor) && (abstractBoard[i+2][j-2] === opponentColor)) {
                abstractBoard[i+1][j-1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i+2) < 19) && ((i-1) > -1)) {
        if (abstractBoard[i+1][j] === myColor) {
            if ((abstractBoard[i+2][j] === opponentColor) && (abstractBoard[i-1][j] === opponentColor)) {
                abstractBoard[i+1][j] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-1) > -1) && ((j-1) > -1) && ((i+2) < 19) && ((j+2) < 19)) {
        if (abstractBoard[i+1][j+1] === myColor) {
            if ((abstractBoard[i-1][j-1] === opponentColor) && (abstractBoard[i+2][j+2] === opponentColor)) {
                abstractBoard[i+1][j+1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((j+2) < 19) && ((j-1) > -1)) {
        if (abstractBoard[i][j+1] === myColor) {
            if ((abstractBoard[i][j-1] === opponentColor) && (abstractBoard[i][j+2] === opponentColor)) {
                abstractBoard[i][j+1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }
    if (((i-2) > -1) && ((j-1) > -1) && ((i+1) < 19) && ((j+2) < 19)) {
        if (abstractBoard[i-1][j+1] === myColor) {
            if ((abstractBoard[i+1][j-1] === opponentColor) && (abstractBoard[i-2][j+2] === opponentColor)) {
                abstractBoard[i-1][j+1] = 0;
                abstractBoard[i][j] = 0;
                if (myColor === 1) {
                    ++whiteCaptures;
                } else {
                    ++blackCaptures;
                }
                poofed = true;
            }
        }
    }

    if (poofed) {
        if (myColor === 1) {
            ++whiteCaptures;
        } else {
            ++blackCaptures;
        }
    }

}
function resetAbstractBoard(abstractBoard) {
    for (let i = 0; i < 19; i++) {
        for (let j = 0; j < 19; j++) {
            abstractBoard[i][j] = 0;
        }
    }
}


function replayGomokuGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (let i = 0; i < Math.min(movesList.length, until); i++) {
        let color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
    }
}
function replayPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (let i = 0; i < Math.min(movesList.length, until); i++) {
        let color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (rated && (moves.length === 2)) {
        for(i = 7; i < 12; ++i)
            for(let j = 7; j < 12; ++j)
                if (abstractBoard[i][j] === 0) {
                    abstractBoard[i][j] = -1;
                }
    }
}
function replayKeryoPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (let i = 0; i < Math.min(movesList.length, until); i++) {
        let color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
        detectKeryoPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (rated && (moves.length === 2)) {
        for(i = 7; i < 12; ++i) {
            for(let j = 7; j < 12; ++j) {
                if (abstractBoard[i][j] === 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
    }
}
function replayConnect6Game(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (let i = 0; i < Math.min(movesList.length, until); i++) {
        let color = (((i % 4) === 0) || ((i % 4) === 3)) ? 1 : 2;
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
    }
}
function replayGPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (let i = 0; i < Math.min(movesList.length, until); i++) {
        let color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (moves.length === 2) {
        for(i = 7; i < 12; i++) {
            for(let j = 7; j < 12; j++) {
                if (abstractBoard[i][j] === 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
        for(i = 1; i < 3; i++) {
            if (abstractBoard[9][11 + i] === 0) {
                abstractBoard[9][11 + i] = -1;
            }
            if (abstractBoard[9][7 - i] === 0) {
                abstractBoard[9][7 - i] = -1;
            }
            if (abstractBoard[11 + i][9] === 0) {
                abstractBoard[11 + i][9] = -1;
            }
            if (abstractBoard[7 - i][9] === 0) {
                abstractBoard[7 - i][9] = -1;
            }
        }
    }
}
function replayPoofPenteGame(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (let i = 0; i < Math.min(movesList.length, until); i++) {
        let color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPoof(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (rated && (moves.length === 2)) {
        for(i = 7; i < 12; ++i) {
            for(let j = 7; j < 12; ++j) {
                if (abstractBoard[i][j] === 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
    }
}




