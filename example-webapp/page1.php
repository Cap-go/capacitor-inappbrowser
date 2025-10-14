<?php
session_start();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>InAppBrowser Test - Page 1</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #e8f5e8;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #28a745;
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
        .button.redirect {
            background-color: #dc3545;
        }
        .button.redirect:hover {
            background-color: #c82333;
        }
        .button.close {
            background-color: #dc3545;
        }
        .button.close:hover {
            background-color: #c82333;
        }
        .navigation-info {
            background-color: #d4edda;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
            border: 1px solid #c3e6cb;
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
        <h1>📄 Page 1 - Navigation Test</h1>
        
        <div class="page-counter">
            Page 1 Visit Count: <?php 
                if (!isset($_SESSION['page1_count'])) {
                    $_SESSION['page1_count'] = 0;
                }
                $_SESSION['page1_count']++;
                echo $_SESSION['page1_count'];
            ?>
        </div>

        <div class="navigation-info">
            <strong>You are now on Page 1</strong>
            <p>Test the back button - it should be enabled and take you back to the previous page.</p>
            <p><strong>Current URL:</strong> <code><?php echo $_SERVER['REQUEST_URI']; ?></code></p>
        </div>

        <h3>Navigation from Page 1:</h3>
        
        <!-- Back to home -->
        <a href="index.php" class="button home">🏠 Back to Home</a>
        
        <!-- To other pages -->
        <a href="page2.php" class="button">📄 Go to Page 2</a>
        <a href="page3.php" class="button">📄 Go to Page 3</a>
        
        <!-- Redirect chains that might cause back button issues -->
        <a href="redirect-chain.php?step=1" class="button redirect">🔄 Redirect Chain Test</a>
        
        <!-- JavaScript back -->
        <button onclick="history.back()" class="button">⬅️ JavaScript Back</button>
        <button onclick="history.forward()" class="button">➡️ JavaScript Forward</button>
        
        <!-- Reload current page -->
        <button onclick="window.location.reload()" class="button">🔄 Reload Page</button>
        
        <!-- Same page with query params -->
        <a href="page1.php?test=1" class="button">🔗 Same Page + Query</a>
        <a href="page1.php?test=2&more=data" class="button">🔗 Same Page + More Queries</a>
        
        <h3>Close Browser Tests (Page 1):</h3>
        <button onclick="testWindowClose()" class="button close">❌ Test window.close()</button>
        <button onclick="testMobileAppClose()" class="button close">📱 Test mobileApp.close()</button>

        <?php if (isset($_GET['test'])): ?>
        <div style="margin-top: 20px; padding: 15px; background-color: #fff3cd; border-radius: 5px;">
            <strong>Query Parameters Detected:</strong>
            <pre><?php print_r($_GET); ?></pre>
        </div>
        <?php endif; ?>

        <div style="margin-top: 30px; padding: 15px; background-color: #f8d7da; border-radius: 5px;">
            <strong>🔍 Back Button Test Points:</strong>
            <ul>
                <li>Did the back button become available when you arrived here?</li>
                <li>Try using browser back vs JavaScript back</li>
                <li>Test redirect chains and see if back button works correctly</li>
                <li>Check if query parameter changes affect back button state</li>
            </ul>
        </div>
    </div>

    <script>
        function testWindowClose() {
            console.log('Testing window.close() from Page 1...');
            alert('Testing window.close() from Page 1 - should close immediately');
            try {
                window.close();
            } catch (e) {
                console.error('Error calling window.close():', e);
                alert('Error: ' + e.message);
            }
        }

        function testMobileAppClose() {
            console.log('Testing mobileApp.close() from Page 1...');
            alert('Testing mobileApp.close() from Page 1');
            try {
                if (window.mobileApp && window.mobileApp.close) {
                    window.mobileApp.close();
                } else {
                    alert('mobileApp.close() not available - this only works in InAppBrowser');
                }
            } catch (e) {
                console.error('Error calling mobileApp.close():', e);
                alert('Error: ' + e.message);
            }
        }

        console.log('Page 1 loaded - Close test functions available');
    </script>
</body>
</html>
