<?php
class Snippets {
    function google_analytics() {
        if(ANALYTICS === true) {?>
<script type="text/javascript">
var gaJsHost = (("https:" == document.location.protocol) ? "https://ssl." : "http://www.");
document.write(unescape("%3Cscript src='" + gaJsHost + "google-analytics.com/ga.js' type='text/javascript'%3E%3C/script%3E"));
</script>
<script type="text/javascript">
try{
var pageTracker = _gat._getTracker("<?php echo ANALYTICS_ID; ?>");
pageTracker._trackPageview();
} catch(err) {}
</script>
<?php }
    }
    function desktop() {
        if(DESKTOP === true) {?>
<p class="bottomlinks"><span>Mobile</span>  |  <span><a href="<?php echo DESKTOP_LINK; ?>">Desktop</a></span></p>
<?php }
    }
    function icon_call() {
        if(CALL === true) {?>
<a href="tel:<?php echo CALL_LINK; ?>" rel="nofollow"><img src="images/icon_phone.gif" width="38" height="38" alt="Click to Call" /></a>
<?php }
    }
    function icon_facebook() {
        if(FACEBOOK === true) {?>
<a href="<?php echo FACEBOOK_LINK; ?>" rel="nofollow"><img src="images/icon_facebook.gif" width="38" height="38" alt="Facebook" /></a>
<?php }
    }
    function icon_map() {
        if(MAP === true) {?>
<a href="<?php echo MAP_LINK; ?>" rel="nofollow"><img src="images/icon_map.gif" width="38" height="38" alt="View a Map" /></a>
<?php }
    }
    function icon_linkedin() {
        if(LINKEDIN === true) {?>
<a href="<?php echo LINKEDIN_LINK; ?>" rel="nofollow"><img src="images/icon_linkedin.gif" width="38" height="38" alt="Linked In" /></a>
<?php }
    }
    function icon_twitter() {
        if(TWITTER === true) {?>
<a href="<?php echo TWITTER_LINK; ?>" rel="nofollow"><img src="images/icon_twitter.gif" width="38" height="38" alt="Twitter" /></a>
<?php }
    }
    function slogan() {
        if(SLOGAN === true) {?>
<p><?php echo SLOGAN_TEXT_LINE1; ?><br /><?php echo SLOGAN_TEXT_LINE2; ?></p>
<?php }
    }
    function click_to_call() {
        if(CLICKTOCALL === true) {?>
<!-- Start Call Button -->
<div class="buttonclick"><a href="<?php echo BUTTON_LINK; ?>"><?php echo BUTTON_TEXT; ?></a></div>
<!-- End Call Button -->
<?php
        }
    }
}

$snippets = new Snippets;
?>