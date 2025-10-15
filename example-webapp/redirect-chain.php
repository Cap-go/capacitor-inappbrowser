<?php
// Redirect chain to test complex navigation scenarios

$step = (int)($_GET['step'] ?? 1);
$return = $_GET['return'] ?? 'index';
$maxSteps = 3;

// Add delay to simulate real-world redirect chains
usleep(300000); // 0.3 second delay

if ($step < $maxSteps) {
    // Continue the chain
    $nextStep = $step + 1;
    header("HTTP/1.1 302 Found");
    header("Location: redirect-chain.php?step=$nextStep&return=$return");
    exit();
} else {
    // End of chain, redirect to final destination
    $destinations = [
        'index' => 'index.php',
        'page1' => 'page1.php',
        'page2' => 'page2.php',
        'page3' => 'page3.php'
    ];
    
    $finalDestination = $destinations[$return] ?? 'index.php';
    
    header("HTTP/1.1 302 Found");
    header("Location: $finalDestination?redirected=chain&steps=$maxSteps");
    exit();
}
?>
