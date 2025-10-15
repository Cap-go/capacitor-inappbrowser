<?php
// Simple redirect script to test different redirect types

$target = $_GET['target'] ?? 'index';
$type = $_GET['type'] ?? '302';

// Map target names to actual files
$targets = [
    'index' => 'index.php',
    'page1' => 'page1.php',
    'page2' => 'page2.php',
    'page3' => 'page3.php'
];

$targetFile = $targets[$target] ?? 'index.php';

// Add some delay to simulate network latency that might affect back button
usleep(500000); // 0.5 second delay

// Set appropriate redirect header
if ($type === '301') {
    header("HTTP/1.1 301 Moved Permanently");
} else {
    header("HTTP/1.1 302 Found");
}

header("Location: $targetFile");
exit();
?>
