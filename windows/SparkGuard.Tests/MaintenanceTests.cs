using SparkGuard;
using SparkGuard.Service;
using Xunit;

namespace SparkGuard.Tests;

public class MaintenanceTests : IDisposable
{
    private readonly string _dir = Path.Combine(Path.GetTempPath(), "sparkguard-m-" + Guid.NewGuid().ToString("N"));
    public MaintenanceTests() => Directory.CreateDirectory(_dir);
    public void Dispose() { try { Directory.Delete(_dir, true); } catch { } }

    [Fact] public void K6_uninstalling_takes_spark_out_of_the_hosts_file_and_keeps_the_rest()
    {
        var original = "# my hosts\r\n127.0.0.1 localhost\r\n10.0.0.5 nas.home\r\n";
        var p = Path.Combine(_dir, "hosts"); File.WriteAllText(p, Hosts.Desired(original, new[] { "youtube.com" }, true));
        Assert.True(Maintenance.CleanHosts(p, resetPermissions: false));
        Assert.Equal(original, File.ReadAllText(p));
    }

    [Fact] public void K6_cleaning_a_file_with_no_spark_block_changes_nothing()
    {
        var p = Path.Combine(_dir, "hosts"); File.WriteAllText(p, "127.0.0.1 localhost\r\n");
        Assert.False(Maintenance.CleanHosts(p, false)); Assert.Equal("127.0.0.1 localhost\r\n", File.ReadAllText(p));
    }

    [Fact] public void X7_a_missing_hosts_file_is_not_an_error() => Assert.False(Maintenance.CleanHosts(Path.Combine(_dir, "nope"), false));

    [Fact] public void K5_the_permissions_the_service_set_are_given_back_on_uninstall()
    {
        var p = Path.Combine(_dir, "hosts"); File.WriteAllText(p, "127.0.0.1 localhost\r\n");
        var info = new FileInfo(p); var acl = info.GetAccessControl(); acl.SetAccessRuleProtection(true, true); info.SetAccessControl(acl);     // protected, as the service leaves it
        Assert.True(new FileInfo(p).GetAccessControl().AreAccessRulesProtected);
        Maintenance.CleanHosts(p, resetPermissions: true);
        Assert.False(new FileInfo(p).GetAccessControl().AreAccessRulesProtected);
    }
}
