<?php header('Content-type: text/html; charset=utf-8'); ?>
<?php include("details.php"); ?>
<!DOCTYPE html PUBLIC "-//WAPFORUM//DTD XHTML Mobile 1.2//EN" "http://www.openmobilealliance.org/tech/DTD/xhtml-mobile12.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-type" content="text/html;charset=UTF-8" />
<title><?php echo $businessname; ?> - Features</title>
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
<p><a href="./">Home&nbsp; |&nbsp; Features</a></p>
</div>	
<!-- End Nav Button -->

<!-- Start Content -->
<div id="contentinner">
<div class="content">
    <p>Pente Live lets you play turn-based games at your beloved pente.org. No longer are you bound your computer to play pente versus other players. This app supports *all* games available at <a href="http://www.pente.org">pente.org</a>.</p>
		<p>Features:
			<ul>
				<li>Supports Pente, Connect6, Gomoku, Keryo-Pente, D-Pente, G-Pente, Poof-Pente, Boat-Pente. Turn-based games only.</li>
				<li>Tap and hold to zoom the board.</li>
				<li>Play rated or unrated.</li>
				<li>Send invitations from your iPhone.</li>
				<li>Accept or decline invitations from your iPhone.</li>
				<li>Slide to cancel sent invitations.</li>
				<li>Slide to resign games.</li>
			</ul>
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