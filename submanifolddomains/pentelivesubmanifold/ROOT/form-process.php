<?php include("details.php"); ?>
<?php
session_start();
define('EMAIL_TO', 'pentelive@submanifold.be'); // Who the email is sent to
define('EMAIL_FROM', 'pentelive@submanifold.be'); // Who the email is from, if the "Email_From" field is not used in the form
if(!empty($_POST)) {
    $required = array();
    $errors = array();
    if(!empty($_POST['required'])) {
        $required = explode(',', $_POST['required']);
        unset($_POST['required']);
    }
    if(!empty($_POST['thank_you_url'])) {
        $thank_you_url = $_POST['thank_you_url'];
        unset($_POST['thank_you_url']);
    }
    if(!empty($_POST['error_url'])) {
        $error_url = $_POST['error_url'];
        unset($_POST['error_url']);
    }
    if(!empty($_POST['subject'])) {
        $subject = $_POST['subject'];
        unset($_POST['subject']);
    }
    else {
        $subject = 'Contact requested on website';
    }
    $emailfrom = !empty($_POST['Email_From']) ? $_POST['Email_From'] : EMAIL_FROM;
    if(!empty($_POST['Full_Name'])) {
        $emailfrom = stripslashes($_POST['Full_Name']).' <'.$emailfrom.'>';
    }
    foreach($required as $r) {
        if(empty($_POST[$r])) {
            $errors[$r] = '<span class="formerror">Required.</span>';
        }
    }
    $_SESSION['form_errors'] = $errors;
    if(empty($thank_you_url) && !empty($_SERVER['HTTP_REFERER'])) {
        $thank_you_url = $_SERVER['HTTP_REFERER'];
    }
    elseif(empty($thank_you_url)) {
        $thank_you_url = '/';
    }
    if(empty($error_url) && !empty($_SERVER['HTTP_REFERER'])) {
        $error_url = $_SERVER['HTTP_REFERER'];
    }
    elseif(empty($error_url)) {
        $error_url = '/';
    }
    if(empty($errors)) {
        $message = '';
        foreach($_POST as $k=>$v) {
            $k = str_replace('_', ' ', $k);
            $message .= "$k: $v\n";
        }
        if (function_exists('get_magic_quotes_gpc') && get_magic_quotes_gpc()) {
            $message = stripslashes($message);
        }
        mail(EMAIL_TO, $subject, $message, "From: $emailfrom\r\n");
        header('Location: '.$thank_you_url);
    }
    else {
        header('Location: '.$error_url);
    }
}
?>