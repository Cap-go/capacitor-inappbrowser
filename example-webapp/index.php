<?php
session_start();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>InAppBrowser Test - Home</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #f5f5f5;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #333;
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
        .button.redirect {
            background-color: #28a745;
        }
        .button.redirect:hover {
            background-color: #1e7e34;
        }
        .button.javascript {
            background-color: #ffc107;
            color: #212529;
        }
        .button.javascript:hover {
            background-color: #e0a800;
        }
        .navigation-info {
            background-color: #e9ecef;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
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
        <h1>🏠 InAppBrowser Navigation Test - Home Page</h1>
        
        <div class="page-counter">
            Page Visit Count: <?php 
                if (!isset($_SESSION['visit_count'])) {
                    $_SESSION['visit_count'] = 0;
                }
                $_SESSION['visit_count']++;
                echo $_SESSION['visit_count'];
            ?>
        </div>

        <div class="navigation-info">
            <strong>Test the back button behavior:</strong>
            <p>This webapp is designed to test various navigation scenarios that might cause the back button to not work properly in the InAppBrowser.</p>
        </div>

        <h3>Navigation Options:</h3>
        
        <!-- Regular links -->
        <a href="page1.php" class="button">📄 Go to Page 1 (Regular Link)</a>
        <a href="page2.php" class="button">📄 Go to Page 2 (Regular Link)</a>
        
        <!-- Server-side redirects -->
        <a href="redirect.php?target=page1" class="button redirect">🔄 Redirect to Page 1 (302)</a>
        <a href="redirect.php?target=page2" class="button redirect">🔄 Redirect to Page 2 (302)</a>
        <a href="redirect.php?target=page1&type=301" class="button redirect">🔄 Redirect to Page 1 (301)</a>
        
        <!-- JavaScript navigation -->
        <button onclick="window.location.href='page1.php'" class="button javascript">⚡ JS Navigate to Page 1</button>
        <button onclick="window.location.replace('page2.php')" class="button javascript">⚡ JS Replace to Page 2</button>
        
        <!-- Form submissions -->
        <form method="POST" action="form-handler.php" style="display: inline;">
            <input type="hidden" name="redirect_to" value="page1.php">
            <button type="submit" class="button">📝 Form Submit → Page 1</button>
        </form>
        
        <!-- Hash navigation (same page) -->
        <a href="#section1" class="button">🔗 Hash Navigation #section1</a>
        
        <!-- External then back -->
        <a href="external-redirect.php" class="button redirect">🌐 External Site Test</a>

        <div id="section1" style="margin-top: 50px; padding: 20px; background-color: #f8f9fa; border-radius: 5px;">
            <h4>Section 1 (Hash Target)</h4>
            <p>This section is targeted by the hash navigation above. Notice how the URL changes but it's still the same page.</p>
        </div>

        <div style="margin-top: 30px; padding: 15px; background-color: #d1ecf1; border-radius: 5px;">
            <strong>🧪 Testing Instructions:</strong>
            <ol>
                <li>Try different navigation methods above</li>
                <li>Check if the back button becomes available/unavailable</li>
                <li>Test going back multiple steps</li>
                <li>Pay attention to URL changes and back button state</li>
            </ol>
        </div>
    </div>
</body>
</html>
