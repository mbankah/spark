using System.Net;
using System.Net.Sockets;
using System.Text;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using SparkGuard;
using SparkGuard.Service;
using Xunit;

namespace SparkGuard.Tests;

/// <summary>The read-only dashboard (A1, A5, A6, B2, G8, ST6): the phone sends a signed snapshot, the PC shows it on this PC only.</summary>
public class DashboardTests : IAsyncLifetime
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "sparkguard-dash-" + Guid.NewGuid().ToString("N"));
    private int _port; private IHost _host = null!; private byte[] _key = null!;
    private readonly HttpClient _http = new();

    private static int FreePort() { var l = new TcpListener(IPAddress.Loopback, 0); l.Start(); var p = ((IPEndPoint)l.LocalEndpoint).Port; l.Stop(); return p; }
    private async Task<IHost> Start() { var h = Host.CreateDefaultBuilder().ConfigureServices(s => { s.AddSingleton(new GuardPaths(Path.Combine(_dir, "data"), Path.Combine(_dir, "hosts"), _port, Lock: false)); s.AddHostedService<Worker>(); }).Build(); await h.StartAsync(); return h; }

    public async Task InitializeAsync()
    {
        Directory.CreateDirectory(_dir); File.WriteAllText(Path.Combine(_dir, "hosts"), "127.0.0.1 localhost\r\n"); _port = FreePort();
        _host = await Start(); _key = StateVerifier.KeyFromCode(File.ReadAllText(Path.Combine(_dir, "data", "pairing.code"))); await Task.Delay(300);
    }
    public async Task DisposeAsync() { await _host.StopAsync(); _http.Dispose(); try { Directory.Delete(_dir, true); } catch { } }

    private static byte[] Snap(DateTime issued, string buffer = "Buffer +24.0h") => Encoding.UTF8.GetBytes($"{{\"v\":1,\"issuedAtUtc\":\"{issued:O}\",\"buffer\":\"{buffer}\",\"until\":\"Wed 4 Nov 17:00\",\"negative\":false,\"working\":[\"Essay 6.3h\"],\"now\":\"Lecture\",\"next\":[\"12:30 Lunch\"],\"money\":\"£12.50\",\"shopping\":12,\"week\":[\"Mon 2: 3 from 11:00\"]}}");
    private async Task<HttpStatusCode> Post(byte[] body, string? sig = null)
    {
        var req = new HttpRequestMessage(HttpMethod.Post, $"http://127.0.0.1:{_port}/snapshot") { Content = new ByteArrayContent(body) };
        req.Headers.Add("X-Spark-Signature", sig ?? StateVerifier.Sign(_key, body));
        return (await _http.SendAsync(req)).StatusCode;
    }

    [Fact] public async Task A6_the_pc_shows_what_the_phone_sent()
    {
        Assert.Equal(HttpStatusCode.OK, await Post(Snap(DateTime.UtcNow)));
        var j = await _http.GetStringAsync($"http://127.0.0.1:{_port}/snapshot.json");
        Assert.Contains("Buffer +24.0h", j); Assert.Contains("Essay 6.3h", j);
    }

    [Fact] public async Task A5_before_anything_arrives_the_snapshot_is_not_found_rather_than_invented()
        => Assert.Equal(HttpStatusCode.NotFound, (await _http.GetAsync($"http://127.0.0.1:{_port}/snapshot.json")).StatusCode);

    [Fact] public async Task A5_the_dashboard_cannot_change_anything_only_the_signed_phone_can()
    {
        Assert.Equal(HttpStatusCode.BadRequest, await Post(Snap(DateTime.UtcNow), new string('1', 64)));
        Assert.Equal(HttpStatusCode.NotFound, (await _http.GetAsync($"http://127.0.0.1:{_port}/snapshot.json")).StatusCode);
        var put = await _http.PutAsync($"http://127.0.0.1:{_port}/snapshot.json", new StringContent("{}")); Assert.Equal(HttpStatusCode.NotFound, put.StatusCode);
    }

    [Fact] public async Task K5_an_old_or_repeated_snapshot_cannot_replace_a_newer_one()
    {
        var now = DateTime.UtcNow;
        Assert.Equal(HttpStatusCode.OK, await Post(Snap(now, "Buffer +5.0h")));
        Assert.Equal(HttpStatusCode.Conflict, await Post(Snap(now.AddMinutes(-3), "Buffer +99.0h")));
        Assert.Contains("+5.0h", await _http.GetStringAsync($"http://127.0.0.1:{_port}/snapshot.json"));
    }

    [Fact] public async Task A6_the_dashboard_page_is_served_on_this_pc()
    {
        var page = await _http.GetStringAsync($"http://127.0.0.1:{_port}/");
        Assert.Contains("Spark, read from your phone", page); Assert.Contains("/snapshot.json", page);
    }

    [Fact] public async Task K6_a_restart_keeps_the_last_snapshot()
    {
        await Post(Snap(DateTime.UtcNow)); await _host.StopAsync(); _host = await Start(); await Task.Delay(300);
        Assert.Contains("Buffer +24.0h", await _http.GetStringAsync($"http://127.0.0.1:{_port}/snapshot.json"));
    }

    [Fact] public void A4_the_snapshot_verdicts_are_the_states()
    {
        var k = StateVerifier.KeyFromCode("c"); var b = Snap(DateTime.UtcNow);
        Assert.Equal(Verdict.Accepted, StateVerifier.VerifySnapshot(k, b, StateVerifier.Sign(k, b), DateTime.UtcNow, null, out _));
        var future = Snap(DateTime.UtcNow.AddHours(2)); Assert.Equal(Verdict.FromTheFuture, StateVerifier.VerifySnapshot(k, future, StateVerifier.Sign(k, future), DateTime.UtcNow, null, out _));
        var bad = Encoding.UTF8.GetBytes("nope"); Assert.Equal(Verdict.Malformed, StateVerifier.VerifySnapshot(k, bad, StateVerifier.Sign(k, bad), DateTime.UtcNow, null, out _));
    }
}
