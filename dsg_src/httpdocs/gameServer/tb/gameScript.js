var penteColor = "#FDDEA3";
var keryPenteColor = "#BAFDA3";
var gomokuColor = "#A3FDEB";
var dPenteColor = "#A3CDFD";
var gPenteColor = "#AEA3FD";
var poofPenteColor = "#EDA3FD";
var connect6Color = "#EDA3FD";
var boatPenteColor = "#25BAFF";
var dkeryoPenteColor = "#FFA500";
var goColor = "#FAC832";

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
        
        var whiteCaptures = 0, blackCaptures = 0;
        var c6Move1 = -1, c6Move2 = -1, dPenteMove1 = -1, dPenteMove2 = -1, dPenteMove3 = -1, dPenteMove4 = -1;

        var stoneCanvas = document.getElementById("stone");
        var stoneContext = stoneCanvas.getContext("2d");
        var interactionCanvas = document.getElementById("interactionLayer");
        var interactionContext = interactionCanvas.getContext("2d");

        var goGroupsByPlayerAndID = {1: {}, 2: {}}, goStoneGroupIDsByPlayer = {1: {}, 2: {}};
        var koMove = -1;
        var suicideAllowed = false;
        var goTerritoryByPlayer = {1: [], 2: []};
        var territoryDrawn = false;
        var passMove = gridSize*gridSize;
        var handicapPass = passMove + 1;

        var goDeadStonesByPlayer = {1: [], 2: []};
        
        
function leftRight(e) {

    e = e || window.event;
    

    if (e.keyCode == '37' && typeof goBack === "function") {
        goBack();
    } else if (e.keyCode == '39' && typeof goForward === "function") {
        goForward();
    }

}

function drawGame() {
    gridSize = 19;
    for (var i = 0; i < gridSize; i++) {
        for (var j = 0; j < gridSize; j++) {
            if (abstractBoard[i][j] > 0) {
                drawStone(i, j, abstractBoard[i][j]);
            } 
        }
    }
    if (game === 69) {
        drawGoCaptures();
        var p1DeadStones = goDeadStonesByPlayer[1];
        for ( var i = 0; i < p1DeadStones.length; i++ ) {
            drawDeadStone(p1DeadStones[i], 2);
        }
        var p2DeadStones = goDeadStonesByPlayer[2];
        for ( i = 0; i < p2DeadStones.length; i++ ) {
            drawDeadStone(p2DeadStones[i], 1);
        }
    } else {
        drawCaptures();
    }
}

function drawRedDot(i, j) {
    if (i>=gridSize || j>=gridSize) {
        return;
    }
    var centerX = indentWidth + stepX * (i);
    var centerY = indentHeight + stepY * (j);
    boardContext.beginPath();
    boardContext.arc(centerX, centerY, stepX / 7, 0, Math.PI * 2, true);
    boardContext.fillStyle = 'red';
    boardContext.fill();
    boardContext.closePath();
}
function drawStone(i, j, color) {
    if (color < 1 || color > 2) {
        return;
    }
    boardContext.save();
    var centerX = indentWidth + stepX*(i);
    var centerY = indentHeight + stepY*(j);
    boardContext.beginPath();
    boardContext.arc(centerX, centerY, radius , 0, Math.PI*2, true);
    if (color === 2) {
        boardContext.fillStyle = 'black';
    } else {
        boardContext.fillStyle = 'white';
    }
    centerX -= radius/8;
    centerY -= radius/8;
    boardContext.shadowColor = 'DimGray';
    boardContext.shadowBlur = 1;
    boardContext.shadowOffsetX = radius/8;
    boardContext.shadowOffsetY = radius/8;
    if (color === 2) {
        var gradient = boardContext.createRadialGradient(centerX, centerY, radius / 8, centerX, centerY, radius);
        gradient.addColorStop(0, 'Grey');
        gradient.addColorStop(1, 'Black');
        boardContext.fillStyle = gradient;
    } else {
        gradient = boardContext.createRadialGradient(centerX, centerY, 2*radius / 4, centerX, centerY, radius);
        gradient.addColorStop(0, 'White');
        gradient.addColorStop(1, 'Gainsboro');
        boardContext.fillStyle = gradient;
    }
    boardContext.fill();
    // boardContext.lineWidth = 5;
    // boardContext.strokeStyle = '#003300';
    // boardContext.stroke();
    boardContext.closePath();
    boardContext.restore();
}

