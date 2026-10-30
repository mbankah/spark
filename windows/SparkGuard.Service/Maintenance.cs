using System.Security.AccessControl;
using SparkGuard;

namespace SparkGuard.Service;

/// <summary>Jobs the installer runs through the command line: --clean-hosts when Spark Guard is uninstalled.</summary>
public static class Maintenance
{
    /// <summary>
    /// Takes Spark's block out of the hosts file and gives the file its normal permissions back, so an uninstall leaves the PC as it found it. Everything the person or
    /// Windows wrote is kept. Returns true when the file had a Spark block.
    /// </summary>
    public static bool CleanHosts(string hostsPath, bool resetPermissions)
    {
        if (!File.Exists(hostsPath)) return false;
        if (resetPermissions) RestoreInheritance(hostsPath);
        var text = File.ReadAllText(hostsPath);
        var had = text.Contains("# >>> Spark Guard");
        if (had) File.WriteAllText(hostsPath, Hosts.Desired(text, Array.Empty<string>(), false));
        return had;
    }

    private static void RestoreInheritance(string path)
    {
        var info = new FileInfo(path); var acl = info.GetAccessControl();
        acl.SetAccessRuleProtection(false, false);                  // inherit from the folder again, as Windows set it up
        info.SetAccessControl(acl);
    }
}
