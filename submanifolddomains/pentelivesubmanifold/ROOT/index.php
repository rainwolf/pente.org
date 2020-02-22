<?php header('Content-type: text/html; charset=utf-8'); ?>
<?php include("details.php"); ?>
<!DOCTYPE html PUBLIC "-//WAPFORUM//DTD XHTML Mobile 1.2//EN" "http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
<title><?php echo $businessname; ?></title>
<meta http-equiv="Cache-Control" content="max-age=600" />
<meta name="description" content="" />
<meta name="keywords" content="" />
<meta name="robots" content="index, follow, all" />
<meta name="HandheldFriendly" content="true" />
<meta name="MobileOptimized" content="width" />
<meta name="viewport" content="width=device-width, minimum-scale=1.0, maximum-scale=2.0, user-scalable=no" />
<link href="css/styles.css" rel="stylesheet" type="text/css" />
</head>
<body>

<!-- Start Header -->
<?php include("header-home.php"); ?>	
<!-- End Header -->

<div style="margin: 0pt auto; max-width: 600px;">

<div id="wrapper">

<?php $snippets->click_to_call(); ?>

<!-- Start Content -->
<div id="content">
    <div id="nav">
    <ul>
    <li class="top"><a href="features.php"><span>Features</span></a></li>
    <li><a href="screenshots.php"><span>Screenshots</span></a></li>
    <li><a href="support.php"><span>Support</span></a></li>
    </ul>
    </div>    
</div>
<!-- End Content -->

</div><!-- wrapper -->

</div><!-- margin -->

<!-- Start Footer -->
<?php include("footer-home.php"); ?>					
<!-- End Footer -->

</body>
</html>