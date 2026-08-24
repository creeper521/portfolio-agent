param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$CertificatePath,

    [Parameter(Mandatory = $true)]
    [AllowEmptyString()]
    [string]$CertificatePassword,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$CoordinationPort,

    [Parameter(Mandatory = $true)]
    [string]$ActiveSignalPath,

    [Parameter(Mandatory = $true)]
    [string]$ClosedSignalPath
)

$ErrorActionPreference = 'Stop'
$providerHost = 'open.bigmodel.cn'
$providerPort = 443
$providerPath = '/chat/completions'

if ($CoordinationPort -eq $providerPort) {
    throw 'CoordinationPort must not use the fixed Provider port.'
}

$certificate = [System.IO.Path]::GetFullPath($CertificatePath)
$activeSignal = [System.IO.Path]::GetFullPath($ActiveSignalPath)
$closedSignal = [System.IO.Path]::GetFullPath($ClosedSignalPath)
if ([System.StringComparer]::OrdinalIgnoreCase.Equals($activeSignal, $closedSignal)) {
    throw 'ActiveSignalPath and ClosedSignalPath must be different files.'
}
if ((Test-Path -LiteralPath $activeSignal) -or (Test-Path -LiteralPath $closedSignal)) {
    throw 'Coordination signal paths must not exist before fixture startup.'
}

# The implementation lives in-process so the HTTPS Provider listener and the
# loopback-only coordination listener can make progress concurrently. It never
# records request headers or bodies; the only observable evidence is state and
# a monotonic request count.
$fixtureSource = @'
using System;
using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;

public sealed class ProviderBodyStallHttpsFixture : IDisposable
{
    private const int MaximumHeaderBytes = 64 * 1024;
    private const int MaximumRequestBodyBytes = 2 * 1024 * 1024;
    private static readonly byte[] StallByte = Encoding.UTF8.GetBytes(" ");

    private readonly string expectedHost;
    private readonly string expectedPath;
    private readonly string activeSignalPath;
    private readonly string closedSignalPath;
    private readonly X509Certificate2 certificate;
    private readonly TcpListener providerListener;
    private readonly TcpListener coordinationListener;
    private readonly Thread coordinationThread;
    private volatile bool stopping;
    private volatile bool active;
    private volatile bool closed;
    private int providerRequestCount;

    public ProviderBodyStallHttpsFixture(
        string expectedHost,
        int providerPort,
        string expectedPath,
        int coordinationPort,
        string certificatePath,
        string certificatePassword,
        string activeSignalPath,
        string closedSignalPath)
    {
        this.expectedHost = expectedHost;
        this.expectedPath = expectedPath;
        this.activeSignalPath = activeSignalPath;
        this.closedSignalPath = closedSignalPath;
        certificate = (X509Certificate2)Activator.CreateInstance(
            typeof(X509Certificate2),
            new object[] {
                certificatePath,
                certificatePassword,
                X509KeyStorageFlags.UserKeySet | X509KeyStorageFlags.Exportable
            });
        if (!certificate.HasPrivateKey)
        {
            throw new InvalidOperationException("Fixture certificate must contain a private key.");
        }

        string certificateDnsName = certificate.GetNameInfo(X509NameType.DnsName, false);
        if (!CertificateMatchesHost(certificateDnsName, expectedHost))
        {
            throw new InvalidOperationException(
                "Fixture certificate DNS identity does not match the fixed Provider host.");
        }

        providerListener = new TcpListener(IPAddress.Loopback, providerPort);
        coordinationListener = new TcpListener(IPAddress.Loopback, coordinationPort);
        coordinationThread = new Thread(RunCoordinationLoop);
        coordinationThread.IsBackground = true;
        coordinationThread.Name = "provider-body-stall-coordination";
    }

    public void Run()
    {
        providerListener.Start();
        coordinationListener.Start();
        coordinationThread.Start();
        Console.WriteLine("BODY_STALL_FIXTURE_READY provider=" + expectedHost + ":443 coordination=loopback");

        while (!stopping)
        {
            TcpClient client = null;
            try
            {
                client = providerListener.AcceptTcpClient();
                HandleProvider(client);
            }
            catch (SocketException)
            {
                if (!stopping) throw;
            }
            finally
            {
                if (client != null) client.Dispose();
            }
        }
    }

