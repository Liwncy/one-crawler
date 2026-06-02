param(
    [string]$SpiderName = "",
    [string]$OutputPath = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir
$spidersModule = Join-Path $repoRoot 'one-modules\one-spiders'

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

        $execArgs = @()
        if (-not [string]::IsNullOrWhiteSpace($SpiderName)) {
            $execArgs += $SpiderName.Trim()
        }
        if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
            if ($execArgs.Count -eq 0) {
                $execArgs += ''
            }
            $execArgs += $OutputPath.Trim()
        }
        if ($execArgs.Count -gt 0) {
            $arguments += ('-Dexec.args=' + ($execArgs -join ' '))
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

