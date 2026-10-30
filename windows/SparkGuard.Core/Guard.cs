using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace SparkGuard;

/// <summary>One stretch of time in which the sites are blocked, in UTC. The phone works these out from the plan (K4, A4).</summary>
public sealed record BlockWindow([property: JsonPropertyName("start")] DateTime Start, [property: JsonPropertyName("end")] DateTime End, [property: JsonPropertyName("why")] string Why);

/// <summary>What the phone tells the PC: which domains, and when they are blocked (A4, A6: the phone is the source of truth).</summary>
public sealed record GuardState(
    [property: JsonPropertyName("v")] int Version,
    [property: JsonPropertyName("issuedAtUtc")] DateTime IssuedAtUtc,
    [property: JsonPropertyName("domains")] IReadOnlyList<string> Domains,
    [property: JsonPropertyName("windows")] IReadOnlyList<BlockWindow> Windows)
{
    public static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive = true };

    public static GuardState? Parse(byte[] body)
    {
        try { return JsonSerializer.Deserialize<GuardState>(body, Json); } catch (JsonException) { return null; }
    }

    /// <summary>The window that covers <paramref name="nowUtc"/>, if any. Blocking is never decided by anything but the phone's windows.</summary>
    public BlockWindow? ActiveAt(DateTime nowUtc) => Windows.FirstOrDefault(w => nowUtc >= w.Start && nowUtc < w.End);
}

public enum Verdict { Accepted, BadSignature, Malformed, Stale, FromTheFuture, Unsupported }

/// <summary>
/// Checks a state sent by the phone. Signed with HMAC-SHA256 over the exact body bytes with the pairing key, so nobody on the network can change what is blocked,
/// and a state older than the last accepted one (or too far ahead) is refused, so an old state cannot be replayed to turn blocking off.
/// </summary>
public static class StateVerifier
{
    public static readonly TimeSpan MaxFuture = TimeSpan.FromMinutes(5);

    public static string Sign(byte[] key, byte[] body) => Convert.ToHexString(HMACSHA256.HashData(key, body)).ToLowerInvariant();

    public static (Verdict, GuardState?) Verify(byte[] key, byte[] body, string? signatureHex, DateTime nowUtc, DateTime? lastIssuedUtc)
    {
        if (string.IsNullOrWhiteSpace(signatureHex)) return (Verdict.BadSignature, null);
        var expected = HMACSHA256.HashData(key, body);
        byte[] given;
        try { given = Convert.FromHexString(signatureHex.Trim()); } catch (FormatException) { return (Verdict.BadSignature, null); }
        if (!CryptographicOperations.FixedTimeEquals(expected, given)) return (Verdict.BadSignature, null);
        var state = GuardState.Parse(body);
        if (state is null || state.Domains is null || state.Windows is null) return (Verdict.Malformed, null);
        if (state.Version != 1) return (Verdict.Unsupported, null);
        if (state.IssuedAtUtc > nowUtc + MaxFuture) return (Verdict.FromTheFuture, null);
        if (lastIssuedUtc is { } last && state.IssuedAtUtc <= last) return (Verdict.Stale, null);
        if (state.Domains.Any(d => !Hosts.IsPlainDomain(d))) return (Verdict.Malformed, null);
        return (Verdict.Accepted, state);
    }

    /// <summary>The pairing key is shown to the person as base64url and typed into the phone once.</summary>
    public static string NewPairingCode() => Convert.ToBase64String(RandomNumberGenerator.GetBytes(24)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    public static byte[] KeyFromCode(string code) => Encoding.UTF8.GetBytes(code.Trim());
}

/// <summary>The hosts file (K4, K5, K7). Spark Guard only ever owns one marked block, so it never touches the rest of the file.</summary>
public static class Hosts
{
    public const string Begin = "# >>> Spark Guard (do not edit: it is put back within a second) >>>";
    public const string End = "# <<< Spark Guard <<<";

    public static bool IsPlainDomain(string d) =>
        d.Length is > 3 and < 254 && d.All(c => char.IsAsciiLetterOrDigit(c) || c is '.' or '-') && d.Contains('.') && !d.StartsWith('.') && !d.EndsWith('.') && !d.Contains("..");

    /// <summary>K7: the root, www and mobile forms of each domain (a hosts file has no wildcards), pointing nowhere.</summary>
    public static IEnumerable<string> Lines(IEnumerable<string> domains) =>
        domains.OrderBy(d => d, StringComparer.Ordinal).SelectMany(d => new[] { d, "www." + d, "m." + d }).Distinct().Select(h => $"0.0.0.0 {h}");

    /// <summary>The file with Spark's block removed. Everything the person or Windows wrote is kept exactly as it was.</summary>
    public static string Strip(string content)
    {
        var nl = content.Contains("\r\n") ? "\r\n" : "\n";
        var lines = content.Replace("\r\n", "\n").Split('\n').ToList();
        var output = new List<string>(); var inside = false;
        foreach (var line in lines)
        {
            if (line.StartsWith("# >>> Spark Guard")) { inside = true; continue; }
            if (inside) { if (line.StartsWith("# <<< Spark Guard")) inside = false; continue; }
            output.Add(line);
        }
        while (output.Count > 0 && output[^1].Length == 0 && output.Count > 1 && output[^2].Length == 0) output.RemoveAt(output.Count - 1);
        return string.Join(nl, output);
    }

    /// <summary>What the file must say now: the person's own lines, plus Spark's block when <paramref name="blocked"/>.</summary>
    public static string Desired(string current, IEnumerable<string> domains, bool blocked)
    {
        var nl = current.Contains("\r\n") || !current.Contains('\n') ? "\r\n" : "\n";
        var clean = Strip(current).TrimEnd('\r', '\n');
        if (!blocked) return clean + nl;
        var block = new List<string> { Begin }; block.AddRange(Lines(domains)); block.Add(End);
        return clean + nl + string.Join(nl, block) + nl;
    }
}

/// <summary>A line in the log kept for 90 days (ST4). Nothing here is sent anywhere.</summary>
public sealed class GuardLog
{
    private readonly string _path;
    private readonly object _lock = new();
    public static readonly TimeSpan Keep = TimeSpan.FromDays(90);
    public GuardLog(string path) { _path = path; Directory.CreateDirectory(Path.GetDirectoryName(path)!); }

    public void Write(DateTime nowUtc, string kind, string detail)
    {
        lock (_lock) File.AppendAllText(_path, $"{nowUtc:O}\t{kind}\t{detail.Replace('\t', ' ').Replace('\n', ' ')}{Environment.NewLine}");
    }

    public IReadOnlyList<(DateTime At, string Kind, string Detail)> Read()
    {
        if (!File.Exists(_path)) return Array.Empty<(DateTime, string, string)>();
        lock (_lock)
            return File.ReadAllLines(_path).Select(l => l.Split('\t', 3)).Where(p => p.Length == 3 && DateTime.TryParse(p[0], null, System.Globalization.DateTimeStyles.RoundtripKind, out _))
                .Select(p => (DateTime.Parse(p[0], null, System.Globalization.DateTimeStyles.RoundtripKind), p[1], p[2])).ToList();
    }

    /// <summary>Drops lines older than <see cref="Keep"/>.</summary>
    public int Trim(DateTime nowUtc)
    {
        lock (_lock)
        {
            var all = Read(); var keep = all.Where(e => nowUtc - e.At <= Keep).ToList();
            File.WriteAllLines(_path, keep.Select(e => $"{e.At:O}\t{e.Kind}\t{e.Detail}"));
            return all.Count - keep.Count;
        }
    }
}
