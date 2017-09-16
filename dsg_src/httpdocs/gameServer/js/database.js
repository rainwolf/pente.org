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

moveImages = new Array(whiteImage, blackImage);
moveCaptureImages = new Array(whiteCaptureImage, blackCaptureImage);

var game = 0;

var currentMove = 0;
var maxMove = currentMove;
var startMoves = 0;
var viewingSearchResults = 1;

var statisticsShowing = 1;

coordinateAlphas = new Array("A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T");

var ns4 = (document.layers) ? true : false;
var ie4 = (document.all) ? true : false;
var ns6 = ((navigator.vendor) && (navigator.vendor.indexOf("Netscape6"))) != -1;

var highlightedMove = "";

numCaptures = new Array(0, 0, 0);
capturedAt = new Array(new Array(60), new Array(60), new Array(60));
capturedMoves = new Array(new Array(60), new Array(60), new Array(60));

board = new Array(new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19), new Array(19));
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
  return (game != 5 && game != 6);
}
function gameHasTripleCaptures() {
  return (game == 3 || game == 4 || game == 17 || game == 18);
}

function initializeGame() {

    game = document.data_form.game.value;
    var filter_data = getFilterData();
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

function setGame(moveStr) {

    for (var i = 0; i < 3; i++) {
       numCaptures[i] = 0;
    }
    for (var i = 0; i < 3; i++) {
        for (var j = 0; j < 60; j++) {
            capturedAt[i][j] = 0;
        }
    }
    for (var i = 0; i < 3; i++) {
        for (var j = 0; j < 60; j++) {
            capturedMoves[i][j] = 0;
        }
    }

    for (var i = 1; i < 3; i++) {
        for (var j = 0; j < 19; j++) {

            var i1 = p[i] + "c" + j;

            document[i1].src = dummyImage.src;
        }
    }

    for (var i = 0; i < 19; i++) {
        for (var j = 0; j < 19; j++) {

            if (board[i][j] == 1 ||
                board[i][j] == 2) {
                clearMove(getStrMove(j * 19 + i));
            }
            board[i][j] = 0;
        }
    }
    for (var i = 0; i < 361; i++) {
        moves[i] = 0;
    }

    currentMove = 0;
    var startMove = -1, endMove = 0;

    var stillMovesLeft = true;

    while (stillMovesLeft) {

        if (currentMove != 0) {
            startMove = moveStr.indexOf(",", startMove + 1);
        }

        endMove = moveStr.indexOf(",", startMove + 1);
        if (endMove == (moveStr.length - 1)) {
            stillMovesLeft = false;
        }

        var move = moveStr.substring(startMove + 1, endMove);

        addMove(move);
    }

    maxMove = currentMove;
}

function getCurrentMoveStr() {

    var moveStr = "";

    for (var i = 0; i < currentMove; i++) {
        moveStr += getStrMove(moves[i]) + ",";
    }

    return moveStr;
}

function highlightMove(move) {

    if (document[move].src == statisticImage.src) {

        var currentPlayer = currentMove % 2;
        document[move].src = moveImages[currentPlayer].src;

        highlightedMove = move;
    }
}

function unHighlightMove(move) {

    if (highlightedMove == move &&
        (document[move].src == moveImages[0].src ||
         document[move].src == moveImages[1].src)) {

        document[move].src = statisticImage.src;
        highlightedMove = "";
    }
}

function highlightStat(stat) {

    // get the image differently in ns and ie
    var image = (ns4) ? document['statsDiv'].document[stat] : document[stat];

    image.src = statisticHighlightImage.src;
}
function unHighlightStat(stat) {

    // get the image differently in ns and ie
    var image = (ns4) ? document['statsDiv'].document[stat] : document[stat];

    image.src = statisticBlankImage.src;
}

function addMoveInternal(intMove) {

    // put move in move list
    moves[currentMove] = intMove;

    // put move on internal board
    var x = intMove % 19;
    var y = parseInt(intMove / 19);
    var currentPlayer = currentMove % 2 + 1;
    board[x][y] = currentPlayer;
}

function addMove(move) {

    if (highlightedMove == move ||
        (document[move].src != moveImages[0].src &&
         document[move].src != moveImages[1].src))
    {

        hideStatistics();

        var intMove = getIntMove(move);
        addMoveInternal(intMove);

        // put move on image board
        var currentPlayer = currentMove++ % 2;
        document[move].src = moveImages[currentPlayer].src;

        maxMove = currentMove;

        highlightedMove = "";

        if (gameHasCaptures()) {
            removeCaptures(intMove, false);
        }
        
        if (shouldShowSearchStats()) {
            showStatistics();
        }
    }
}


function printBoard() {

    var boardStr = "";
    for (var i = 0; i < 19; i++) {
        for (var j = 0; j < 19; j++) {
            boardStr += board[j][i] + " ";
        }
        boardStr += "\n";
    }

    alert(boardStr);
}

dx = new Array(-1, 0, 1, -1, 1, -1, 0, 1);
dy = new Array(-1, -1, -1, 0, 0, 1, 1, 1);

p = new Array("", "w", "b");

function removeCaptures(intMove, internal) {

   var currentPlayer = (currentMove - 1) % 2 + 1;
   var otherPlayer = 3 - currentPlayer;
   var x = intMove % 19;
   var y = parseInt(intMove / 19);

   for (var d = 0; d < 8; d++) {
       var c1 = x + dx[d];
       var c2 = y + dy[d];
       var c3 = c1 + dx[d];
       var c4 = c2 + dy[d];
       var c5 = c3 + dx[d];
       var c6 = c4 + dy[d];
       var c7 = c5 + dx[d];
       var c8 = c6 + dy[d];

       if (c5 >= 0 && c5 < 19 && c6 >= 0 && c6 < 19) {

           if (board[c1][c2] == otherPlayer &&
               board[c3][c4] == otherPlayer &&
               board[c5][c6] == currentPlayer) {

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
                   var i1 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i1].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;
               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   var i2 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
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
           
           if (board[c1][c2] == otherPlayer &&
               board[c3][c4] == otherPlayer &&
               board[c5][c6] == otherPlayer &&
               board[c7][c8] == currentPlayer) {
           
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
                   var i1 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i1].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;
               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   var i2 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
                   document[i2].src = moveCaptureImages[otherPlayer - 1].src;
               }
               numCaptures[currentPlayer]++;
               // don't display captures if they go over the limit
               if (numCaptures[currentPlayer] < 19) {
                   var i3 = p[otherPlayer] + "c" + numCaptures[currentPlayer] + "";
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

    var currentPlayer = currentMove  % 2 + 1;
    var otherPlayer = 3 - currentPlayer;

    while (capturedAt[currentPlayer][numCaptures[currentPlayer] - 1] == currentMove) {

        var back = capturedMoves[currentPlayer][(numCaptures[currentPlayer] - 1)];

        var x = back % 19;
        var y = parseInt(back / 19);
        board[x][y] = otherPlayer;

        document[getStrMove(back)].src = moveImages[otherPlayer - 1].src;

        numCaptures[currentPlayer]--;
        
        // don't un-display captures if they go over the limit
        if (numCaptures[currentPlayer] < 19) {
            var i1 = p[otherPlayer] + "c" + numCaptures[currentPlayer];
            document[i1].src = dummyImage.src;
        }
    }
}

function getIntMove(move) {

    var alpha = move.charAt(0);
    var x = 0;
    var y = 0;

    for (var i = 0; i < coordinateAlphas.length; i++) {
        if (alpha == coordinateAlphas[i]) {
            x = i;
            break;
        }
    }

    y = 19 - move.substring(1);

    return y * 19 + x;
}

function getStrMove(move) {

    var x = move % 19;
    var y = 19 - parseInt(move / 19);

    return coordinateAlphas[x] + y;
}

function backMove() {

    if (currentMove != 1) {

        hideStatistics();

        var intMove = moves[--currentMove];
        var x = intMove % 19;
        var y = parseInt(intMove / 19);
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

        var intMove = moves[currentMove];

        var currentPlayer = currentMove++ % 2;
        document[getStrMove(intMove)].src = moveImages[currentPlayer].src;

        var x = intMove % 19;
        var y = parseInt(intMove / 19);
        board[x][y] = currentPlayer + 1;

        if (gameHasCaptures()) {
            removeCaptures(intMove, false);
        }

        if (shouldShowSearchStats()) {
            showStatistics();
        }
    }
}

function firstMove() {

    for (var i = currentMove; i > 1; i--) {
        backMove();
    }
}

function lastMove() {

    for (var i = currentMove; i < maxMove; i++) {
        forwardMove();
    }
}

function hideStatistics() {

    if (statisticsShowing) {

        var startMove, endMove;

        for (var i = 0; i < numResults * 3; i++) {
 
            if (i == 0) {
                startMove = -1;
            }
            else {
                startMove = results.indexOf(",", startMove + 1);
            }

            endMove = results.indexOf(",", startMove + 1);
            if (endMove == -1) {
                endMove = results.length;
            }

            if (i % 3 == 0) {
                var move = results.substring(startMove + 1, endMove);
                clearMove(move);
            }
        }

        hide('statsDiv');
        hide('gamesDiv');

        statisticsShowing = 0;
    }
}

function shouldShowSearchStats() {

    var at = true;

    if (!statisticsShowing && 
        viewingSearchResults &&
        currentMove == startMoves) {

        var startMove = -1, endMove = 0;
        var stillMovesLeft = true;
        var i = 0;
        var moveStr = document.data_form.moves.value;

        while (stillMovesLeft) {

            if (i++ != 0) {
                startMove = moveStr.indexOf(",", startMove + 1);
            }

            endMove = moveStr.indexOf(",", startMove + 1);
            if (endMove == (moveStr.length - 1)) {
                stillMovesLeft = false;
            }

            var move = moveStr.substring(startMove + 1, endMove);

            if (move != getStrMove(moves[i - 1])) {
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

    var startMove, endMove;

    for (var i = 0; i < numResults * 3; i++) {
 
        if (i == 0) {
            startMove = -1;
        }
        else {
            startMove = results.indexOf(",", startMove + 1);
        }

        endMove = results.indexOf(",", startMove + 1);
        if (endMove == -1) {
            endMove = results.length;
        }

        if (i % 3 == 0) {
            var move = results.substring(startMove + 1, endMove);
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

    if (move == 'G7' || move == 'G13' || move == 'N7' || move == 'N13') {
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

    var filter_data = getFilterData();

    filter_data.startGameNum.value = 0;
    filter_data.endGameNum = 100;

    submitForm();
}
function search2() {
    var filter_data = getFilterData();


    filter_data.startGameNum.value = 0;
    filter_data.endGameNum = 100;

    submitForm2();
}

function sortResults(sortOrder) {

    document.data_form.results_order.value = sortOrder;
    submitForm();
}

function changeNumMatchedGames() {

    var filter_data = getFilterData();
    newNumMatchedGames = 100;

    if (newNumMatchedGames != currentNumMatchedGames) {
        submitForm();
    }
}

function downloadGames(num) {

    var filter_data = getFilterData();

    // store the existing parameters so they can be restored later    
    var old_response_format = document.data_form.response_format.value;
    var old_submit_form_action = document.submit_form.action;    
    var old_start_game_num = filter_data.startGameNum.value;

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

    var filter_data = getFilterData();

    currentMove = drawUntilMove;
    var moves = "moves=" + escape(getCurrentMoveStr());
    var response = "response_format=" + escape(document.data_form.response_format.value);
    var sortOrder = "results_order=" + escape(document.data_form.results_order.value);
    var responseParams = "zippedPartNumParam=" + escape(document.data_form.zippedPartNumParam.value);
    responseParams = "response_params=" + escape(responseParams);

    var startGameNum = "start_game_num=" + filter_data.startGameNum.value;
    var endGameNum = 100;
    endGameNum += parseInt(filter_data.startGameNum.value);
    endGameNum = "end_game_num=" + endGameNum;

    var game = "game=" + escape(gameStr);
    var site = "site=" + escape("All Sites");
    var event = "event=" + escape("All Events");
    var round = "round=" + escape("All Rounds");
    var section = "section=" + escape("All Sections");

    var player1Name = "player_1_name=" + escape(document.filter_options_data.player_1_name.value);
    var player2Name = "player_2_name=" + escape(document.filter_options_data.player_2_name.value);

    var select = document.filter_options_data.selectWinner;
    var winner = "winner=0";

    var filterData = startGameNum + "&" + endGameNum + "&" + player1Name + "&" + player2Name + "&" +
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

    var filter_data = getFilterData();

    var moves = "moves=" + escape(getCurrentMoveStr());
    var response = "response_format=" + escape(document.data_form.response_format.value);
    var sortOrder = "results_order=" + escape(document.data_form.results_order.value);
    var responseParams = "zippedPartNumParam=" + escape(document.data_form.zippedPartNumParam.value);
    responseParams = "response_params=" + escape(responseParams);

    var startGameNum = "start_game_num=" + filter_data.startGameNum.value;
    var endGameNum = 100;
    endGameNum += parseInt(filter_data.startGameNum.value);
    endGameNum = "end_game_num=" + endGameNum;

    var game = "game=" + escape(getSelectValue(GAME_NAME));
    var site = "site=" + escape(getSelectValue(SITE_NAME));
    var event = "event=" + escape(getSelectValue(EVENT_NAME));
    var round = "round=" + escape(getSelectValue(ROUND_NAME));
    var section = "section=" + escape(getSelectValue(SECTION_NAME));

    var player1Name = "player_1_name=" + escape(document.filter_options_data.player_1_name.value);
    var player2Name = "player_2_name=" + escape(document.filter_options_data.player_2_name.value);

    var select = document.filter_options_data.selectWinner;
    var winner = "winner=" + select[select.selectedIndex].value;

    var afterDateStr = document.filter_options_data.after_date.value;
    var beforeDateStr = document.filter_options_data.before_date.value;
    var afterDate;
    var beforeDate;
    var afterDateValid = false;
    var beforeDateValid = false;

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

    if (afterDate != null && afterDateValid) {
        afterDate.setDate(afterDate.getDate() + 1);
    }

    if (afterDate != null && beforeDate != null && afterDate >= beforeDate) {
        alert("After date must be before the before date");
        return false;
    }

    var filterData = startGameNum + "&" + endGameNum + "&" + player1Name + "&" + player2Name + "&" +
                     game + "&" + site + "&" + event + "&" + round + "&" + section + "&" + winner;

    if (afterDate != null) {
        filterData += "&" + "after_date=" + escape(getDateStr(afterDate));
    }
    if (beforeDate != null) {
        filterData += "&" + "before_date=" + escape(getDateStr(beforeDate));
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
    if (dateStr.length == 0) {
        return true;
    }
    else if (dateStr.length < 10) {
        return false;
    }
    else {
        var date = getDate(dateStr);
        return date != null;
    }
}

function getDate(dateStr) {

    var date = new Date(dateStr.substr(6),
                        dateStr.substr(0, 2) - 1,
                        dateStr.substr(3, 2));

    if (date == "NaN" || date == null) {
        return null;
    }
    var year = date.getYear();
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

   if (date == null || date == "NaN" || date == "undefined") {
       return "";
   }
 
   var year = date.getYear();

   if (year < 1899) {
       year += 1900;
   }

   return (date.getMonth() + 1) + "/" +
          date.getDate() + "/" + 
          year;
}

function nextGames() {

    var filter_data = getFilterData();

    var startNumGames = parseInt(filter_data.startGameNum.value);
    var numGames = 100; // no longer selectable
    startNumGames += numGames;
    filter_data.startGameNum.value = startNumGames;
    submitForm();
}

function prevGames() {

    var filter_data = getFilterData();

    var startNumGames = parseInt(filter_data.startGameNum.value);
    var numGames = 100;
    startNumGames -= numGames;
    if (startNumGames < 0) {
        startNumGames = 0;
    }
    filter_data.startGameNum.value = startNumGames;
    submitForm();
}

// -->