    private void HandleProvider(TcpClient client)
    {
        client.NoDelay = true;
        client.ReceiveTimeout = 10000;
        client.SendTimeout = 10000;
        bool responseStarted = false;

        using (SslStream stream = new SslStream(client.GetStream(), false))
        {
            stream.AuthenticateAsServer(
                certificate,
                false,
                SslProtocols.Tls12,
                false);

            RequestMetadata request = ReadRequest(stream);
            if (!request.IsExpected(expectedHost, expectedPath))
            {
                WriteCompleteResponse(stream, "404 Not Found", "{}");
                return;
            }

            Interlocked.Increment(ref providerRequestCount);
            active = true;
            closed = false;

            byte[] partialBody = Encoding.UTF8.GetBytes(
                "{\"id\":\"fixture\",\"object\":\"chat.completion\",\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"");
            byte[] responseHeaders = Encoding.ASCII.GetBytes(
                "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=utf-8\r\n" +
                "Cache-Control: no-store\r\n" +
                "Content-Length: 1048576\r\n" +
                "Connection: keep-alive\r\n\r\n");
            stream.Write(responseHeaders, 0, responseHeaders.Length);
            stream.Write(partialBody, 0, partialBody.Length);
            stream.Flush();
            responseStarted = true;
            WriteSignal(activeSignalPath, "ACTIVE");

            try
            {
                while (!stopping)
                {
                    Thread.Sleep(50);
                    stream.Write(StallByte, 0, StallByte.Length);
                    stream.Flush();
                }
            }
            catch (IOException)
            {
                // Expected evidence: cancellation/timeout closed the client side.
            }
            catch (ObjectDisposedException)
            {
                // Expected when the caller stops the fixture.
            }
            finally
            {
                if (responseStarted && !stopping)
                {
                    closed = true;
                    WriteSignal(closedSignalPath, "CLOSED");
                }
            }
        }
    }

    private static RequestMetadata ReadRequest(SslStream stream)
    {
        MemoryStream header = new MemoryStream();
        int matched = 0;
        byte[] terminator = new byte[] { 13, 10, 13, 10 };
        while (header.Length < MaximumHeaderBytes)
        {
            int value = stream.ReadByte();
            if (value < 0) throw new IOException("Client disconnected before request headers completed.");
            header.WriteByte((byte)value);
            if (value == terminator[matched])
            {
                matched++;
                if (matched == terminator.Length) break;
            }
            else
            {
                matched = value == terminator[0] ? 1 : 0;
            }
        }
        if (matched != terminator.Length)
        {
            throw new InvalidDataException("Provider request headers exceed the fixture limit.");
        }

        string text = Encoding.ASCII.GetString(header.ToArray());
        string[] lines = text.Split(new[] { "\r\n" }, StringSplitOptions.None);
        string[] requestLine = lines[0].Split(' ');
        if (requestLine.Length < 2) return RequestMetadata.Invalid;

        string host = "";
        int contentLength = 0;
        foreach (string line in lines)
        {
            if (line.StartsWith("Host:", StringComparison.OrdinalIgnoreCase))
            {
                host = line.Substring(5).Trim();
            }
            else if (line.StartsWith("Content-Length:", StringComparison.OrdinalIgnoreCase))
            {
                if (!Int32.TryParse(line.Substring(15).Trim(), out contentLength) ||
                    contentLength < 0 || contentLength > MaximumRequestBodyBytes)
                {
                    throw new InvalidDataException("Provider request body length is invalid.");
                }
            }
        }

        byte[] discard = new byte[8192];
        int remaining = contentLength;
        while (remaining > 0)
        {
            int read = stream.Read(discard, 0, Math.Min(discard.Length, remaining));
            if (read <= 0) throw new IOException("Client disconnected before request body completed.");
            remaining -= read;
        }
        return new RequestMetadata(requestLine[0], requestLine[1], host);
    }

    private void RunCoordinationLoop()
    {
        while (!stopping)
        {
            TcpClient client = null;
            try
            {
                client = coordinationListener.AcceptTcpClient();
                using (NetworkStream stream = client.GetStream())
                {
                    string requestLine = ReadCoordinationRequestLine(stream);
                    bool statusPath = requestLine.StartsWith("GET /status ", StringComparison.Ordinal);
                    string body = statusPath ? StatusJson() : "{}";
                    WriteCoordinationResponse(stream, statusPath ? "200 OK" : "404 Not Found", body);
                }
            }
            catch (SocketException)
            {
                if (!stopping) throw;
            }
            catch (IOException)
            {
                // A coordination poll may be abandoned; it is not Provider evidence.
            }
            finally
            {
                if (client != null) client.Dispose();
            }
        }
    }

    private static string ReadCoordinationRequestLine(NetworkStream stream)
    {
        StringBuilder firstLine = new StringBuilder();
        bool firstLineComplete = false;
        int matched = 0;
        byte[] terminator = new byte[] { 13, 10, 13, 10 };
        int total = 0;
        while (total < MaximumHeaderBytes)
        {
            int current = stream.ReadByte();
            if (current < 0) break;
            total++;
            if (!firstLineComplete)
            {
                if (current == 10)
                {
                    if (firstLine.Length > 0 && firstLine[firstLine.Length - 1] == '\r')
                    {
                        firstLine.Length--;
                    }
                    firstLineComplete = true;
                }
                else
                {
                    firstLine.Append((char)current);
                }
            }

            if (current == terminator[matched])
            {
                matched++;
                if (matched == terminator.Length) break;
            }
            else
            {
                matched = current == terminator[0] ? 1 : 0;
            }
        }
        return firstLine.ToString();
    }

    private string StatusJson()
    {
        string state = closed ? "CLOSED" : active ? "ACTIVE" : "READY";
        return "{\"state\":\"" + state +
            "\",\"ready\":true,\"active\":" + active.ToString().ToLowerInvariant() +
            ",\"closed\":" + closed.ToString().ToLowerInvariant() +
            ",\"providerRequestCount\":" + Volatile.Read(ref providerRequestCount) + "}";
    }

    private static void WriteCoordinationResponse(NetworkStream stream, string status, string body)
    {
        byte[] bodyBytes = Encoding.UTF8.GetBytes(body);
        byte[] headers = Encoding.ASCII.GetBytes(
            "HTTP/1.1 " + status + "\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Content-Length: " + bodyBytes.Length + "\r\n" +
            "Connection: close\r\n\r\n");
        stream.Write(headers, 0, headers.Length);
        stream.Write(bodyBytes, 0, bodyBytes.Length);
        stream.Flush();
    }

    private static void WriteCompleteResponse(SslStream stream, string status, string body)
    {
        byte[] bodyBytes = Encoding.UTF8.GetBytes(body);
        byte[] headers = Encoding.ASCII.GetBytes(
            "HTTP/1.1 " + status + "\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: " + bodyBytes.Length + "\r\n" +
            "Connection: close\r\n\r\n");
        stream.Write(headers, 0, headers.Length);
        stream.Write(bodyBytes, 0, bodyBytes.Length);
        stream.Flush();
    }

    private static void WriteSignal(string path, string value)
    {
        string directory = Path.GetDirectoryName(path);
        if (String.IsNullOrEmpty(directory))
        {
            throw new InvalidOperationException("Signal path must include a directory.");
        }
        Directory.CreateDirectory(directory);
        string temporary = Path.Combine(directory, "." + Path.GetFileName(path) + "." + Guid.NewGuid().ToString("N") + ".tmp");
        try
        {
            File.WriteAllText(temporary, value, new UTF8Encoding(false));
            if (File.Exists(path)) File.Delete(path);
            File.Move(temporary, path);
        }
        finally
        {
            if (File.Exists(temporary)) File.Delete(temporary);
        }
    }

    private static bool CertificateMatchesHost(string certificateDnsName, string host)
    {
        if (String.Equals(certificateDnsName, host, StringComparison.OrdinalIgnoreCase)) return true;
        if (String.IsNullOrWhiteSpace(certificateDnsName) || !certificateDnsName.StartsWith("*.", StringComparison.Ordinal)) return false;
        string suffix = certificateDnsName.Substring(1);
        return host.EndsWith(suffix, StringComparison.OrdinalIgnoreCase) &&
            host.Length > suffix.Length &&
            host.Substring(0, host.Length - suffix.Length).IndexOf('.') < 0;
    }

    public void Dispose()
    {
        stopping = true;
        try { providerListener.Stop(); } catch { }
        try { coordinationListener.Stop(); } catch { }
        if (coordinationThread.IsAlive) coordinationThread.Join(1000);
        certificate.Dispose();
    }

    private sealed class RequestMetadata
    {
        internal static readonly RequestMetadata Invalid = new RequestMetadata("", "", "");
        private readonly string method;
        private readonly string path;
        private readonly string host;

        internal RequestMetadata(string method, string path, string host)
        {
            this.method = method;
            this.path = path;
            this.host = host;
        }

        internal bool IsExpected(string expectedHost, string expectedPath)
        {
            string normalizedHost = host;
            int colon = normalizedHost.LastIndexOf(':');
            if (colon > 0) normalizedHost = normalizedHost.Substring(0, colon);
            return String.Equals(method, "POST", StringComparison.Ordinal) &&
                String.Equals(path, expectedPath, StringComparison.Ordinal) &&
                String.Equals(normalizedHost, expectedHost, StringComparison.OrdinalIgnoreCase);
        }
    }
}
'@

Add-Type -TypeDefinition $fixtureSource -Language CSharp
$fixture = [ProviderBodyStallHttpsFixture]::new(
    $providerHost,
    $providerPort,
    $providerPath,
    $CoordinationPort,
    $certificate,
    $CertificatePassword,
    $activeSignal,
    $closedSignal
)

try {
    $fixture.Run()
}
finally {
    $fixture.Dispose()
}
