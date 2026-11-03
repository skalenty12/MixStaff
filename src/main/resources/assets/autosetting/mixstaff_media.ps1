$ErrorActionPreference = 'SilentlyContinue'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
        $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
    })[0]
} catch {
    [Console]::Out.WriteLine('ERR|Windows Runtime unavailable: ' + $_.Exception.Message)
    [Console]::Out.Flush()
    exit 1
}
function Await($WinRtTask, $ResultType) {
    $asTask = $asTaskGeneric.MakeGenericMethod($ResultType)
    $netTask = $asTask.Invoke($null, @($WinRtTask))
    $netTask.Wait(-1) | Out-Null
    $netTask.Result
}
Add-Type -Namespace MixStaff -Name MediaKeys -MemberDefinition @"
[System.Runtime.InteropServices.DllImport("user32.dll")]
public static extern void keybd_event(byte bVk, byte bScan, int dwFlags, int dwExtraInfo);
public static void Press(byte key) {
    keybd_event(key, 0, 0, 0);
    keybd_event(key, 0, 2, 0);
}
"@
try {
    [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime] | Out-Null
} catch {
    [Console]::Out.WriteLine('ERR|SMTC unavailable: ' + $_.Exception.Message)
    [Console]::Out.Flush()
    exit 1
}

$manager = $null
$last = ''
$lastError = ''
$lastEmitAt = 0
$inputTask = [Console]::In.ReadLineAsync()
while ($true) {
    try {
        if ($inputTask.IsCompleted) {
            $cmd = $inputTask.Result
            if ($cmd -eq 'PREV') { [MixStaff.MediaKeys]::Press(0xB1) }
            elseif ($cmd -eq 'NEXT') { [MixStaff.MediaKeys]::Press(0xB0) }
            elseif ($cmd -eq 'PLAY') { [MixStaff.MediaKeys]::Press(0xB3) }
            $inputTask = [Console]::In.ReadLineAsync()
        }
    } catch { }

    $line = 'NONE'
    try {
        if ($null -eq $manager) {
            $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
        }
        $session = $null
        $fallbackSession = $manager.GetCurrentSession()
        foreach ($candidate in @($manager.GetSessions())) {
            $candidateInfo = $candidate.GetPlaybackInfo()
            if ($candidateInfo.PlaybackStatus.ToString() -eq 'Playing') {
                $session = $candidate
                break
            }
        }
        if ($null -eq $session) {
            $session = $fallbackSession
        }
        if ($null -ne $session) {
            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $info = $session.GetPlaybackInfo()
            $timeline = $session.GetTimelineProperties()
            $title = ($props.Title -replace '\|', ' ').Trim()
            $artist = ($props.Artist -replace '\|', ' ').Trim()
            $line = 'M|' + $info.PlaybackStatus.ToString() + '|' + $title + '|' + $artist + '|' + [int]$timeline.EndTime.TotalSeconds + '|' + [int]$timeline.Position.TotalSeconds
        }
        $lastError = ''
    } catch {
        $errorMessage = $_.Exception.Message -replace '[\r\n|]', ' '
        if ($errorMessage -ne $lastError) {
            [Console]::Out.WriteLine('ERR|' + $errorMessage)
            [Console]::Out.Flush()
            $lastError = $errorMessage
        }
        $manager = $null
        $line = 'NONE'
    }

    $now = [DateTime]::UtcNow.Ticks
    if ($line -ne $last -or $now - $lastEmitAt -ge 50000000) {
        [Console]::Out.WriteLine($line)
        [Console]::Out.Flush()
        $last = $line
        $lastEmitAt = $now
    }
    Start-Sleep -Milliseconds 400
}
