<?php
// Form handler that processes POST data and redirects

session_start();

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    // Simulate form processing
    $redirectTo = $_POST['redirect_to'] ?? 'index.php';
    
    // Log form submission
    if (!isset($_SESSION['form_submissions'])) {
        $_SESSION['form_submissions'] = 0;
    }
    $_SESSION['form_submissions']++;
    
    // Add processing delay
    usleep(200000); // 0.2 second delay
    
    // Redirect after processing
    header("HTTP/1.1 302 Found");
    header("Location: $redirectTo?form_processed=1&submission=" . $_SESSION['form_submissions']);
    exit();
} else {
    // GET request, redirect to home
    header("HTTP/1.1 302 Found");
    header("Location: index.php");
    exit();
}
?>
