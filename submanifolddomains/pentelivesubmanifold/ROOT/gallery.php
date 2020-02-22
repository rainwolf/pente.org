<?php header('Content-type: text/html; charset=utf-8'); ?>
<?php include("details.php"); ?>
<!DOCTYPE html PUBLIC "-//WAPFORUM//DTD XHTML Mobile 1.2//EN" "http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
<title><?php echo $businessname; ?> - Gallery</title>
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
<?php include("header.php"); ?>	
<!-- End Header -->

<div style="margin: 0pt auto; max-width: 600px;">

<div id="wrapper">

<!-- Start Nav Button -->
<div class="topnav">
<p><a href="./">Home&nbsp; |&nbsp; Gallery</a></p>
</div>	
<!-- End Nav Button -->

<!-- Start Content -->
<div id="contentinner">
<div class="content">
    <p></p>
    <div class="photos">
    <p class="centered"><span><img src="images/image.gif" width="208" height="160" alt="" /></span></p>
    <p class="centered">Image with white border and caption</p>
    <p></p>
    <p class="centered"><span><img src="images/image.gif" width="208" height="160" alt="" /></span></p>
    <p class="centered">Image with white border and caption</p>
    <p></p>
    <p class="centered"><span><img src="images/image.gif" width="208" height="160" alt="" /></span></p>
    <p class="centered">Image with white border and caption</p>
    </div>
</div>
</div>	
<!-- End Content -->

</div><!-- wrapper -->

</div><!-- margin -->
	
<!-- Start Footer -->
<?php include("footer.php"); ?>					
<!-- End Footer -->

</body>
</html>
