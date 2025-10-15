<?php
// External redirect test - redirects to external site then back

$step = $_GET['step'] ?? '1';

if ($step === '1') {
    // First step: show warning page
    ?>
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>External Redirect Test</title>
        <style>
            body {
                font-family: Arial, sans-serif;
                max-width: 600px;
                margin: 50px auto;
                padding: 20px;
                background-color: #fff3cd;
                text-align: center;
            }
            .container {
                background: white;
                padding: 30px;
                border-radius: 10px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            }
            .button {
                display: inline-block;
                padding: 12px 24px;
                margin: 10px;
                background-color: #007bff;
                color: white;
                text-decoration: none;
                border-radius: 5px;
                border: none;
                cursor: pointer;
                font-size: 16px;
            }
            .button.warning {
                background-color: #dc3545;
            }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>⚠️ External Redirect Test</h1>
            <p>This will redirect you to an external site (httpbin.org) and then back.</p>
            <p>This tests how external redirects affect the back button behavior.</p>
            
            <a href="external-redirect.php?step=2" class="button warning">🌐 Continue to External Site</a>
            <a href="index.php" class="button">🏠 Cancel - Back to Home</a>
            
            <div style="margin-top: 20px; font-size: 14px; color: #666;">
                <strong>Test Instructions:</strong><br>
                1. Click "Continue to External Site"<br>
                2. You'll be redirected to httpbin.org<br>
                3. Use the back button to return<br>
                4. Check if back button works correctly
            </div>
        </div>
    </body>
    </html>
    <?php
} else if ($step === '2') {
    // Second step: redirect to external site
    header("HTTP/1.1 302 Found");
    header("Location: https://httpbin.org/html");
    exit();
} else {
    // Default: back to home
    header("HTTP/1.1 302 Found");
    header("Location: index.php");
    exit();
}
?>
