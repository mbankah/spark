using System.Net;
using System.Security.AccessControl;
using System.Security.Principal;
using System.Text.Json;
using SparkGuard;

namespace SparkGuard.Service;

public sealed record GuardPaths(string DataDir, string HostsFile, int Port = 47831, bool Lock = true);

/// <summary>
/// The service (K4, K5, K6). Once a second it makes the hosts file match the phone's last state. It listens on port 47831 for the phone's signed state (A4), serves
/// the browser extension on the loopback address only (K8, the second layer), logs every attempt to change the hosts file (K5), and notes when it was stopped (K6).
/// Windows restarts it by itself if it dies (the installer sets the recovery actions).
/// </summary>
public sealed class Worker : BackgroundService
{
    private readonly GuardPaths _paths; private readonly ILogger<Worker> _logger;
    private GuardLog _log = null!; private Enforcer _enforcer = null!; private byte[] _key = null!;

    public Worker(GuardPaths paths, ILogger<Worker> logger) { _paths = paths; _logger = logger; }

    public override Task StartAsync(CancellationToken ct)
    {
        Directory.CreateDirectory(_paths.DataDir);
        if (_paths.Lock) LockDown(_paths.DataDir);
        _log = new GuardLog(Path.Combine(_paths.DataDir, "guard.log"));
        var pairing = Path.Combine(_paths.DataDir, "pairing.code");
        if (!File.Exists(pairing)) File.WriteAllText(pairing, StateVerifier.NewPairingCode());
        _key = StateVerifier.KeyFromCode(File.ReadAllText(pairing));
        _enforcer = new Enforcer(_paths.HostsFile, Path.Combine(_paths.DataDir, "state.json"), _log);
        var heartbeat = Path.Combine(_paths.DataDir, "heartbeat.txt");
        if (File.Exists(heartbeat) && DateTime.TryParse(File.ReadAllText(heartbeat), null, System.Globalization.DateTimeStyles.RoundtripKind, out var last) && DateTime.UtcNow - last > TimeSpan.FromSeconds(15))
            _log.Write(DateTime.UtcNow, "stopped", $"Spark Guard was not running from {last:O} until now");        // K6: stopping it is noticed and logged
        _log.Write(DateTime.UtcNow, "started", "service started");
        _log.Trim(DateTime.UtcNow);
        if (_paths.Lock) LockHosts();
        return base.StartAsync(ct);
    }

    protected override async Task ExecuteAsync(CancellationToken ct)
    {
        _ = Task.Run(() => ListenAsync(ct), ct);
        var heartbeat = Path.Combine(_paths.DataDir, "heartbeat.txt");
        var tick = new PeriodicTimer(TimeSpan.FromSeconds(1)); var n = 0;
        while (await tick.WaitForNextTickAsync(ct))
        {
            try
            {
                if (_enforcer.Tick() && _paths.Lock) LockHosts();
                File.WriteAllText(heartbeat, DateTime.UtcNow.ToString("O"));
                if (++n % 3600 == 0) _log.Trim(DateTime.UtcNow);
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException) { _logger.LogWarning(e, "tick failed"); }       // X7: keep going
        }
    }

    public override async Task StopAsync(CancellationToken ct)
    {
        _log.Write(DateTime.UtcNow, "stopped", "service stopping");
        await base.StopAsync(ct);
    }

    private HttpListener? Open(string prefix)
    {
        var l = new HttpListener(); l.Prefixes.Add(prefix);
        try { l.Start(); return l; } catch (Exception e) when (e is HttpListenerException or ObjectDisposedException) { try { l.Close(); } catch { } return null; }
    }

    private async Task ListenAsync(CancellationToken ct)
    {
        try
        {
            var http = Open($"http://+:{_paths.Port}/");
            if (http is null)
            {
                http = Open($"http://127.0.0.1:{_paths.Port}/");
                _log.Write(DateTime.UtcNow, "error", "no permission to listen on the network, listening on this PC only (the installer grants it)");
            }
            if (http is null) { _log.Write(DateTime.UtcNow, "error", $"could not listen on port {_paths.Port}"); return; }
            using (http)
            {
                ct.Register(() => { try { http.Stop(); } catch (ObjectDisposedException) { } });
                while (!ct.IsCancellationRequested)
                {
                    HttpListenerContext c;
                    try { c = await http.GetContextAsync(); } catch { break; }
                    _ = Task.Run(() => Handle(c), ct);
                }
            }
        }
        catch (Exception e) { _logger.LogError(e, "listener stopped"); _log.Write(DateTime.UtcNow, "error", "listener stopped: " + e.Message); }
    }

