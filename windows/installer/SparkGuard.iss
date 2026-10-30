; Spark Guard installer (A2, K4, K5, K6). Built by windows/build-installer.ps1 with Inno Setup. It needs administrator rights because it installs a Windows service.
; What it does, in order: copies the program, installs the service to start by itself and restart if it dies, lets the phone reach it on PRIVATE networks only,
; starts it, and shows the address and pairing code to type into the phone. Uninstalling stops the service, takes Spark's lines out of the hosts file and gives the
; file its normal permissions back.

#define AppName "Spark Guard"
#define AppVersion "1.0.0"
#define ServiceName "SparkGuard"
#define Port "47831"

[Setup]
AppId={{6D1A4E2B-5C3F-4B7A-9E10-5A9B7C0D2F41}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher=Spark
DefaultDirName={autopf}\Spark Guard
DisableProgramGroupPage=yes
PrivilegesRequired=admin
OutputDir=..\dist
OutputBaseFilename=SparkGuard-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayName={#AppName}
CloseApplications=no

[Messages]
WelcomeLabel1=Spark Guard
WelcomeLabel2=This blocks the sites of the apps you blocked in Spark on this PC, during your work blocks, so it matches your phone.%n%nIt installs a Windows service that edits the hosts file, puts the edit back if anyone changes it, and listens for your phone on your home network only.%n%nIt does not read your browsing, send anything to the internet, or run anything else.

[Files]
Source: "..\publish\SparkGuard.Service.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\extension\manifest.json"; DestDir: "{app}\extension"; Flags: ignoreversion
Source: "..\extension\background.js"; DestDir: "{app}\extension"; Flags: ignoreversion
Source: "..\extension\rules.js"; DestDir: "{app}\extension"; Flags: ignoreversion
Source: "..\extension\blocked.html"; DestDir: "{app}\extension"; Flags: ignoreversion
Source: "README.txt"; DestDir: "{app}"; Flags: ignoreversion isreadme

[Icons]
Name: "{autoprograms}\Spark Guard\Install the Spark browser extension"; Filename: "notepad.exe"; Parameters: """{app}\README.txt"""
Name: "{autoprograms}\Spark Guard\Show the pairing code"; Filename: "{cmd}"; Parameters: "/k ""{app}\SparkGuard.Service.exe"" --show-pairing"; Flags: runminimized

[Run]
Filename: "{sys}\sc.exe"; Parameters: "create {#ServiceName} binPath= ""{app}\SparkGuard.Service.exe"" start= auto DisplayName= ""Spark Guard"""; Flags: runhidden; StatusMsg: "Installing the service..."
Filename: "{sys}\sc.exe"; Parameters: "description {#ServiceName} ""Blocks the sites of the apps you blocked in Spark during work blocks, as the phone says."""; Flags: runhidden
Filename: "{sys}\sc.exe"; Parameters: "failure {#ServiceName} reset= 0 actions= restart/1000/restart/1000/restart/1000"; Flags: runhidden; StatusMsg: "Setting it to restart by itself..."
Filename: "{sys}\sc.exe"; Parameters: "failureflag {#ServiceName} 1"; Flags: runhidden
Filename: "{sys}\netsh.exe"; Parameters: "http add urlacl url=http://+:{#Port}/ user=""NT AUTHORITY\SYSTEM"""; Flags: runhidden; StatusMsg: "Letting the phone reach it..."
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall add rule name=""Spark Guard"" dir=in action=allow protocol=TCP localport={#Port} profile=private program=""{app}\SparkGuard.Service.exe"""; Flags: runhidden
Filename: "{sys}\sc.exe"; Parameters: "start {#ServiceName}"; Flags: runhidden; StatusMsg: "Starting Spark Guard..."

[UninstallRun]
Filename: "{sys}\sc.exe"; Parameters: "stop {#ServiceName}"; Flags: runhidden; RunOnceId: "StopService"
Filename: "{app}\SparkGuard.Service.exe"; Parameters: "--clean-hosts"; Flags: runhidden; RunOnceId: "CleanHosts"
Filename: "{sys}\sc.exe"; Parameters: "delete {#ServiceName}"; Flags: runhidden; RunOnceId: "DeleteService"
Filename: "{sys}\netsh.exe"; Parameters: "http delete urlacl url=http://+:{#Port}/"; Flags: runhidden; RunOnceId: "DeleteUrlAcl"
Filename: "{sys}\netsh.exe"; Parameters: "advfirewall firewall delete rule name=""Spark Guard"""; Flags: runhidden; RunOnceId: "DeleteFirewall"

[Code]
var
  PairPage: TOutputMsgMemoWizardPage;

function ReadFirstLine(const Path: String): String;
var
  Lines: TArrayOfString;
begin
  Result := '';
  if LoadStringsFromFile(Path, Lines) and (GetArrayLength(Lines) > 0) then
    Result := Trim(Lines[0]);
end;

function PcAddress(): String;
var
  Tmp, Ps: String;
  Code: Integer;
begin
  Tmp := ExpandConstant('{tmp}\ip.txt');
  Ps := '-NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -match ''^(192\.168|10\.|172\.(1[6-9]|2[0-9]|3[01]))'' -and $_.InterfaceAlias -notmatch ''vEthernet|Loopback|VMware|VirtualBox'' } | Select-Object -First 1 -ExpandProperty IPAddress) | Out-File -Encoding ascii ''' + Tmp + '''"';
  Exec(ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe'), Ps, '', SW_HIDE, ewWaitUntilTerminated, Code);
  Result := ReadFirstLine(Tmp);
  if Result = '' then Result := GetComputerNameString();
end;

procedure InitializeWizard();
begin
  PairPage := CreateOutputMsgMemoPage(wpInstalling, 'Link your phone', 'Type these two things into Spark on your phone.',
    'Setup, Link a Windows PC.', 'Waiting for Spark Guard to start...');
end;

procedure CurPageChanged(CurPageID: Integer);
var
  Code, Addr: String;
  I: Integer;
begin
  if CurPageID = PairPage.ID then
  begin
    Code := '';
    for I := 1 to 20 do
    begin
      Code := ReadFirstLine(ExpandConstant('{commonappdata}\SparkGuard\pairing.code'));
      if Code <> '' then Break;
      Sleep(500);
    end;
    Addr := PcAddress();
    if Code = '' then
      PairPage.RichEditViewer.Text := 'Spark Guard has not started. Open Services, start "Spark Guard", then run "Show the pairing code" from the Start menu.'
    else
      PairPage.RichEditViewer.Text := 'PC address:   ' + Addr + ':{#Port}' + #13#10#13#10 + 'Pairing code: ' + Code + #13#10#13#10 +
        'Both phone and PC must be on the same home network. The code is also available later from the Start menu: Spark Guard, Show the pairing code.' + #13#10#13#10 +
        'To block the same sites inside the browser as well, load the extension: see README.txt.';
  end;
end;
