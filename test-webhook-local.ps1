# Script to test webhook endpoint locally
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Testing Stripe Webhook Endpoint" -ForegroundColor Cyan
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"
$webhookPath = "/api/billing/webhook"
$fullUrl = "$baseUrl$webhookPath"

Write-Host "🔍 Testing URL: $fullUrl" -ForegroundColor Yellow
Write-Host ""

# Test 1: Check if server is running
Write-Host "Test 1: Server health check..." -ForegroundColor Green
try {
    $health = Invoke-WebRequest -Uri "$baseUrl/api/health" -Method GET -UseBasicParsing -TimeoutSec 5
    Write-Host "  ✅ Server is running (Status: $($health.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Server is NOT running or /api/health not accessible" -ForegroundColor Red
    Write-Host "     Start the server with: .\gradlew.bat bootRun" -ForegroundColor Yellow
    exit 1
}
Write-Host ""

# Test 2: Try webhook without signature
Write-Host "Test 2: Webhook without signature (should fail gracefully)..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri $fullUrl -Method POST -Body '{"test":"data"}' -ContentType "application/json" -UseBasicParsing -TimeoutSec 10
    Write-Host "  Response Status: $($response.StatusCode)" -ForegroundColor Cyan
    Write-Host "  Response Body: $($response.Content)" -ForegroundColor Cyan
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "  Status: $statusCode" -ForegroundColor Yellow
    if ($statusCode -eq 400) {
        Write-Host "  ✅ Expected 400 - endpoint is responding!" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️ Unexpected status code" -ForegroundColor Yellow
    }
}
Write-Host ""

# Test 3: Try webhook with fake signature
Write-Host "Test 3: Webhook with fake signature (should fail verification)..." -ForegroundColor Green
$headers = @{
    "stripe-signature" = "t=1234567890,v1=fake_signature"
    "Content-Type" = "application/json"
}
try {
    $response = Invoke-WebRequest -Uri $fullUrl -Method POST -Headers $headers -Body '{"type":"test"}' -UseBasicParsing -TimeoutSec 10
    Write-Host "  Response Status: $($response.StatusCode)" -ForegroundColor Cyan
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "  Status: $statusCode" -ForegroundColor Yellow
    if ($statusCode -eq 400) {
        Write-Host "  ✅ Expected 400 - signature validation is working!" -ForegroundColor Green
    } else {
        Write-Host "  ⚠️ Unexpected status code" -ForegroundColor Yellow
    }
}
Write-Host ""

Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Local tests complete!" -ForegroundColor Cyan
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Check console logs for emoji markers (🔔, 💰, ✅, etc.)" -ForegroundColor Yellow
Write-Host "  2. If localhost works, deploy to production" -ForegroundColor Yellow
Write-Host "  3. Use Stripe CLI to send real test webhook:" -ForegroundColor Yellow
Write-Host "     stripe listen --forward-to $fullUrl" -ForegroundColor Cyan
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan

