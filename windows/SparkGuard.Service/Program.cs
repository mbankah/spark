using SparkGuard;
using SparkGuard.Service;

// Spark Guard: the Windows part of Spark (A2, K4, K5, K6). It writes the hosts file during work blocks, puts it back if it is changed, and listens for the phone.
// Run with --show-pairing to print the code the phone needs (administrator only, because the file is only readable by administrators).
string Arg(string name) { var i = Array.IndexOf(args, name); return i >= 0 && i + 1 < args.Length ? args[i + 1] : ""; }
var data = Arg("--data") is { Length: > 0 } d ? d : Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonApplicationData), "SparkGuard");

if (args.Contains("--clean-hosts"))                  // run by the uninstaller
{
    var hosts = Arg("--hosts") is { Length: > 0 } hp ? hp : Path.Combine(Environment.SystemDirectory, "drivers", "etc", "hosts");
    Console.WriteLine(Maintenance.CleanHosts(hosts, resetPermissions: !args.Contains("--no-lock")) ? "Removed the Spark block from the hosts file." : "The hosts file had no Spark block.");
    return;
}

if (args.Contains("--show-pairing"))
{
    var path = Path.Combine(data, "pairing.code");
    Console.WriteLine(File.Exists(path) ? File.ReadAllText(path).Trim() : "Spark Guard has not started yet. Start the service first.");
    return;
}

var builder = Host.CreateApplicationBuilder(args);
builder.Services.AddWindowsService(o => o.ServiceName = "SparkGuard");
// --hosts, --port and --no-lock exist only so the whole service can be tried against a temporary file (tools/pc-link-e2e.sh). The installed service uses none of them.
builder.Services.AddSingleton(new GuardPaths(data, Arg("--hosts") is { Length: > 0 } h ? h : Path.Combine(Environment.SystemDirectory, "drivers", "etc", "hosts"),
    int.TryParse(Arg("--port"), out var port) ? port : 47831, !args.Contains("--no-lock")));
builder.Services.AddHostedService<Worker>();
builder.Build().Run();
