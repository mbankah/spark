using System.Text;
using SparkGuard;
using Xunit;

namespace SparkGuard.Tests;

public class GuardTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "sparkguard-" + Guid.NewGuid().ToString("N"));
    private static readonly DateTime T0 = new(2026, 11, 2, 10, 0, 0, DateTimeKind.Utc);
    private readonly byte[] _key = StateVerifier.KeyFromCode("pairing-code-for-tests");

    public GuardTests() => Directory.CreateDirectory(_dir);
    public void Dispose() { try { Directory.Delete(_dir, true); } catch { } }

    private static byte[] Body(DateTime issued, string domains = "\"youtube.com\"", string windows = "{\"start\":\"2026-11-02T09:00:00Z\",\"end\":\"2026-11-02T12:00:00Z\",\"why\":\"Work block\"}") =>
        Encoding.UTF8.GetBytes($"{{\"v\":1,\"issuedAtUtc\":\"{issued:O}\",\"domains\":[{domains}],\"windows\":[{windows}]}}");

    private (Verdict, GuardState?) Check(byte[] body, string? sig = null, DateTime? now = null, DateTime? last = null) =>
        StateVerifier.Verify(_key, body, sig ?? StateVerifier.Sign(_key, body), now ?? T0, last);

    // ---- A4, A6: the state from the phone ----
    [Fact] public void A4_a_state_signed_with_the_pairing_key_is_accepted() { var (v, s) = Check(Body(T0)); Assert.Equal(Verdict.Accepted, v); Assert.Single(s!.Windows); Assert.Equal("youtube.com", s.Domains[0]); }
    [Fact] public void A4_a_state_with_the_wrong_signature_is_refused() => Assert.Equal(Verdict.BadSignature, Check(Body(T0), new string('a', 64)).Item1);
    [Fact] public void A4_a_state_changed_after_signing_is_refused() { var b = Body(T0); var sig = StateVerifier.Sign(_key, b); Assert.Equal(Verdict.BadSignature, Check(Body(T0, "\"example.com\""), sig).Item1); }
    [Fact] public void A4_a_missing_or_rubbish_signature_is_refused() { Assert.Equal(Verdict.BadSignature, Check(Body(T0), "").Item1); Assert.Equal(Verdict.BadSignature, Check(Body(T0), "zz").Item1); Assert.Equal(Verdict.BadSignature, StateVerifier.Verify(_key, Body(T0), null, T0, null).Item1); }
    [Fact] public void A4_a_state_signed_with_another_key_is_refused() { var b = Body(T0); Assert.Equal(Verdict.BadSignature, Check(b, StateVerifier.Sign(StateVerifier.KeyFromCode("someone-else"), b)).Item1); }
    [Fact] public void K5_an_old_state_cannot_be_replayed_to_switch_blocking_off() => Assert.Equal(Verdict.Stale, Check(Body(T0.AddMinutes(-5)), last: T0).Item1);
    [Fact] public void K5_the_same_state_twice_is_refused_the_second_time() => Assert.Equal(Verdict.Stale, Check(Body(T0), last: T0).Item1);
    [Fact] public void K5_a_state_from_far_in_the_future_is_refused() => Assert.Equal(Verdict.FromTheFuture, Check(Body(T0.AddHours(3))).Item1);
    [Fact] public void A4_a_body_that_is_not_a_state_is_malformed() { var b = Encoding.UTF8.GetBytes("not json"); Assert.Equal(Verdict.Malformed, Check(b).Item1); }
    [Fact] public void K7_a_domain_that_is_not_a_plain_name_is_refused() { Assert.Equal(Verdict.Malformed, Check(Body(T0, "\"1.2.3.4 evil.com\"")).Item1); Assert.Equal(Verdict.Malformed, Check(Body(T0, "\"..com\"")).Item1); }
    [Fact] public void A4_a_state_of_an_unknown_version_is_unsupported() { var b = Encoding.UTF8.GetBytes($"{{\"v\":2,\"issuedAtUtc\":\"{T0:O}\",\"domains\":[],\"windows\":[]}}"); Assert.Equal(Verdict.Unsupported, Check(b).Item1); }
    [Fact] public void A4_the_pairing_code_is_long_random_and_url_safe() { var a = StateVerifier.NewPairingCode(); Assert.True(a.Length >= 30); Assert.DoesNotContain('+', a); Assert.DoesNotContain('/', a); Assert.DoesNotContain('=', a); Assert.NotEqual(a, StateVerifier.NewPairingCode()); }

    /// <summary>The same signature the phone's tests compute (docs/PC_PROTOCOL.md), so both sides agree on the format.</summary>
    [Fact] public void A4_the_shared_test_vector_matches_the_phone()
    {
        var key = Encoding.UTF8.GetBytes("spark-test-key");
        var body = Encoding.UTF8.GetBytes("{\"v\":1,\"issuedAtUtc\":\"2026-11-02T10:00:00Z\",\"domains\":[\"youtube.com\"],\"windows\":[]}");
        Assert.Equal(File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "vector.sig")).Trim(), StateVerifier.Sign(key, body));
    }

    // ---- K4, K5, K7: the hosts file ----
    private const string Original = "# Copyright\r\n127.0.0.1 localhost\r\n::1 localhost\r\n10.0.0.5 nas.home\r\n";

    [Fact] public void K7_each_domain_gets_its_root_www_and_mobile_lines() => Assert.Equal(new[] { "0.0.0.0 youtu.be", "0.0.0.0 www.youtu.be", "0.0.0.0 m.youtu.be", "0.0.0.0 youtube.com", "0.0.0.0 www.youtube.com", "0.0.0.0 m.youtube.com" }, Hosts.Lines(new[] { "youtube.com", "youtu.be" }).ToArray());
    [Fact] public void K4_blocking_adds_one_marked_block_and_keeps_the_persons_lines()
    {
        var w = Hosts.Desired(Original, new[] { "youtube.com" }, true);
        Assert.StartsWith(Original.TrimEnd(), w); Assert.Contains(Hosts.Begin, w); Assert.Contains("0.0.0.0 m.youtube.com", w); Assert.Contains(Hosts.End, w);
    }
    [Fact] public void K4_when_not_blocked_the_file_is_the_persons_own_again() => Assert.Equal(Original, Hosts.Desired(Hosts.Desired(Original, new[] { "youtube.com" }, true), new[] { "youtube.com" }, false));
    [Fact] public void K4_blocking_twice_does_not_stack_blocks() { var once = Hosts.Desired(Original, new[] { "youtube.com" }, true); Assert.Equal(once, Hosts.Desired(once, new[] { "youtube.com" }, true)); }
    [Fact] public void K4_a_changed_list_replaces_the_block() { var a = Hosts.Desired(Original, new[] { "youtube.com" }, true); var b = Hosts.Desired(a, new[] { "reddit.com" }, true); Assert.DoesNotContain("youtube", b); Assert.Contains("reddit.com", b); }
    [Fact] public void K4_unix_line_endings_are_kept() { var w = Hosts.Desired("127.0.0.1 localhost\n", new[] { "youtube.com" }, true); Assert.DoesNotContain("\r\n", w); }

    private Enforcer Make(Func<DateTime> clock, string? hosts = null) { var h = hosts ?? Path.Combine(_dir, "hosts"); if (!File.Exists(h)) File.WriteAllText(h, Original); return new Enforcer(h, Path.Combine(_dir, "state.json"), new GuardLog(Path.Combine(_dir, "log.txt")), clock); }
    private static GuardState St(DateTime issued) => GuardState.Parse(Body(issued))!;
    private string Hostfile => File.ReadAllText(Path.Combine(_dir, "hosts"));

    [Fact] public void K4_inside_a_window_the_file_is_written_and_outside_it_is_restored()
    {
        var now = T0; var e = Make(() => now); e.Accept(St(T0), Body(T0));
        Assert.True(e.Tick()); Assert.Contains("0.0.0.0 youtube.com", Hostfile);
        now = new DateTime(2026, 11, 2, 12, 30, 0, DateTimeKind.Utc);
        Assert.True(e.Tick()); Assert.Equal(Original, Hostfile);
    }
    [Fact] public void K5_a_change_is_put_back_on_the_next_second_and_logged_as_an_attempt()
    {
        var now = T0; var e = Make(() => now); e.Accept(St(T0), Body(T0)); e.Tick();
        File.WriteAllText(Path.Combine(_dir, "hosts"), Original);               // someone removes the block
        Assert.True(e.Tick()); Assert.Contains("0.0.0.0 youtube.com", Hostfile);
        Assert.Contains(new GuardLog(Path.Combine(_dir, "log.txt")).Read(), x => x.Kind == "attempt");
    }
    [Fact] public void K5_nothing_to_do_when_the_file_is_already_right() { var e = Make(() => T0); e.Accept(St(T0), Body(T0)); e.Tick(); Assert.False(e.Tick()); }
    [Fact] public void K6_a_restart_enforces_the_last_state_at_once_from_disk()
    {
        var first = Make(() => T0); first.Accept(St(T0), Body(T0));
        File.WriteAllText(Path.Combine(_dir, "hosts"), Original);
        var second = new Enforcer(Path.Combine(_dir, "hosts"), Path.Combine(_dir, "state.json"), new GuardLog(Path.Combine(_dir, "log2.txt")), () => T0);
        Assert.True(second.Tick()); Assert.Contains("0.0.0.0 youtube.com", Hostfile);
    }
    [Fact] public void X7_with_no_state_the_hosts_file_is_never_touched() { var e = Make(() => T0); Assert.False(e.Tick()); Assert.Equal(Original, Hostfile); }
    [Fact] public void X7_a_missing_hosts_file_is_created_not_a_crash() { var e = Make(() => T0, Path.Combine(_dir, "nohosts")); File.Delete(Path.Combine(_dir, "nohosts")); e.Accept(St(T0), Body(T0)); Assert.True(e.Tick()); Assert.Contains("youtube.com", File.ReadAllText(Path.Combine(_dir, "nohosts"))); }
    [Fact] public void K4_the_edges_of_a_window_are_start_inclusive_and_end_exclusive()
    {
        var s = St(T0);
        Assert.NotNull(s.ActiveAt(new DateTime(2026, 11, 2, 9, 0, 0, DateTimeKind.Utc))); Assert.Null(s.ActiveAt(new DateTime(2026, 11, 2, 12, 0, 0, DateTimeKind.Utc))); Assert.Null(s.ActiveAt(new DateTime(2026, 11, 2, 8, 59, 59, DateTimeKind.Utc)));
    }

    // ---- ST4: the log ----
    [Fact] public void ST4_the_log_keeps_ninety_days_and_drops_older_lines()
    {
        var log = new GuardLog(Path.Combine(_dir, "l.txt")); log.Write(T0.AddDays(-100), "attempt", "old"); log.Write(T0.AddDays(-10), "attempt", "recent");
        Assert.Equal(1, log.Trim(T0)); Assert.Single(log.Read()); Assert.Equal("recent", log.Read()[0].Detail);
    }
    [Fact] public void ST4_a_log_line_cannot_break_the_format() { var log = new GuardLog(Path.Combine(_dir, "l2.txt")); log.Write(T0, "x", "a\tb\nc"); Assert.Single(log.Read()); }
}
