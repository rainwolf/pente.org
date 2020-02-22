<?php

// Start of Customizations ******************************************

// 1. Enter the business name 
$businessname = "submanifold.be";

// ******************************************************************

// 2. Enter the filename of the logo image 
$logoimage = "logo.gif";

// ******************************************************************

// 3. Enter the width and height of the logo image 
$logowidth = "300px";
$logoheight = "146px";

// ******************************************************************

// 4. Enter the full street address of the business
$address1 = "";
$address2 = "";

// ******************************************************************

// 5. Enter the main phone number of the business
$workphone = "";

// ******************************************************************

// 6. Enter iTunes link
$itunes = "https://itunes.apple.com/us/app/pente-live/id595426592?ls=1&mt=8";

// ******************************************************************


// 6. To include a slogan on the homepage, set SLOGAN to true, and type in the slogan
define('SLOGAN', false);// make true to activate the slogan text
define('SLOGAN_TEXT_LINE1', 'Scuba accessories customized to suit every taste!');
define('SLOGAN_TEXT_LINE2', '');

// ******************************************************************

// 7. To include a Click to Call button on homepage, set CLICKTOCALL to true and fill in the button text and link
define('CLICKTOCALL', false);// make true to activate Click to Call button
define('BUTTON_TEXT', 'SHOP Customizables');
define('BUTTON_LINK', 'url:http://www.divetag.asia');// Phone numbers must start with tel:

// ******************************************************************

// 8. To use Google Analytics set ANALYTICS to true, and fill in an analytics id
define('ANALYTICS', false);// make true to activate analytics
define('ANALYTICS_ID', 'UA-20529582-1');

// ******************************************************************

// 9. To include a social icon set to true and fill in the social url

define('CALL', false);// make true to activate click to call icon
define('CALL_LINK', '800-800-8000');

define('FACEBOOK', false);// make true to activate facebook icon
define('FACEBOOK_LINK', 'http://www.facebook.com/');

define('MAP', false);// make true to activate map icon
define('MAP_LINK', './find-us.php');

define('LINKEDIN', false);// make true to activate linkedin icon
define('LINKEDIN_LINK', 'http://www.linkedin.com');

define('TWITTER', false);// make true to activate twitter icon
define('TWITTER_LINK', 'http://www.twitter.com/');

// ******************************************************************

// 10. To include a link to desktop site, set DESKTOP to true, and fill in the desktop site URL
define('DESKTOP', false);// make true to activate link to desktop
define('DESKTOP_LINK', 'http://www.website.com/#r=off');

// End of Customizations ********************************************************************

header('Content-type: text/html; charset=utf-8' ); 
header("Cache-Control: max-age=600" );

date_default_timezone_set ('America/New_York');

if(!class_exists('Snippets')) {
    require_once dirname(__FILE__).'/snippets.class.php';
}
?>