using System.Net;
using System.Net.Sockets;
using System.Text;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using SparkGuard;
using SparkGuard.Service;
using Xunit;

namespace SparkGuard.Tests;

/// <summary>The whole service in this process, on a temporary hosts file and a free port: the real hosts file is never touched (K4, K5, K6, A4).</summary>
public class ServiceTests : IAsyncLifetime
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "sparkguard-svc-" + Guid.NewGuid().ToString("N"));
    private string Hosts => Path.Combine(_dir, "hosts");
    private int _port; private IHost _host = null!; private byte[] _key = null!;
    private readonly HttpClient _http = new();
    private const string Original = "127.0.0.1 localhost\r\n";

    private static int FreePort() { var l = new TcpListener(IPAddress.Loopback, 0); l.Start(); var p = ((IPEndPoint)l.LocalEndpoint).Port; l.Stop(); return p; }
    private async Task<IHost> Start() { var h = Host.CreateDefaultBuilder().ConfigureServices(s => { s.AddSingleton(new GuardPaths(Path.Combine(_dir, "data"), Hosts, _port, Lock: false)); s.AddHostedService<Worker>(); }).Build(); await h.StartAsync(); return h; }

    public async Task InitializeAsync()
    {
        Directory.CreateDirectory(_dir); File.WriteAllText(Hosts, Original); _port = FreePort();
        _host = await Start();
        _key = StateVerifier.KeyFromCode(File.ReadAllText(Path.Combine(_dir, "data", "pairing.code")));
        await Task.Delay(300);
    }
    public async Task DisposeAsync() { await _host.StopAsync(); _http.Dispose(); try { Directory.Delete(_dir, true); } catch { } }

    private static string Window(DateTime from, DateTime to) => $"{{\"start\":\"{from:O}\",\"end\":\"{to:O}\",\"why\":\"Work block\"}}";
    private byte[] Body(DateTime issued, DateTime from, DateTime to) => Encoding.UTF8.GetBytes($"{{\"v\":1,\"issuedAtUtc\":\"{issued:O}\",\"domains\":[\"youtube.com\"],\"windows\":[{Window(from, to)}]}}");
    private async Task<HttpStatusCode> Post(byte[] body, string? sig = null)
    {
        var req = new HttpRequestMessage(HttpMethod.Post, $"http://127.0.0.1:{_port}/state") { Content = new ByteArrayContent(body) };
        req.Headers.Add("X-Spark-Signature", sig ?? StateVerifier.Sign(_key, body));
        return (await _http.SendAsync(req)).StatusCode;
    }
    private static async Task<bool> Eventually(Func<bool> cond, int ms = 5000) { var end = DateTime.UtcNow.AddMilliseconds(ms); while (DateTime.UtcNow < end) { if (cond()) return true; await Task.Delay(100); } return cond(); }

    [Fact] public async Task A4_K4_a_signed_state_makes_the_service_write_the_block_within_seconds()
    {
        var now = DateTime.UtcNow;
        Assert.Equal(HttpStatusCode.OK, await Post(Body(now, now.AddMinutes(-1), now.AddHours(1))));
        Assert.True(await Eventually(() => File.ReadAllText(Hosts).Contains("0.0.0.0 youtube.com")), "the block was not written");
        Assert.Contains("127.0.0.1 localhost", File.ReadAllText(Hosts));
    }

    [Fact] public async Task K5_a_change_to_the_hosts_file_is_put_back_within_about_a_second_and_logged()
    {
        var now = DateTime.UtcNow; await Post(Body(now, now.AddMinutes(-1), now.AddHours(1)));
        Assert.True(await Eventually(() => File.ReadAllText(Hosts).Contains("youtube")));
        File.WriteAllText(Hosts, Original);
        Assert.True(await Eventually(() => File.ReadAllText(Hosts).Contains("0.0.0.0 youtube.com"), 3000), "it was not put back");
        Assert.True(await Eventually(() => File.Exists(Path.Combine(_dir, "data", "guard.log")) && File.ReadAllText(Path.Combine(_dir, "data", "guard.log")).Contains("attempt")));
    }

    [Fact] public async Task A4_a_state_with_a_wrong_signature_or_an_old_one_is_refused_and_changes_nothing()
    {
        var now = DateTime.UtcNow; var b = Body(now, now.AddMinutes(-1), now.AddHours(1));
        Assert.Equal(HttpStatusCode.BadRequest, await Post(b, new string('0', 64)));
        Assert.Equal(HttpStatusCode.OK, await Post(b));
        Assert.Equal(HttpStatusCode.Conflict, await Post(b));                                   // the same one again is a replay
        await Task.Delay(1500); Assert.Contains("refused", File.ReadAllText(Path.Combine(_dir, "data", "guard.log")));
    }

    [Fact] public async Task K8_the_browser_extension_gets_the_domains_only_while_they_are_blocked()
    {
        var now = DateTime.UtcNow; await Post(Body(now, now.AddMinutes(-1), now.AddHours(1)));
        var on = await _http.GetStringAsync($"http://127.0.0.1:{_port}/state");
        Assert.Contains("\"blocked\":true", on); Assert.Contains("youtube.com", on); Assert.DoesNotContain("pairing", on); Assert.Contains("\"until\":", on);
    }

    [Fact] public async Task K8_outside_a_window_the_extension_is_told_nothing_is_blocked()
    {
        var now = DateTime.UtcNow; await Post(Body(now, now.AddHours(2), now.AddHours(3)));
        var off = await _http.GetStringAsync($"http://127.0.0.1:{_port}/state");
        Assert.Contains("\"blocked\":false", off); Assert.DoesNotContain("youtube", off);
    }

    [Fact] public async Task K6_a_restart_enforces_the_saved_state_and_logs_that_it_started_again()
    {
        var now = DateTime.UtcNow; await Post(Body(now, now.AddMinutes(-1), now.AddHours(1)));
        Assert.True(await Eventually(() => File.ReadAllText(Hosts).Contains("youtube")));
        await _host.StopAsync(); File.WriteAllText(Hosts, Original);                       // while it was stopped, the file was changed
        _host = await Start();
        Assert.True(await Eventually(() => File.ReadAllText(Hosts).Contains("0.0.0.0 youtube.com"), 4000), "the saved state was not enforced after the restart");
        var log = File.ReadAllText(Path.Combine(_dir, "data", "guard.log"));
        Assert.Contains("stopped", log); Assert.Equal(2, log.Split("\tstarted\t").Length - 1);
    }

    [Fact] public async Task X7_a_body_that_is_too_large_is_refused() => Assert.Equal(HttpStatusCode.RequestEntityTooLarge, await Post(new byte[70_000]));
}
