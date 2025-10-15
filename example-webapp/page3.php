<?php
session_start();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>InAppBrowser Test - Page 3</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #fff3e0;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #ff9800;
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
        .button.iframe {
            background-color: #6f42c1;
        }
        .button.iframe:hover {
            background-color: #5a32a3;
        }
        .navigation-info {
            background-color: #ffe0b3;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
            border: 1px solid #ffcc80;
        }
        .page-counter {
            text-align: center;
            font-size: 18px;
            margin: 20px 0;
            color: #666;
        }
        iframe {
            width: 100%;
            height: 300px;
            border: 1px solid #ddd;
            border-radius: 5px;
            margin: 20px 0;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📄 Page 3 - Iframe & Complex Navigation</h1>
        
        <div class="page-counter">
            Page 3 Visit Count: <?php 
                if (!isset($_SESSION['page3_count'])) {
                    $_SESSION['page3_count'] = 0;
                }
                $_SESSION['page3_count']++;
                echo $_SESSION['page3_count'];
            ?>
        </div>

        <div class="navigation-info">
            <strong>You are now on Page 3</strong>
            <p>This page tests iframe navigation and complex scenarios that might interfere with back button behavior.</p>
            <p><strong>Current URL:</strong> <code><?php echo $_SERVER['REQUEST_URI']; ?></code></p>
        </div>

        <h3>Navigation from Page 3:</h3>
        
        <!-- Regular navigation -->
        <a href="index.php" class="button home">🏠 Back to Home</a>
        <a href="page1.php" class="button">📄 Go to Page 1</a>
        <a href="page2.php" class="button">📄 Go to Page 2</a>
        
        <!-- Iframe tests -->
        <button onclick="loadIframe('page1.php')" class="button iframe">🖼️ Load Page 1 in Iframe</button>
        <button onclick="loadIframe('page2.php')" class="button iframe">🖼️ Load Page 2 in Iframe</button>
        <button onclick="loadExternalIframe()" class="button iframe">🌐 Load External Site in Iframe</button>
        
        <!-- Complex navigation scenarios -->
        <a href="deep-link-test.php" class="button">🔗 Deep Link Test</a>
        <button onclick="multipleRedirects()" class="button">🔄 Multiple Redirects Test</button>
        
        <!-- Back navigation -->
        <button onclick="history.back()" class="button">⬅️ JavaScript Back</button>

        <iframe id="test-iframe" src="about:blank" style="display: none;"></iframe>

        <div style="margin-top: 30px; padding: 15px; background-color: #e1f5fe; border-radius: 5px;">
            <strong>🧪 Complex Navigation Tests:</strong>
            <ul>
                <li>Load content in iframes and check back button behavior</li>
                <li>Test deep linking scenarios</li>
                <li>Try multiple redirects in sequence</li>
                <li>Check if iframe navigation affects parent back button</li>
            </ul>
        </div>

        <div style="margin-top: 20px; padding: 15px; background-color: #ffebee; border-radius: 5px;">
            <strong>🐛 Known Issue Testing:</strong>
            <p>This page is designed to reproduce the back button issue where:</p>
            <ol>
                <li>Navigate through several pages</li>
                <li>Back button becomes available</li>
                <li>On certain URL changes, back button becomes greyed out</li>
                <li>Further navigation makes it available again</li>
                <li>You can then go back multiple steps</li>
            </ol>
        </div>
    </div>

    <script>
        function loadIframe(src) {
            const iframe = document.getElementById('test-iframe');
            iframe.src = src;
            iframe.style.display = 'block';
            
            // This might affect the parent's navigation history
            console.log('Loaded iframe with src:', src);
        }

        function loadExternalIframe() {
            const iframe = document.getElementById('test-iframe');
            iframe.src = 'https://httpbin.org/html';
            iframe.style.display = 'block';
        }

        function multipleRedirects() {
            // This will trigger a series of redirects that might confuse the back button
            window.location.href = 'redirect-chain.php?step=1&return=page3';
        }

        // Monitor iframe navigation
        document.getElementById('test-iframe').addEventListener('load', function() {
            console.log('Iframe loaded, current URL:', this.src);
            console.log('Parent history length:', window.history.length);
        });
    </script>
</body>
</html>
