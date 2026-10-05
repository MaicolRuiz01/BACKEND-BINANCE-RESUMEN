# PRUEBA (solo lectura) del acceso al chat P2P de Binance desde este PC.
#
# Hace la misma llamada que GET /api/p2p/chat/probar-credencial del backend, pero sin
# levantar Spring ni tocar ninguna base de datos: no envía mensajes ni cambia nada en Binance.
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\probar-chat-binance.ps1
# Toma la API key/secret de las variables BINANCE_API_KEY / BINANCE_API_SECRET si existen;
# si no, las pide por consola (el secret no se muestra al escribirlo). No guarda nada.

$ErrorActionPreference = 'Stop'

$apiKey = $env:BINANCE_API_KEY
if (-not $apiKey) { $apiKey = Read-Host 'API key' }

$secret = $env:BINANCE_API_SECRET
if (-not $secret) {
    $seguro = Read-Host 'API secret' -AsSecureString
    $secret = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
}

function Firmar([string]$datos, [string]$clave) {
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($clave)
    $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($datos))
    return ([BitConverter]::ToString($hash) -replace '-', '').ToLower()
}

$ts = (Invoke-RestMethod 'https://api.binance.com/api/v3/time').serverTime
$query = "clientType=web&recvWindow=60000&timestamp=$ts"
$url = "https://api.binance.com/sapi/v1/c2c/chat/retrieveChatCredential?$query&signature=$(Firmar $query $secret)"

try {
    $r = Invoke-RestMethod -Uri $url -Headers @{ 'X-MBX-APIKEY' = $apiKey }
    $d = $r.data
    $ok = [bool]($d.listenKey -and $d.listenToken)
    Write-Host ''
    Write-Host ("ok                  : {0}" -f $ok) -ForegroundColor $(if ($ok) { 'Green' } else { 'Yellow' })
    Write-Host ("code                : {0}" -f $r.code)
    Write-Host ("message             : {0}" -f $r.message)
    Write-Host ("chatWssUrl          : {0}" -f $d.chatWssUrl)
    Write-Host ("listenKeyRecibido   : {0}" -f [bool]$d.listenKey)
    Write-Host ("listenTokenRecibido : {0}" -f [bool]$d.listenToken)
    if (-not $ok) { Write-Host ''; Write-Host ($r | ConvertTo-Json -Depth 5) }
} catch {
    # Aquí sale el motivo exacto de Binance: permisos de la key, IP no autorizada o no Merchant.
    Write-Host ''
    Write-Host 'ok : False' -ForegroundColor Red
    Write-Host ("HTTP: {0}" -f $_.Exception.Response.StatusCode.value__)
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message } else { Write-Host $_.Exception.Message }
}
