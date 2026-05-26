param(
    [ValidateSet('full', 'infra', 'backend', 'frontend', 'up', 'down')]
    [string]$Action = 'full'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$DeployDir = Join-Path $ProjectRoot 'deploy'
$ApiDir = Join-Path $ProjectRoot 'api'
$WebDir = Join-Path $ProjectRoot 'web'
$ComposeFile = Join-Path $DeployDir 'docker-compose.yml'

$ApiBuilderImage = 'teaching-open-api-builder'
$ApiBaseImage = 'teaching-open-local-maven8:latest'
$ApiBaseSources = @(
    'docker.m.daocloud.io/library/maven:3.8-openjdk-8-slim',
    'maven:3.8-openjdk-8-slim'
)
$ApiMavenCacheVolume = 'teaching-open-m2-cache'
$ApiRuntimeImage = 'teaching-open-api:latest'
$ApiRuntimeBaseImage = 'teaching-open-local-openjdk8:latest'
$ApiRuntimeBaseSources = @(
    'docker.m.daocloud.io/library/eclipse-temurin:8-jre-jammy',
    'eclipse-temurin:8-jre-jammy'
)

$WebBuilderImage = 'teaching-open-web-builder'
$WebBaseImage = 'teaching-open-local-node16:latest'
$WebBaseSources = @(
    'docker.m.daocloud.io/library/node:16',
    'node:16'
)
$WebNpmCacheVolume = 'teaching-open-npm-cache'
$WebRuntimeImage = 'teaching-open-web:latest'
$WebRuntimeBaseImage = 'teaching-open-local-nginx:latest'
$WebRuntimeBaseSources = @(
    'docker.m.daocloud.io/library/nginx:latest',
    'nginx:latest'
)

$PullRetryCount = 3
$PullRetryDelaySeconds = 3

function Write-Step {
    param([string]$Message)
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-Checked {
    param(
        [string]$FilePath,
        [string[]]$Arguments
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed: $FilePath $($Arguments -join ' ')"
    }
}

function Test-Docker {
    Write-Step 'Checking Docker'
    Invoke-Checked 'docker' @('version')
    Invoke-Checked 'docker' @('compose', 'version')
}

function Image-Exists {
    param([string]$Image)

    & docker image inspect $Image *> $null
    return $LASTEXITCODE -eq 0
}

function Get-ImageSources {
    param(
        [string]$EnvVarName,
        [string[]]$Defaults
    )

    $sources = @()
    $envValue = [Environment]::GetEnvironmentVariable($EnvVarName)
    if (-not [string]::IsNullOrWhiteSpace($envValue)) {
        $sources += ($envValue -split '[,;]' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    }
    $sources += $Defaults

    $uniqueSources = New-Object System.Collections.Generic.List[string]
    foreach ($source in $sources) {
        if (-not $uniqueSources.Contains($source)) {
            $uniqueSources.Add($source)
        }
    }
    return $uniqueSources.ToArray()
}

function Pull-ImageWithRetry {
    param([string]$Image)

    for ($attempt = 1; $attempt -le $PullRetryCount; $attempt++) {
        Write-Step "Pulling $Image (attempt $attempt/$PullRetryCount)"
        & docker pull $Image
        if ($LASTEXITCODE -eq 0) {
            return $true
        }
        if ($attempt -lt $PullRetryCount) {
            Start-Sleep -Seconds $PullRetryDelaySeconds
        }
    }
    return $false
}

function Pull-And-Tag {
    param(
        [string[]]$Sources,
        [string]$Target
    )

    if (Image-Exists $Target) {
        Write-Step "Using cached image $Target"
        return
    }

    foreach ($source in $Sources) {
        if (Image-Exists $source) {
            Write-Step "Using local base image $source"
            Invoke-Checked 'docker' @('tag', $source, $Target)
            return
        }
    }

    foreach ($source in $Sources) {
        if (Pull-ImageWithRetry $source) {
            Write-Step "Tagging $source as $Target"
            Invoke-Checked 'docker' @('tag', $source, $Target)
            return
        }
    }

    throw "Failed to pull base image. Tried: $($Sources -join ', '). You can also pre-pull one of them manually or set TEACHING_OPEN_*_SOURCE environment variables."
}

function Ensure-Volume {
    param([string]$Name)

    Write-Step "Ensuring Docker volume $Name"
    Invoke-Checked 'docker' @('volume', 'create', $Name)
}

function Start-Infra {
    Write-Step 'Starting db and redis'
    Invoke-Checked 'docker' @('compose', '-f', $ComposeFile, 'up', '-d', 'db', 'redis')
}

function Build-Backend {
    Pull-And-Tag -Sources (Get-ImageSources -EnvVarName 'TEACHING_OPEN_API_BASE_SOURCE' -Defaults $ApiBaseSources) -Target $ApiBaseImage

    Write-Step 'Building backend builder image'
    Invoke-Checked 'docker' @(
        'build',
        '--build-arg', "BASE_IMAGE=$ApiBaseImage",
        '-t', $ApiBuilderImage,
        '-f', (Join-Path $ApiDir 'Dockerfile.builder'),
        $ApiDir
    )

    Ensure-Volume -Name $ApiMavenCacheVolume

    Write-Step 'Packaging backend jar'
    Invoke-Checked 'docker' @(
        'run', '--rm',
        '-v', "${ApiDir}:/workspace",
        '-v', "${ApiMavenCacheVolume}:/root/.m2",
        '-w', '/workspace',
        $ApiBuilderImage,
        'bash', '-lc', 'mvn clean package'
    )

    Pull-And-Tag -Sources (Get-ImageSources -EnvVarName 'TEACHING_OPEN_API_RUNTIME_BASE_SOURCE' -Defaults $ApiRuntimeBaseSources) -Target $ApiRuntimeBaseImage

    Write-Step 'Building backend runtime image'
    Invoke-Checked 'docker' @(
        'build',
        '--build-arg', "BASE_IMAGE=$ApiRuntimeBaseImage",
        '-t', $ApiRuntimeImage,
        '-f', (Join-Path $ApiDir 'Dockerfile'),
        $ApiDir
    )
}

function Build-Frontend {
    Pull-And-Tag -Sources (Get-ImageSources -EnvVarName 'TEACHING_OPEN_WEB_BASE_SOURCE' -Defaults $WebBaseSources) -Target $WebBaseImage

    Write-Step 'Building frontend builder image'
    Invoke-Checked 'docker' @(
        'build',
        '--build-arg', "BASE_IMAGE=$WebBaseImage",
        '-t', $WebBuilderImage,
        '-f', (Join-Path $WebDir 'Dockerfile.builder'),
        $WebDir
    )

    Ensure-Volume -Name $WebNpmCacheVolume

    Write-Step 'Installing frontend dependencies and building dist'
    Invoke-Checked 'docker' @(
        'run', '--rm',
        '-v', "${WebDir}:/workspace",
        '-v', "${WebNpmCacheVolume}:/root/.npm",
        '-w', '/workspace',
        $WebBuilderImage,
        'bash', '-lc', 'npm ci --cache /root/.npm --legacy-peer-deps && npm run build'
    )

    Pull-And-Tag -Sources (Get-ImageSources -EnvVarName 'TEACHING_OPEN_WEB_RUNTIME_BASE_SOURCE' -Defaults $WebRuntimeBaseSources) -Target $WebRuntimeBaseImage

    Write-Step 'Building frontend runtime image'
    Invoke-Checked 'docker' @(
        'build',
        '--build-arg', "BASE_IMAGE=$WebRuntimeBaseImage",
        '-t', $WebRuntimeImage,
        '-f', (Join-Path $WebDir 'Dockerfile'),
        $WebDir
    )
}

function Start-App {
    Write-Step 'Starting api and web'
    Invoke-Checked 'docker' @('compose', '-f', $ComposeFile, 'up', '-d', 'api', 'web')
}

function Recreate-Api {
    Write-Step 'Recreating api container'
    Invoke-Checked 'docker' @('compose', '-f', $ComposeFile, 'up', '-d', '--force-recreate', 'api')
}

function Recreate-Web {
    Write-Step 'Recreating web container'
    Invoke-Checked 'docker' @('compose', '-f', $ComposeFile, 'up', '-d', '--force-recreate', 'web')
}

function Stop-App {
    Write-Step 'Stopping all services'
    Invoke-Checked 'docker' @('compose', '-f', $ComposeFile, 'down')
}

Test-Docker

switch ($Action) {
    'full' {
        Start-Infra
        Build-Backend
        Build-Frontend
        Start-App
    }
    'infra' {
        Start-Infra
    }
    'backend' {
        Build-Backend
        Recreate-Api
    }
    'frontend' {
        Build-Frontend
        Recreate-Web
    }
    'up' {
        Start-App
    }
    'down' {
        Stop-App
    }
}

Write-Host ""
Write-Host "Done. Action: $Action" -ForegroundColor Green
