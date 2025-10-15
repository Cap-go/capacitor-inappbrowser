<?php
session_start();
?>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>InAppBrowser Test - Page 2</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            max-width: 800px;
            margin: 0 auto;
            padding: 20px;
            background-color: #e8f4fd;
        }
        .container {
            background: white;
            padding: 30px;
            border-radius: 10px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        h1 {
            color: #007bff;
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
        .button.ajax {
            background-color: #17a2b8;
        }
        .button.ajax:hover {
            background-color: #138496;
        }
        .navigation-info {
            background-color: #cce7ff;
            padding: 15px;
            border-radius: 5px;
            margin: 20px 0;
            border: 1px solid #99d6ff;
        }
        .page-counter {
            text-align: center;
            font-size: 18px;
            margin: 20px 0;
            color: #666;
        }
        #ajax-content {
            margin-top: 20px;
            padding: 15px;
            background-color: #f8f9fa;
            border-radius: 5px;
            display: none;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>📄 Page 2 - AJAX & Dynamic Content Test</h1>
        
        <div class="page-counter">
            Page 2 Visit Count: <?php 
                if (!isset($_SESSION['page2_count'])) {
                    $_SESSION['page2_count'] = 0;
                }
                $_SESSION['page2_count']++;
                echo $_SESSION['page2_count'];
            ?>
        </div>

        <div class="navigation-info">
            <strong>You are now on Page 2</strong>
            <p>This page tests AJAX content loading and dynamic URL changes that might affect back button behavior.</p>
            <p><strong>Current URL:</strong> <code><?php echo $_SERVER['REQUEST_URI']; ?></code></p>
        </div>

        <h3>Navigation from Page 2:</h3>
        
        <!-- Regular navigation -->
        <a href="index.php" class="button home">🏠 Back to Home</a>
        <a href="page1.php" class="button">📄 Go to Page 1</a>
        <a href="page3.php" class="button">📄 Go to Page 3</a>
        
        <!-- AJAX and dynamic content -->
        <button onclick="loadAjaxContent()" class="button ajax">📡 Load AJAX Content</button>
        <button onclick="changeUrlWithoutReload()" class="button ajax">🔗 Change URL (History API)</button>
        <button onclick="pushStateTest()" class="button ajax">📌 Push State Test</button>
        
        <!-- Back navigation -->
        <button onclick="history.back()" class="button">⬅️ JavaScript Back</button>

        <div id="ajax-content">
            <h4>AJAX Content Loaded!</h4>
            <p>This content was loaded via AJAX. The URL might have changed, but did the back button state update correctly?</p>
        </div>

        <div style="margin-top: 30px; padding: 15px; background-color: #d1ecf1; border-radius: 5px;">
            <strong>🧪 AJAX & History API Tests:</strong>
            <ul>
                <li>Load AJAX content and check back button state</li>
                <li>Use History API to change URL without reload</li>
                <li>Test if pushState affects back button availability</li>
                <li>Check if back button works after dynamic content changes</li>
            </ul>
        </div>
    </div>

    <script>
        function loadAjaxContent() {
            const content = document.getElementById('ajax-content');
            content.style.display = 'block';
            
            // Simulate URL change that might affect back button
            if (window.history && window.history.pushState) {
                window.history.pushState({page: 'ajax-loaded'}, 'AJAX Content', '?ajax=loaded');
            }
        }

        function changeUrlWithoutReload() {
            if (window.history && window.history.pushState) {
                const newUrl = '?dynamic=' + Date.now();
                window.history.pushState({page: 'dynamic'}, 'Dynamic URL', newUrl);
                alert('URL changed to: ' + window.location.href + '\nCheck if back button is still available!');
            }
        }

        function pushStateTest() {
            if (window.history && window.history.pushState) {
                window.history.pushState({test: true, step: 1}, 'Push State Test 1', '?pushstate=1');
                setTimeout(() => {
                    window.history.pushState({test: true, step: 2}, 'Push State Test 2', '?pushstate=2');
                    alert('Pushed 2 states. Try the back button - it should go back through these states.');
                }, 1000);
            }
        }

        // Listen for popstate events (back/forward navigation)
        window.addEventListener('popstate', function(event) {
            console.log('PopState event:', event.state);
            if (event.state && event.state.page === 'ajax-loaded') {
                document.getElementById('ajax-content').style.display = 'block';
            } else {
                document.getElementById('ajax-content').style.display = 'none';
            }
        });
    </script>
</body>
</html>
