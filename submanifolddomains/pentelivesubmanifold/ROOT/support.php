<?php header('Content-type: text/html; charset=utf-8'); ?>
<?php
session_start();
include("details.php");
function form_error($field) {
    if(!empty($_SESSION['form_errors'][$field])) {
        echo $_SESSION['form_errors'][$field];
    }
}
?>
<!DOCTYPE html PUBLIC "-//WAPFORUM//DTD XHTML Mobile 1.2//EN" "http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
<title><?php echo $businessname; ?> - Support</title>
<meta http-equiv="Cache-Control" content="max-age=600" />
<meta name="description" content="" />
<meta name="keywords" content="" />
<meta name="robots" content="noindex, nofollow, all" />
<meta name="HandheldFriendly" content="true" />
<meta name="MobileOptimized" content="width" />
<meta name="viewport" content="width=device-width, minimum-scale=1.0, maximum-scale=2.0, user-scalable=no" />
<link href="css/styles.css" rel="stylesheet" type="text/css" />
</head>
<body>

<!-- Start Header -->
<?php include("header.php"); ?>	
<!-- End Header -->

<div style="margin: 0pt auto; max-width: 600px;">

<div id="wrapper">

<!-- Start Nav Button -->
<div class="topnav">
<p><a href="./">Home&nbsp; |&nbsp; Support</a></p>
</div>	
<!-- End Nav Button -->

<!-- Start Content -->
<div id="contentinner">
<div class="content">
    <p></p>
    <p>Need help? Found a bug? Questions, comments or just want to say hi?<br>Leave us a message!</p>
    <br><br>
    <p>*Required field
    <div class="form">
    <form id="form1" method="post" action="form-process.php" onsubmit="return checkSelection('form1');">
    <input type="hidden" name="required" value="Full_Name,Email_From,Comments" />
    <input type="hidden" name="thank_you_url" value="./thank-you.php" />
    <input type="hidden" name="error_url" value="./contact-us.php" />
    <input type="hidden" name="subject" value="Contact Request from Website" />

    <!-- Start Form Fields -->
    <label>* Full Name: <?php form_error('Full_Name');?></label>
    <input name="Full_Name" type="text" class="input" />
    <label>* Email: <?php form_error('Email_From');?></label>
    <input name="Email_From" type="text" class="input" />
    <label>* Message: <?php form_error('Comments');?></label>
    <textarea name="Comments" cols="" rows="" class="textarea"></textarea>
    <!-- End Form Fields -->
    
    <input type="submit" class="button" value="Submit" />   
    </div>
    <p>We respect your privacy.</p>
</div>
</div>	
<!-- End Content -->

</div><!-- wrapper -->

</div><!-- margin -->

<!-- Start Footer -->
<?php include("footer.php"); ?>					
<!-- End Footer -->

<!-- Start Form Popup -->
<script type="text/javascript">
<!--
function checkSelection(whichform) {
    if(document.forms[whichform].required.value) {
        var required = document.forms[whichform].required.value.split(','), errors = false;
        for(var i = 0; i < required.length; i++) {
            if(document.forms[whichform][required[i]].value == "") {
                errors = true;
            }
        }
        if (errors) {
            alert ('Whoops! You must fill in all required fields before you can continue.');
            return false;
        }
        else {
            return true; 
        }
    }
}
        
//-->
</script>
<!-- End Form Popup -->

</body>
</html>	
<?php $_SESSION['form_errors'] = array();//reset form errors so they don't appear next time?>
