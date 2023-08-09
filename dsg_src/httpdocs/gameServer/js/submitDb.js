// <!--

function submitToDatabase(game, site, event, round, section) {

   // old function didn't specify game, so shift everything
   if (!section) {
      section = round;
      round = event;
      event = site;
      site = game;
      game = 'Pente';
   }

   var moves = "moves=K10,";
   var response = "response_format=" + escape("org.pente.gameDatabase.SimpleHtmlGameStorerSearchResponseFormat");
   var sortOrder = "results_order=1";
   var responseParams = "zippedPartNumParam=1";
   responseParams = "response_params=" + escape(responseParams);

   var startGameNum = "start_game_num=0";
   var endGameNum = "end_game_num=100";

   var game = "game=" + escape(game);
   var site = "site=" + escape(site);
   var event = "event=" + escape(event);
   var round = "round=" + escape(round);
   var section = "section=" + escape(section);

   var filterData = startGameNum + "&" + endGameNum + "&" +
      game + "&" + site + "&" + event + "&" + round + "&" + section;

   filterData = "filter_data=" + escape(filterData);

   document.submit_form.format_data.value = moves + "&" +
      response + "&" +
      responseParams + "&" +
      sortOrder + "&" +
      filterData;

   document.submit_form.submit();
}

// -->