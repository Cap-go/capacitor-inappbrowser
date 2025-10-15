<?php
session_start();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Deep Link Test - InAppBrowser</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f3e5f5;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #9c27b0;
            text-align: center;
            margin-bottom: 30px;
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
            transition: background-color 0.3s;
        }
        .button:hover {
            background-color: #0056b3;
        }
        .button.home {
            background-color: #6c757d;
        }
        .button.home:hover {
            background-color: #545b62;
        }
        .navigation-info {
            background-color: #e1bee7;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
            border: 1px solid #ce93d8;
        }
        .deep-link {
            background-color: #f8f9fa;
            padding: 15px;
            border-radius: 5px;
            margin: 10px 0;
            border-left: 4px solid #9c27b0;
        }
        .page-counter {
            text-align: center;
            font-size: 18px;
            margin: 20px 0;
            color: #666;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>🔗 Deep Link Navigation Test</h1>
        
        <div class="page-counter">
            Deep Link Page Visit Count: <?php 
                if (!isset($_SESSION['deeplink_count'])) {
                    $_SESSION['deeplink_count'] = 0;
                }
                $_SESSION['deeplink_count']++;
                echo $_SESSION['deeplink_count'];
            ?>
        </div>

        <div class="navigation-info">
            <strong>Deep Link Test Page</strong>
            <p>This page simulates deep linking scenarios that might cause back button issues.</p>
            <p><strong>Current URL:</strong> <code><?php echo $_SERVER['REQUEST_URI']; ?></code></p>
        </div>

        <?php
        // Display query parameters if any
        if (!empty($_GET)) {
            echo '<div class="deep-link">';
            echo '<strong>Deep Link Parameters:</strong><br>';
            foreach ($_GET as $key => $value) {
                echo "<code>$key</code> = <code>" . htmlspecialchars($value) . "</code><br>";
            }
            echo '</div>';
        }
        ?>

        <h3>Deep Link Navigation Tests:</h3>
        
        <!-- Regular navigation -->
        <a href="index.php" class="button home">🏠 Back to Home</a>
        <a href="page1.php" class="button">📄 Go to Page 1</a>
        
        <!-- Deep link simulations -->
        <a href="deep-link-test.php?section=profile&user=123" class="button">👤 Profile Deep Link</a>
        <a href="deep-link-test.php?section=settings&tab=privacy" class="button">⚙️ Settings Deep Link</a>
        <a href="deep-link-test.php?action=share&content=test&source=app" class="button">📤 Share Deep Link</a>
        
        <!-- Complex deep links with multiple parameters -->
        <a href="deep-link-test.php?page=product&id=456&variant=blue&size=large&ref=search" class="button">🛍️ Product Deep Link</a>
        
        <!-- Simulated app-to-app deep links -->
        <button onclick="simulateAppDeepLink()" class="button">📱 Simulate App Deep Link</button>
        
        <!-- Fragment/hash deep links -->
        <a href="deep-link-test.php#section-1" class="button">🔗 Fragment Link #section-1</a>
        <a href="deep-link-test.php#section-2" class="button">🔗 Fragment Link #section-2</a>

        <div id="section-1" style="margin-top: 50px; padding: 20px; background-color: #e3f2fd; border-radius: 5px;">
            <h4>Section 1 (Fragment Target)</h4>
            <p>This section is targeted by fragment navigation. URL changes but page doesn't reload.</p>
        </div>

        <div id="section-2" style="margin-top: 20px; padding: 20px; background-color: #e8f5e8; border-radius: 5px;">
            <h4>Section 2 (Fragment Target)</h4>
            <p>Another fragment target. Test how fragment navigation affects back button state.</p>
        </div>

        <div style="margin-top: 30px; padding: 15px; background-color: #fff3e0; border-radius: 5px;">
            <strong>🧪 Deep Link Back Button Tests:</strong>
            <ul>
                <li>Navigate using deep links with complex parameters</li>
                <li>Test fragment navigation (hash links)</li>
                <li>Check if back button works after deep link navigation</li>
                <li>Try simulated app-to-app deep linking</li>
                <li>Verify back button state after parameter changes</li>
            </ul>
        </div>
    </div>

    <script>
        function simulateAppDeepLink() {
            // Simulate what happens when an app deep link is triggered
            const deepLinkUrl = 'deep-link-test.php?source=app&action=open&timestamp=' + Date.now();
            
            // Use replace to simulate how some deep links work
            window.location.replace(deepLinkUrl);
        }

        // Handle fragment navigation
        window.addEventListener('hashchange', function(event) {
            console.log('Hash changed from', event.oldURL, 'to', event.newURL);
            console.log('History length:', window.history.length);
        });

        // Log initial load
        console.log('Deep link page loaded with URL:', window.location.href);
        console.log('Initial history length:', window.history.length);
    </script>
</body>
</html>
