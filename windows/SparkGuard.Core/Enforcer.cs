namespace SparkGuard;

/// <summary>
/// Keeps the hosts file as the phone's state says (K4, K5). One call to <see cref="Tick"/> is one second of the service's life. The file is rewritten whenever it
/// differs from what it must be, and each time that happens without the service having changed it, that is logged as an attempt to change it (K5).
/// The paths are given in, so the tests use a temporary file and never the real hosts file.
/// </summary>
public sealed class Enforcer
{
    private readonly string _hosts; private readonly string _statePath; private readonly GuardLog _log; private readonly Func<DateTime> _clock;
    private GuardState? _state; private string? _lastWritten;
    public DateTime? LastIssuedUtc => _state?.IssuedAtUtc;
    public GuardState? State => _state;

    public Enforcer(string hostsPath, string statePath, GuardLog log, Func<DateTime>? clock = null)
    {
        _hosts = hostsPath; _statePath = statePath; _log = log; _clock = clock ?? (() => DateTime.UtcNow);
        if (File.Exists(statePath)) { try { _state = GuardState.Parse(File.ReadAllBytes(statePath)); } catch (IOException) { } }     // survives a restart (K6)
    }

    /// <summary>Takes a state that already passed <see cref="StateVerifier"/>; keeps it on disk so a restart or reboot enforces it at once.</summary>
    public void Accept(GuardState s, byte[] raw)
    {
        _state = s;
        var tmp = _statePath + ".tmp"; File.WriteAllBytes(tmp, raw); File.Move(tmp, _statePath, overwrite: true);
        _log.Write(_clock(), "state", $"accepted {s.Windows.Count} windows, {s.Domains.Count} domains");
    }

    public bool BlockedNow => _state?.ActiveAt(_clock()) is not null;

    /// <summary>X7: with no state there is nothing to enforce, and the file is left as it is. A broken file read never blocks or unblocks anything.</summary>
    public bool Tick()
    {
        var s = _state; if (s is null) return false;
        string current;
        try { current = File.Exists(_hosts) ? File.ReadAllText(_hosts) : ""; } catch (IOException) { return false; }
        var want = Hosts.Desired(current, s.Domains, BlockedNow);
        if (Normalise(current) == Normalise(want)) return false;
        if (_lastWritten != null && Normalise(current) != Normalise(_lastWritten)) _log.Write(_clock(), "attempt", "the hosts file was changed and was put back");
        try { File.WriteAllText(_hosts, want); _lastWritten = want; return true; }
        catch (UnauthorizedAccessException) { _log.Write(_clock(), "error", "could not write the hosts file"); return false; }
        catch (IOException) { return false; }
    }

    private static string Normalise(string t) => t.Replace("\r\n", "\n").TrimEnd('\n');
}
