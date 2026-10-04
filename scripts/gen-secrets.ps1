# Generate cryptographically secure random secrets for .env configuration
$jwtBytes = New-Object byte[] 48
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$jwtSecret = [Convert]::ToBase64String($jwtBytes)

$mongoBytes = New-Object byte[] 24
$rng.GetBytes($mongoBytes)
$mongoPassword = [Convert]::ToBase64String($mongoBytes)

Write-Host "=========================================================="
Write-Host "Generated Cryptographically Secure Random Secrets for .env"
Write-Host "=========================================================="
Write-Host "JWT_SECRET=$jwtSecret"
Write-Host "MONGO_INITDB_ROOT_PASSWORD=$mongoPassword"
Write-Host "=========================================================="