    private void Handle(HttpListenerContext c)
    {
        try
        {
            var path = c.Request.Url!.AbsolutePath;
            if (c.Request.HttpMethod == "POST" && path == "/state")
            {
                using var ms = new MemoryStream(); c.Request.InputStream.CopyTo(ms);
                var body = ms.ToArray();
                if (body.Length > 64 * 1024) { Reply(c, 413, "too large"); return; }
                var (verdict, state) = StateVerifier.Verify(_key, body, c.Request.Headers["X-Spark-Signature"], DateTime.UtcNow, _enforcer.LastIssuedUtc);
                if (verdict == Verdict.Accepted) { _enforcer.Accept(state!, body); Reply(c, 200, "ok"); }
                else { _log.Write(DateTime.UtcNow, "refused", $"{verdict} from {c.Request.RemoteEndPoint?.Address}"); Reply(c, verdict == Verdict.Stale ? 409 : 400, verdict.ToString()); }
            }
            else if (c.Request.HttpMethod == "GET" && path == "/state" && IPAddress.IsLoopback(c.Request.RemoteEndPoint!.Address))
            {
                // K8: the browser extension asks this PC only. It gets the domains and whether they are blocked right now, never the pairing key.
                var s = _enforcer.State;
                var w = s?.ActiveAt(DateTime.UtcNow);
                Reply(c, 200, JsonSerializer.Serialize(new { blocked = w is not null, domains = w is not null ? s!.Domains : Array.Empty<string>(), until = w?.End }), "application/json");
            }
            else if (c.Request.HttpMethod == "GET" && path == "/health") Reply(c, 200, "ok");
            else Reply(c, 404, "not found");
        }
        catch (Exception e) { _logger.LogWarning(e, "request failed"); try { c.Response.Abort(); } catch { } }
    }

    private static void Reply(HttpListenerContext c, int code, string text, string type = "text/plain")
    {
        var bytes = System.Text.Encoding.UTF8.GetBytes(text);
        c.Response.StatusCode = code; c.Response.ContentType = type; c.Response.ContentLength64 = bytes.Length;
        c.Response.OutputStream.Write(bytes); c.Response.Close();
    }

    /// <summary>The data folder (state, pairing code, log) is for SYSTEM and administrators only.</summary>
    private static void LockDown(string dir)
    {
        var info = new DirectoryInfo(dir); var acl = info.GetAccessControl();
        acl.SetAccessRuleProtection(true, false);
        foreach (FileSystemAccessRule r in acl.GetAccessRules(true, false, typeof(NTAccount))) acl.RemoveAccessRule(r);
        foreach (var sid in new[] { WellKnownSidType.LocalSystemSid, WellKnownSidType.BuiltinAdministratorsSid })
            acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(sid, null), FileSystemRights.FullControl, InheritanceFlags.ContainerInherit | InheritanceFlags.ObjectInherit, PropagationFlags.None, AccessControlType.Allow));
        info.SetAccessControl(acl);
    }

    /// <summary>K5: only SYSTEM can write the hosts file; everyone else can read it. An administrator can still change permissions, which is why the file is also put back within a second.</summary>
    private void LockHosts()
    {
        try
        {
            var f = new FileInfo(_paths.HostsFile); if (!f.Exists) return;
            var acl = f.GetAccessControl(); acl.SetAccessRuleProtection(true, false);
            foreach (FileSystemAccessRule r in acl.GetAccessRules(true, false, typeof(NTAccount))) acl.RemoveAccessRule(r);
            acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(WellKnownSidType.LocalSystemSid, null), FileSystemRights.FullControl, AccessControlType.Allow));
            acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(WellKnownSidType.BuiltinAdministratorsSid, null), FileSystemRights.ReadAndExecute, AccessControlType.Allow));
            acl.AddAccessRule(new FileSystemAccessRule(new SecurityIdentifier(WellKnownSidType.BuiltinUsersSid, null), FileSystemRights.ReadAndExecute, AccessControlType.Allow));
            f.SetAccessControl(acl);
        }
        catch (Exception e) when (e is UnauthorizedAccessException or IOException or InvalidOperationException) { _logger.LogWarning(e, "could not lock the hosts file"); }
    }
}
