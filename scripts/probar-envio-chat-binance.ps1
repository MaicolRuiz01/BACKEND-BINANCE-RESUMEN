# PRUEBA de envio de un mensaje al chat de UNA orden P2P de Binance.
#
# 1) Pide las credenciales del chat (GET /sapi/v1/c2c/chat/retrieveChatCredential).
# 2) Se conecta al WebSocket del chat (wss://im.binance.com/chat/<listenKey>?token=...).
# 3) Si se indica numero de orden, manda UN mensaje de texto a esa orden.
# 4) Se queda escuchando unos segundos e imprime TODO lo que Binance mande (confirmaciones,
#    errores o mensajes del cliente). Con eso se ve el formato real de los mensajes.
#
# Si se deja vacio el numero de orden, solo escucha: sirve para escribir algo en el chat
# desde el celular y ver con que formato lo entrega Binance.
#
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\probar-envio-chat-binance.ps1
# Toma BINANCE_API_KEY / BINANCE_API_SECRET si existen; si no, las pide. No guarda nada.

$ErrorActionPreference = 'Stop'

$apiKey = $env:BINANCE_API_KEY
if (-not $apiKey) { $apiKey = Read-Host 'API key' }

$secret = $env:BINANCE_API_SECRET
if (-not $secret) {
    $seguro = Read-Host 'API secret' -AsSecureString
    $secret = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro))
}

$orderNo = (Read-Host 'Numero de orden (vacio = solo escuchar)').Trim()
$texto = ''
if ($orderNo) {
    $texto = Read-Host 'Mensaje (Enter = mensaje de prueba por defecto)'
    if (-not $texto) { $texto = 'Hola! En un momento te comparto los datos para el pago.' }
}
$segundos = 30

function Firmar([string]$datos, [string]$clave) {
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [Text.Encoding]::UTF8.GetBytes($clave)
    $hash = $hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($datos))
    return ([BitConverter]::ToString($hash) -replace '-', '').ToLower()
}

# ── 1) Credenciales ─────────────────────────────────────────────────────────
$ts = (Invoke-RestMethod 'https://api.binance.com/api/v3/time').serverTime
$query = "clientType=web&recvWindow=60000&timestamp=$ts"
$url = "https://api.binance.com/sapi/v1/c2c/chat/retrieveChatCredential?$query&signature=$(Firmar $query $secret)"
try {
    $cred = (Invoke-RestMethod -Uri $url -Headers @{ 'X-MBX-APIKEY' = $apiKey }).data
} catch {
    Write-Host 'No se pudieron obtener las credenciales del chat:' -ForegroundColor Red
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message } else { Write-Host $_.Exception.Message }
    exit 1
}
if (-not ($cred.listenKey -and $cred.listenToken)) {
    Write-Host 'Binance no devolvio listenKey/listenToken.' -ForegroundColor Red
    exit 1
}

# ── 2) Conexion al WebSocket ────────────────────────────────────────────────
$uri = "$($cred.chatWssUrl)/$($cred.listenKey)?token=$($cred.listenToken)&clientType=web"
$ws = New-Object System.Net.WebSockets.ClientWebSocket
$ct = [Threading.CancellationToken]::None
try {
    $ws.ConnectAsync([Uri]$uri, $ct).Wait()
} catch {
    Write-Host 'No se pudo conectar al WebSocket del chat:' -ForegroundColor Red
    Write-Host $_.Exception.InnerException.Message
    exit 1
}
Write-Host "Conectado al chat ($($ws.State))." -ForegroundColor Green

# ── 3) Envio del mensaje ────────────────────────────────────────────────────
if ($orderNo) {
    $ahora = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $msg = [ordered]@{
        type       = 'text'
        uuid       = "$ahora"
        orderNo    = $orderNo
        content    = $texto
        self       = $true
        clientType = 'web'
        createTime = $ahora
        sendStatus = 0
    } | ConvertTo-Json -Compress
    $bytes = [Text.Encoding]::UTF8.GetBytes($msg)
    $seg = New-Object 'System.ArraySegment[byte]' -ArgumentList @(, $bytes)
    $ws.SendAsync($seg, [Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).Wait()
    Write-Host ''
    Write-Host 'ENVIADO:' -ForegroundColor Cyan
    Write-Host $msg
}

# ── 4) Escuchar respuestas ──────────────────────────────────────────────────
Write-Host ''
Write-Host "Escuchando $segundos s (todo lo que llegue se imprime abajo)..." -ForegroundColor Cyan
$buffer = New-Object byte[] 65536
$acumulado = New-Object System.IO.MemoryStream
$limite = (Get-Date).AddSeconds($segundos)
$tarea = $null
while ((Get-Date) -lt $limite -and $ws.State -eq 'Open') {
    if (-not $tarea) {
        $seg = New-Object 'System.ArraySegment[byte]' -ArgumentList @(, $buffer)
        $tarea = $ws.ReceiveAsync($seg, $ct)
    }
    if (-not $tarea.Wait(500)) { continue }
    $res = $tarea.Result
    $tarea = $null
    if ($res.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) {
        Write-Host "Binance cerro la conexion: $($res.CloseStatus) $($res.CloseStatusDescription)" -ForegroundColor Yellow
        break
    }
    $acumulado.Write($buffer, 0, $res.Count)
    if ($res.EndOfMessage) {
        Write-Host ''
        Write-Host ("[{0:HH:mm:ss}] RECIBIDO:" -f (Get-Date)) -ForegroundColor Green
        Write-Host ([Text.Encoding]::UTF8.GetString($acumulado.ToArray()))
        $acumulado.SetLength(0)
    }
}

if ($ws.State -eq 'Open') {
    try { $ws.CloseAsync([Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'fin', $ct).Wait(3000) | Out-Null } catch {}
}
Write-Host ''
Write-Host 'Fin de la prueba. Revisa en la app de Binance si el mensaje aparece en el chat de la orden.'
