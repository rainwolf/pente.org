// <!--

var GAME_NAME = "game";
var SITE_NAME = "site";
var EVENT_NAME = "event";
var ROUND_NAME = "round";
var SECTION_NAME = "section";
var FORM_NAME = "";

function getSelectObject(selectName) {
   var selectObject = eval("document." + FORM_NAME + "." + selectName + "Select");
   return selectObject;
}

function getSelectValue(selectName) {

   var selectObject = getSelectObject(selectName);
   return selectObject[selectObject.selectedIndex].value;
}

function selectOption(selectName, selectValue) {

   var select = getSelectObject(selectName);

   for (var i = 0; i < select.options.length; i++) {
      if (select.options[i].value == selectValue) {
         select.options[i].selected = true;
      }
   }
}

function updateSelect(selectValues, selectName) {

   var select = getSelectObject(selectName);

   // null out old values
   var l = select.options.length;
   for (var i = 0; i < l; i++) {
      select.options[0] = null;
   }

   // put in new values
   for (var i = 0; i < selectValues.length; i++) {
      select.options[i] = new Option(selectValues[i], selectValues[i]);
   }

   // select the first option
   select.options[0].selected = true;
}

function initSelects(formName, initGame, initSite, initEvent, initRound, initSection) {
   FORM_NAME = formName;
   updateSelect(games, GAME_NAME);
   gameSelectChange();

   selectOption(GAME_NAME, initGame);
   gameSelectChange();
   selectOption(SITE_NAME, initSite);
   siteSelectChange();
   selectOption(EVENT_NAME, initEvent);
   eventSelectChange();
   selectOption(ROUND_NAME, initRound);
   roundSelectChange();
   selectOption(SECTION_NAME, initSection);
}

function gameSelectChange() {
   var gameSelectObject = getSelectObject(GAME_NAME);

   updateSelect(sites[gameSelectObject.selectedIndex], SITE_NAME);
   siteSelectChange();
}

function siteSelectChange() {

   var gameSelectObject = getSelectObject(GAME_NAME);
   var siteSelectObject = getSelectObject(SITE_NAME);

   updateSelect(events[gameSelectObject.selectedIndex][siteSelectObject.selectedIndex], EVENT_NAME);
   eventSelectChange();
}

function eventSelectChange() {

   var gameSelectObject = getSelectObject(GAME_NAME);
   var siteSelectObject = getSelectObject(SITE_NAME);
   var eventSelectObject = getSelectObject(EVENT_NAME);

   updateSelect(rounds[gameSelectObject.selectedIndex][siteSelectObject.selectedIndex][eventSelectObject.selectedIndex], ROUND_NAME);
   roundSelectChange();
}

function roundSelectChange() {

   var gameSelectObject = getSelectObject(GAME_NAME);
   var siteSelectObject = getSelectObject(SITE_NAME);
   var eventSelectObject = getSelectObject(EVENT_NAME);
   var roundSelectObject = getSelectObject(ROUND_NAME);

   updateSelect(sections[gameSelectObject.selectedIndex][siteSelectObject.selectedIndex][eventSelectObject.selectedIndex][roundSelectObject.selectedIndex], SECTION_NAME);
}

function sectionSelectChange() {
}

// -->