# Script to diagnose production webhook endpoint
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Diagnosing Production Webhook" -ForegroundColor Cyan
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""

$prodUrl = "https://api.valoracloud.com"
$webhookPath = "/api/billing/webhook"
$fullUrl = "$prodUrl$webhookPath"

Write-Host "🔍 Testing URL: $fullUrl" -ForegroundColor Yellow
Write-Host ""

# Test 1: DNS Resolution
Write-Host "Test 1: DNS Resolution..." -ForegroundColor Green
try {
    $dns = Resolve-DnsName -Name "api.valoracloud.com" -ErrorAction Stop
    Write-Host "  ✅ DNS resolves to: $($dns[0].IPAddress)" -ForegroundColor Green
} catch {
    Write-Host "  ❌ DNS resolution failed!" -ForegroundColor Red
}
Write-Host ""

# Test 2: HTTPS connectivity
Write-Host "Test 2: HTTPS Connectivity..." -ForegroundColor Green
try {
    $healthUrl = "$prodUrl/api/health"
    Write-Host "  Trying: $healthUrl" -ForegroundColor Cyan
    $health = Invoke-WebRequest -Uri $healthUrl -Method GET -UseBasicParsing -TimeoutSec 10
    Write-Host "  ✅ Server is reachable (Status: $($health.StatusCode))" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Cannot reach server!" -ForegroundColor Red
    Write-Host "     Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Possible causes:" -ForegroundColor Yellow
    Write-Host "    - Server is down" -ForegroundColor Yellow
    Write-Host "    - Firewall blocking requests" -ForegroundColor Yellow
    Write-Host "    - Wrong URL/port" -ForegroundColor Yellow
}
Write-Host ""

# Test 3: Webhook endpoint reachability
Write-Host "Test 3: Webhook Endpoint (without signature)..." -ForegroundColor Green
try {
    $response = Invoke-WebRequest -Uri $fullUrl -Method POST -Body '{"test":"data"}' -ContentType "application/json" -UseBasicParsing -TimeoutSec 30
    Write-Host "  Response: $($response.StatusCode)" -ForegroundColor Cyan
    Write-Host "  Body: $($response.Content)" -ForegroundColor Cyan
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "  Status: $statusCode" -ForegroundColor Yellow

    if ($statusCode -eq 400) {
        Write-Host "  ✅ GOOD! Endpoint responds with 400 (missing signature)" -ForegroundColor Green
        Write-Host "     This means the endpoint IS working!" -ForegroundColor Green
    } elseif ($statusCode -eq 401 -or $statusCode -eq 403) {
        Write-Host "  ❌ BAD! Authentication/authorization blocking webhook" -ForegroundColor Red
        Write-Host "     SecurityConfig is NOT allowing /billing/webhook" -ForegroundColor Red
    } elseif ($statusCode -eq 404) {
        Write-Host "  ❌ BAD! Endpoint not found (404)" -ForegroundColor Red
        Write-Host "     Check context-path in application.yml" -ForegroundColor Red
    } elseif ($statusCode -eq 0) {
        Write-Host "  ❌ TIMEOUT! Server not responding within 30s" -ForegroundColor Red
    } else {
        Write-Host "  ⚠️ Unexpected status: $statusCode" -ForegroundColor Yellow
    }
}
Write-Host ""

# Test 4: Check logs reminder
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Next: Check production logs for emoji markers" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Look for:" -ForegroundColor Cyan
Write-Host "    🔔 STRIPE WEBHOOK RECEIVED" -ForegroundColor Cyan
Write-Host "    🔵 BillingService.handleWebhook called" -ForegroundColor Cyan
Write-Host "    💰 Processing payment_intent.succeeded" -ForegroundColor Cyan
Write-Host "    ✅ Webhook processed successfully" -ForegroundColor Cyan
Write-Host ""
Write-Host "  If you see NONE of these, webhook is being blocked before reaching the controller!" -ForegroundColor Red
Write-Host "═════════════════════════════════════════" -ForegroundColor Cyan