function drawDeadStone(move, color) {
    if (color < 1 || color > 2) {
        return;
    }
    var i = move%gridSize, j = Math.floor(move/gridSize);
    boardContext.save();
    var centerX = indentWidth + stepX * (i);
    var centerY = indentHeight + stepY * (j);
    boardContext.globalAlpha = 0.75;
    boardContext.beginPath();
    if (color === 2) {
        boardContext.fillStyle = 'black';
    } else {
        boardContext.fillStyle = 'white';
    }
    boardContext.arc(centerX, centerY, radius, 0, Math.PI * 2, true);
    centerX -= radius / 8;
    centerY -= radius / 8;
    // boardContext.shadowColor = 'DimGray';
    // boardContext.shadowBlur = 1;
    // boardContext.shadowOffsetX = radius / 8;
    // boardContext.shadowOffsetY = radius / 8;
    if (color === 2) {
        var gradient = boardContext.createRadialGradient(centerX, centerY, radius / 8, centerX, centerY, radius);
        gradient.addColorStop(0, 'Grey');
        gradient.addColorStop(1, 'Black');
        boardContext.fillStyle = gradient;
    } else {
        gradient = boardContext.createRadialGradient(centerX, centerY, 2 * radius / 4, centerX, centerY, radius);
        gradient.addColorStop(0, 'White');
        gradient.addColorStop(1, 'Gainsboro');
        boardContext.fillStyle = gradient;
    }
    boardContext.fill();
    // boardContext.lineWidth = 5;
    // boardContext.strokeStyle = '#003300';
    // boardContext.stroke();
    boardContext.closePath();
    boardContext.restore();
    boardContext.globalAlpha = 1;
}


function drawTerritorySquare(move, color) {
    if (color < 1 || color > 2) {
        return;
    }
    var i = move % gridSize, j = Math.floor(move/gridSize);
    boardContext.save();
    var width = 2*radius/3
    var centerX = indentWidth + stepX * i - width/2;
    var centerY = indentHeight + stepY * j - width/2;
    if (color === 1) {
        boardContext.fillStyle = 'black';
    } else {
        boardContext.fillStyle = 'white';
    }
    boardContext.beginPath();
    boardContext.rect(centerX, centerY, width, width);
    boardContext.fill();
    boardContext.closePath();
    boardContext.restore();
}


function replayGoGame(abstractBoard, movesList, until) {
    passMove = gridSize*gridSize;
    resetAbstractBoard(abstractBoard);
    goGroupsByPlayerAndID = {1: {}, 2: {}}; 
    goStoneGroupIDsByPlayer = {1: {}, 2: {}};
    goDeadStonesByPlayer = {1: [], 2: []};
    var p1DeadStones = goDeadStonesByPlayer[1], p2DeadStones = goDeadStonesByPlayer[2];
    koMove = -1;
    var hasPass = false, doublePass = false;
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var move = movesList[i];
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
            var color = 2 - (i%2);
            abstractBoard[move % 19][Math.floor(move / 19)] = color;
            addGoMove(move, 3-color);
        } else if (doublePass && move !== passMove) {
            var pos = getPosition(move);
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
    var opponent = 3 - currentPlayer;
    // console.log(goStoneGroupIDsByPlayer);
    var groupsByID = goGroupsByPlayerAndID[currentPlayer];
    var stoneGroupIDs = goStoneGroupIDsByPlayer[currentPlayer];
    // console.log("currentPlayer: " + currentPlayer);
    // console.log(groupsByID);
    // console.log(stoneGroupIDs);
    
    settleGroups(move, groupsByID, stoneGroupIDs);
    groupsByID = goGroupsByPlayerAndID[opponent];
    stoneGroupIDs = goStoneGroupIDsByPlayer[opponent];
    makeCaptures(move, groupsByID, stoneGroupIDs, opponent);

    if (suicideAllowed === true) {
        groupsByID = goGroupsByPlayerAndID[currentPlayer];
        stoneGroupIDs = goStoneGroupIDsByPlayer[currentPlayer];
        var moveGroupID = stoneGroupIDs[move];
        var moveGroup = groupsByID[moveGroupID];
        if (!groupHasLiberties(moveGroup)) {
            if (currentPlayer !== 1) {
                whiteCaptures += moveGroup.size();
            } else {
                blackCaptures += moveGroup.size();
            }
            captureGroup(moveGroupID, groupsByID, stoneGroupIDs);
        }
    }
    
    // console.log(abstractBoard);
}

function makeCaptures(move, groupsByID, stoneGroupIDs, colorToCapture) {
    var captures = 0;
    if (move%gridSize !== 0) {
        var neighborStone = move - 1;
        var neighborStoneGroupID = stoneGroupIDs[neighborStone];
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
    var newCaptures = captures;
    var neighborStoneGroup = groupsByID[neighborStoneGroupID];
    // console.log(getMoveCoord(move));
    // console.log(groupsByID);
    if (neighborStoneGroup !== undefined) {
        if (groupHasLiberties(neighborStoneGroup) === false) {
            // console.log("capture");
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
    var position = getPosition(move);
    if (move%gridSize !== 0) {
        var neighborStone = move - 1;
        var neighborPosition = getPosition(neighborStone);
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
    var group = groupsByID[groupID];
    for(var i = 0; i < group.length; ++i) {
        setPosition(group[i], 0);
        delete stoneGroupIDs[group[i]];
    }
    delete groupsByID[groupID];
}
function groupHasLiberties(group) {
    for (var i = 0; i < group.length; i++) {
        if (stoneHasLiberties(group[i]) === true) {
            return true;
        }
    }
    return false;
}

function stoneHasLiberties(stone) {
    if (stone%gridSize !== 0) {
        var neighborStone = stone - 1;
        var position = getPosition(neighborStone);
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
    // console.log("no liberties for " + getMoveCoord(stone));
    return false;
}

function getMoveCoord(move) {
    var letter = coordinateLetters[move%gridSize];
    var number = gridSize-Math.floor(move/gridSize);
    return letter + number;
}
function getPosition(move) {
    return abstractBoard[move%gridSize][Math.floor(move/gridSize)];
}
function setPosition(move, val) {
    abstractBoard[move%gridSize][Math.floor(move/gridSize)] = val;
}

function settleGroups(move, groupsByID, stoneGroupIDs) {
    var newGroup = [];
    newGroup.push(move);
    groupsByID[move] = newGroup;
    stoneGroupIDs[move] = move;
    
    if (move%gridSize !== 0) {
        var neighborStone = move - 1;
        var neighborStoneGroupID = stoneGroupIDs[neighborStone];
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
    var oldGroup, newGroup;
    var oldGroupID, newGroupID;
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
    for(var i = 0; i < oldGroup.length; ++i) {
        newGroup.push(oldGroup[i]);
        stoneGroupIDs[oldGroup[i]] = newGroupID;
    }
    delete groupsByID[oldGroupID];
}

function getEmptyNeighbour(move) {
    if (move%gridSize !== 0) {
        var neighborStone = move - 1;
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
    var neighbourStone = getEmptyNeighbour(move);
    while (neighbourStone !== -1) {
        floodFillWorker(neighbourStone, value);
        neighbourStone = getEmptyNeighbour(move);
    }
}
function resetGoBeforeFlood() {
    for (var i = 0; i < gridSize; i++ ) {
        for (var j = 0; j < gridSize; j++ ) {
            if (abstractBoard[i][j] !== 1 && abstractBoard[i][j] !== 2) {
                abstractBoard[i][j] = 0;                
            }
        }
    }
}
function floodPlayer(player) {
    for (var move = 0; move < gridSize*gridSize; move++) {
        if (getPosition(move) === 3-player) {
            var neighbourStone = getEmptyNeighbour(move);
            while (neighbourStone > -1) {
                floodFillWorker(neighbourStone, player + 2);
                neighbourStone = getEmptyNeighbour(move);
            }
        }
    }
}
function getMovesForValue(value) {
    var result = [];
    for (var j = 0; j < gridSize; j++ ) {
        for (var i = 0; i < gridSize; i++ ) {
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
    var p1Territory = getMovesForValue(3);
    resetGoBeforeFlood();
    floodPlayer(2);
    var p2Territory = getMovesForValue(4);
    resetGoBeforeFlood();
    var i = 0, j = 0;
    while (i < p1Territory.length && j < p2Territory.length) {
        var p1Stone = p1Territory[i], p2Stone = p2Territory[j];
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

function drawTerritories() {
    if (territoryDrawn) {
        drawGrid(boardContext, boardColor, gridSize, true);
        drawGame();
        territoryDrawn = false;
        document.getElementById("scoreBox").innerHTML = "";
    } else {
        getTerritories();
        var p1Territory = goTerritoryByPlayer[1], p2Territory = goTerritoryByPlayer[2];
        for (var i = 0; i < p1Territory.length; i++) {
            drawTerritorySquare(p1Territory[i], 1);
        }
        for (i = 0; i < p2Territory.length; i++) {
            drawTerritorySquare(p2Territory[i], 2);
        }
        territoryDrawn = true;
        
        var p1Score = 0, p2Score = 0;
        for (i = 0; i < gridSize; i++) {
            for (var j = 0; j < gridSize; j++) {
                if (abstractBoard[i][j] === 1) {
                    p2Score += 1;
                } else if (abstractBoard[i][j] === 2) {
                    p1Score += 1;
                }
            }
        }
        document.getElementById("scoreBox").innerHTML = "<b> Black has "+p1Territory.length+" territory and " + p1Score+" stones, total is " +(p1Territory.length+p1Score) +
            "<br> white has "+p2Territory.length+" territory and "+p2Score+" stones, total is " +(p2Territory.length+p2Score+7) +".5</b>"; 
    }
}
function reDrawTerritories() {
    getTerritories();
    var p1Territory = goTerritoryByPlayer[1], p2Territory = goTerritoryByPlayer[2];
    for (var i = 0; i < p1Territory.length; i++) {
        drawTerritorySquare(p1Territory[i], 1);
    }
    for (i = 0; i < p2Territory.length; i++) {
        drawTerritorySquare(p2Territory[i], 2);
    }
    var p1Score = 0, p2Score = 0;
    for (i = 0; i < gridSize; i++) {
        for (var j = 0; j < gridSize; j++) {
            if (abstractBoard[i][j] === 1) {
                p2Score += 1;
            } else if (abstractBoard[i][j] === 2) {
                p1Score += 1;
            }
        }
    }
    document.getElementById("scoreBox").innerHTML = "<b> Black has "+p1Territory.length+" territory and " + p1Score+" stones, total is " +(p1Territory.length+p1Score) +
        "<br> white has "+p2Territory.length+" territory and "+p2Score+" stones, total is " +(p2Territory.length+p2Score+7) +".5</b>";
}

function drawGoCaptures() {
    var pxSize = Math.floor(stepX/2+1);
    if (whiteCaptures > 0) {
        var digits = 1;
        if (whiteCaptures > 9) {
            digits = 2;
        }
        if (whiteCaptures > 99) {
            digits = 3;
        }
        for (var i = 0; i < digits; i++) {
            boardContext.beginPath();
            boardContext.arc(indentWidth + i * stepX * 2 / 3, boardSize + indentHeight + stepY, stepX / 3, 0, Math.PI * 2, true);
            boardContext.fillStyle = 'white';
            boardContext.fill();
            boardContext.stroke();
            boardContext.closePath();
        }
        var digit = 0;
        if (whiteCaptures > 9) {
            digit = Math.floor(whiteCaptures / 10);
            if (whiteCaptures > 99) {
                digit = Math.floor(whiteCaptures / 100);
            } 
        } else {
            digit = whiteCaptures % 10;
        }
        boardContext.beginPath();
        boardContext.font = pxSize+"px bold sans-serif";
        boardContext.fillStyle = 'black';
        boardContext.fillText("" + digit, indentWidth - 4, boardSize + indentHeight + stepY + 4);
        boardContext.stroke();
        boardContext.closePath();
        if (whiteCaptures > 9) {
            digit = whiteCaptures % 10;
            if (whiteCaptures > 99) {
                digit = Math.floor(whiteCaptures/10) % 10;
            }
            boardContext.beginPath();
            // boardContext.font = "14px bold sans-serif";
            boardContext.fillStyle = 'black';
            boardContext.fillText("" + digit, indentWidth + stepX * 2 / 3 - 4, boardSize + indentHeight + stepY + 4);
            boardContext.stroke();
            boardContext.closePath();
            if (whiteCaptures > 99) {
                digit = whiteCaptures % 10;
                boardContext.beginPath();
                // boardContext.font = "14px bold sans-serif";
                boardContext.fillStyle = 'black';
                boardContext.fillText("" + digit, indentWidth + 2* stepX * 2 / 3 - 4, boardSize + indentHeight + stepY + 4);
                boardContext.stroke();
                boardContext.closePath();
            }
        }
    }
    if (blackCaptures > 0) {
        digits = 1;
        if (blackCaptures > 9) {
            digits = 2;
        }
        if (blackCaptures > 99) {
            digits = 3;
        }
        for (var i = 0; i < digits; i++) {
            boardContext.beginPath();
            boardContext.arc(boardSize + indentWidth - i * stepX * 2 / 3, indentHeight - stepY, stepX / 3, 0, Math.PI * 2, true);
            boardContext.fillStyle = 'black';
            boardContext.fill();
            boardContext.stroke();
            boardContext.closePath();
        }
        var digit = blackCaptures % 10;
        boardContext.beginPath();
        boardContext.font = pxSize+"px bold sans-serif";
        boardContext.fillStyle = 'white';
        boardContext.fillText("" + digit, boardSize + indentWidth - 4, indentHeight - stepY + 4);
        boardContext.stroke();
        boardContext.closePath();
        if (blackCaptures > 9) {
            digit = Math.floor(blackCaptures / 10) % 10;
            boardContext.beginPath();
            // boardContext.font = "14px bold sans-serif";
            boardContext.fillStyle = 'white';
            boardContext.fillText("" + digit, boardSize + indentWidth - stepX * 2 / 3 - 4, indentHeight - stepY + 4);
            boardContext.stroke();
            boardContext.closePath();
            if (blackCaptures > 90) {
                digit = Math.floor(blackCaptures / 100);
                boardContext.beginPath();
                // boardContext.font = "14px bold sans-serif";
                boardContext.fillStyle = 'white';
                boardContext.fillText("" + digit, boardSize + indentWidth - 2*stepX * 2 / 3 - 4, indentHeight - stepY + 4);
                boardContext.stroke();
                boardContext.closePath();
            }
        }
    }
}







            function drawGrid(boardContext, boardColor, gridSize, drawAxis) {
                // boardContext.beginPath();
                boardContext.clearRect(0, 0, boardCanvas.width, boardCanvas.height);
                // boardContext.closePath();
              boardContext.save();
                boardContext.beginPath();
                boardContext.rect(indentWidth / 2, indentHeight / 2, boardSize + indentWidth, boardSize + indentHeight);
                boardContext.lineWidth=0.5;
                boardContext.fillStyle=boardColor;
                boardContext.shadowColor = 'Black';
                boardContext.shadowBlur = 5;
                boardContext.shadowOffsetX = radius/4;
                boardContext.shadowOffsetY = radius/4;
                boardContext.fill();     
                // boardContext.closePath();
                boardContext.restore();

                // boardContext.beginPath();
                boardContext.font = "10px sans-serif";
                boardContext.fillStyle='black';
                boardContext.lineWidth=0.5;
                for (var i = 0; i < gridSize; i++) {
                    boardContext.moveTo(indentWidth + i*stepX, indentHeight);
                    boardContext.lineTo(indentWidth + i*stepX, indentHeight + boardSize);
                    if (drawAxis) {
                        boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, indentHeight - 5);
                        boardContext.fillText(coordinateLetters[i], indentWidth + i*stepX - 2, boardSize + indentHeight + 12);
                    }
                }
                for (i = 0; i < gridSize; i++) {
                    boardContext.moveTo(indentWidth, indentHeight + i*stepY);
                    boardContext.lineTo(indentWidth + boardSize, indentHeight + i*stepY);
                    if (drawAxis) {
                        boardContext.fillText("" + (gridSize - i), indentWidth - 15, indentHeight + i*stepX + 3);
                        boardContext.fillText("" + (gridSize - i), boardSize + indentWidth + 6, indentHeight + i*stepX + 3);
                    }
                }
                // boardContext.strokeStyle = "#FFFFFF";
                boardContext.stroke();
                boardContext.closePath();
                if (game < 69) {
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 9*stepX, indentHeight + 9*stepY, stepX / 5, 0, Math.PI*2, true);
                    boardContext.stroke();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 6*stepX, indentHeight + 6*stepY, stepX / 5, 0, Math.PI*2, true);
                    boardContext.stroke();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 6*stepX, indentHeight + 12*stepY, stepX / 5, 0, Math.PI*2, true);
                    boardContext.stroke();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 12*stepX, indentHeight + 6*stepY, stepX / 5, 0, Math.PI*2, true);
                    boardContext.stroke();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 12*stepX, indentHeight + 12*stepY, stepX / 5, 0, Math.PI*2, true);
                    boardContext.stroke();
                    boardContext.closePath();
                } else if (game === 69) {
                    var r = stepX / 8;
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 9*stepX, indentHeight + 9*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 3*stepX, indentHeight + 9*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 9*stepX, indentHeight + 3*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 15*stepX, indentHeight + 9*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 9*stepX, indentHeight + 15*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 3*stepX, indentHeight + 3*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 3*stepX, indentHeight + 15*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 15*stepX, indentHeight + 3*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                    boardContext.beginPath();
                    boardContext.arc(indentWidth + 15*stepX, indentHeight + 15*stepY, r, 0, Math.PI*2, true);
                    boardContext.fill();
                    boardContext.closePath();
                }
            }



function detectPenteCapture(abstractBoard, i, j, myColor) {
    var opponentColor = 1 + (myColor % 2);
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
    var opponentColor = 1 + (myColor % 2);
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
    var opponentColor = 1 + (myColor % 2);
    var poofed = false;
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
    if (rated && (moves.length === 2)) {
        for(i = 7; i < 12; ++i)
            for(var j = 7; j < 12; ++j)
                if (abstractBoard[i][j] === 0) {
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
    if (rated && (moves.length === 2)) {
        for(i = 7; i < 12; ++i) {
            for(var j = 7; j < 12; ++j) {
                if (abstractBoard[i][j] === 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
    }
}
function replayConnect6Game(abstractBoard, movesList, until) {
    resetAbstractBoard(abstractBoard);
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = (((i % 4) === 0) || ((i % 4) === 3)) ? 1 : 2;
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
    if (moves.length === 2) {
        for(i = 7; i < 12; i++) {
            for(var j = 7; j < 12; j++) {
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
    for (var i = 0; i < Math.min(movesList.length, until); i++) {
        var color = 1 + (i%2);
        abstractBoard[movesList[i] % 19][Math.floor(movesList[i] / 19)] = color;
        detectPoof(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
        detectPenteCapture(abstractBoard, movesList[i] % 19, Math.floor(movesList[i] / 19), color);
    }
    if (rated && (moves.length === 2)) {
        for(i = 7; i < 12; ++i) {
            for(var j = 7; j < 12; ++j) {
                if (abstractBoard[i][j] === 0) {
                    abstractBoard[i][j] = -1;
                }
            }
        }
    }
}




