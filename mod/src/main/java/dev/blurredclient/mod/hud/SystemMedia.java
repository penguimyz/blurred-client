package dev.blurredclient.mod.hud;

import dev.blurredclient.mod.BlurredMod;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * What your computer is playing (Spotify, a browser tab, the Media Player app),
 * read from Windows' media controls, the same source as the volume flyout.
 *
 * <p>Java can't reach that API directly, so one small PowerShell process asks
 * it once a second and prints a line; a daemon thread reads the lines. The
 * process only runs while Now playing is on, and it quits by itself if the
 * game goes away (it watches the game's process id), so it can't be left
 * behind. Nothing leaves the machine.
 *
 * <p>Windows only. Elsewhere {@link #current()} stays null and Now playing
 * shows Minecraft's own music.
 */
public final class SystemMedia {
    private SystemMedia() {}

    /** One reading. Times in seconds; {@code length} is 0 when the app doesn't say. */
    public record Track(boolean playing, String app, String artist, String title, double position, double length,
                        long readAt) {
        /** Position now, carried forward from when it was read while playing. */
        public double positionNow() {
            double p = position + (playing ? (System.nanoTime() - readAt) / 1e9 : 0);
            return length > 0 ? Math.min(p, length) : p;
        }
    }

    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    private static final long RETRY_NANOS = 30_000_000_000L;

    private static volatile Track current;
    private static Process process;
    private static long lastStart = Long.MIN_VALUE / 2;

    public static Track current() {
        return current;
    }

    /** Start watching if it isn't already (and hasn't just failed). Cheap to call every frame. */
    public static synchronized void ensureRunning() {
        if (!WINDOWS || (process != null && process.isAlive())) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastStart < RETRY_NANOS) {
            return;
        }
        lastStart = now;
        try {
            String encoded = Base64.getEncoder().encodeToString(
                    script(ProcessHandle.current().pid()).getBytes(StandardCharsets.UTF_16LE));
            Process p = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            p.getOutputStream().close();
            process = p;
            Thread reader = new Thread(() -> read(p), "Blurred now playing");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception e) {
            BlurredMod.LOGGER.warn("Now playing: couldn't start the media watcher", e);
        }
    }

    public static synchronized void stop() {
        if (process != null) {
            process.destroy();
            process = null;
        }
        current = null;
    }

    private static void read(Process p) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                current = parse(line);
            }
        } catch (Exception ignored) {
            // Process ended or was stopped.
        }
        current = null;
    }

    private static Track parse(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 6 || f[3].isBlank()) {
            return null;
        }
        try {
            return new Track(f[0].equals("Playing"), appName(f[1]), f[2].trim(), f[3].trim(),
                    Double.parseDouble(f[4]), Double.parseDouble(f[5]), System.nanoTime());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "Spotify.exe" → "Spotify", "Microsoft.ZuneMusic_8wekyb3d8bbwe!App" → "ZuneMusic". */
    static String appName(String id) {
        String s = id;
        int bang = s.indexOf('!');
        if (bang >= 0) {
            s = s.substring(0, bang);
        }
        int under = s.indexOf('_');
        if (under > 0) {
            s = s.substring(0, under);
        }
        if (s.toLowerCase().endsWith(".exe")) {
            s = s.substring(0, s.length() - 4);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0 && dot < s.length() - 1) {
            s = s.substring(dot + 1);
        }
        // Some apps report an opaque hex id; not worth showing.
        if (s.matches("[0-9A-Fa-f]{12,}")) {
            return "";
        }
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String script(long parentPid) {
        return """
                $ErrorActionPreference = 'Stop'
                [Console]::OutputEncoding = [Text.Encoding]::UTF8
                Add-Type -AssemblyName System.Runtime.WindowsRuntime
                $asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
                function Await($op, $type) { $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op)); $null = $t.Wait(-1); $t.Result }
                $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
                $mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
                function Clean($s) { if ($s) { ($s -replace "[`t`r`n]", ' ') } else { '' } }
                while ($true) {
                  if (-not (Get-Process -Id %d -ErrorAction SilentlyContinue)) { exit }
                  try {
                    $s = $mgr.GetCurrentSession()
                    if ($s) {
                      $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                      $status = [string]$s.GetPlaybackInfo().PlaybackStatus
                      $tl = $s.GetTimelineProperties()
                      $pos = $tl.Position.TotalSeconds
                      if ($status -eq 'Playing' -and $tl.LastUpdatedTime.Year -gt 2000) { $pos += ([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalSeconds }
                      $len = $tl.EndTime.TotalSeconds - $tl.StartTime.TotalSeconds
                      $line = $status + "`t" + (Clean $s.SourceAppUserModelId) + "`t" + (Clean $p.Artist) + "`t" + (Clean $p.Title) + "`t" + [string][int]$pos + "`t" + [string][int]$len
                    } else { $line = 'NONE' }
                  } catch { $line = 'NONE' }
                  [Console]::Out.WriteLine($line)
                  [Console]::Out.Flush()
                  Start-Sleep -Seconds 1
                }
                """.formatted(parentPid);
    }
}
