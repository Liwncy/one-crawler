param(
    [string]$SpiderName = "",
    [string]$OutputPath = "",
    [switch]$List,
    [switch]$RefreshLogin,
    [string]$BrowserUserDataDir = "",
    [string[]]$SystemProperty = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::UTF8

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$spidersModule = Join-Path $repoRoot 'one-modules\one-spiders'

function Format-MavenExecArg {
    param(
        [AllowEmptyString()]
        [string]$Value
    )

    if ($null -eq $Value) {
        return '""'
    }

    if ($Value.Contains("'")) {
        $escaped = $Value.Replace('"', '\"')
        if ($escaped -match '\s') {
            return '"' + $escaped + '"'
        }
        return $escaped
    }

    if ($Value -match '\s') {
        return "'" + $Value + "'"
    }
    return $Value
}

Push-Location $repoRoot
try {
    Write-Host '[1/2] 安装运行通用 Spider 开发入口所需模块...'
    & mvn -q -DskipTests install -pl one-modules/one-spiders -am
    if ($LASTEXITCODE -ne 0) {
        throw "Maven install failed with exit code $LASTEXITCODE"
    }

    Push-Location $spidersModule
    try {
        Write-Host '[2/2] 启动通用 Spider 开发入口...'
        $arguments = @(
            '-q'
            '-DskipTests'
            'org.codehaus.mojo:exec-maven-plugin:3.5.0:java'
            '-Dexec.mainClass=me.liwncy.spiders.SpiderDevApplication'
        )

        foreach ($item in $SystemProperty) {
            if ([string]::IsNullOrWhiteSpace($item)) {
                continue
            }
            $property = $item.Trim()
            if ($property.StartsWith('-D')) {
                $arguments += $property
            }
            else {
                $arguments += ('-D' + $property)
            }
        }

        $execArgs = @()
        if ($List.IsPresent) {
            $execArgs += '--list'
        }
        if (-not [string]::IsNullOrWhiteSpace($SpiderName)) {
            $execArgs += '--spider'
            $execArgs += $SpiderName.Trim()
        }
        if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
            $execArgs += '--output'
            $execArgs += $OutputPath.Trim()
        }
        if ($RefreshLogin.IsPresent) {
            $execArgs += '--refresh-login'
        }
        if (-not [string]::IsNullOrWhiteSpace($BrowserUserDataDir)) {
            $execArgs += '--browser-user-data-dir'
            $execArgs += $BrowserUserDataDir.Trim()
        }
        if ($execArgs.Count -gt 0) {
            $arguments += ('-Dexec.args=' + (($execArgs | ForEach-Object { Format-MavenExecArg $_ }) -join ' '))
        }

        & mvn @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Maven exec failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    Pop-Location
}

