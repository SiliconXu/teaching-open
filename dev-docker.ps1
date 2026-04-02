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
$ApiBaseSource = 'maven:3.8-openjdk-8-slim'
$ApiRuntimeImage = 'registry.cn-shanghai.aliyuncs.com/goodat/teaching-open-api:latest'
$ApiRuntimeBaseImage = 'teaching-open-local-openjdk8:latest'
$ApiRuntimeBaseSource = 'eclipse-temurin:8-jre-jammy'

$WebBuilderImage = 'teaching-open-web-builder'
$WebBaseImage = 'teaching-open-local-node16:latest'
$WebBaseSource = 'node:16'
$WebRuntimeImage = 'registry.cn-shanghai.aliyuncs.com/goodat/teaching-open-web:latest'
$WebRuntimeBaseImage = 'teaching-open-local-nginx:latest'
$WebRuntimeBaseSource = 'nginx:latest'

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
    Write-Step "Checking Docker"
    Invoke-Checked 'docker' @('version')
    Invoke-Checked 'docker' @('compose', 'version')
}

function Pull-And-Tag {
    param(
        [string]$Source,
        [string]$Target
    )

    Write-Step "Pulling $Source"
    Invoke-Checked 'docker' @('pull', $Source)

    Write-Step "Tagging $Source as $Target"
    Invoke-Checked 'docker' @('tag', $Source, $Target)
}

function Start-Infra {
    Write-Step 'Starting db and redis'
    Invoke-Checked 'docker' @('compose', '-f', $ComposeFile, 'up', '-d', 'db', 'redis')
}

function Build-Backend {
    Pull-And-Tag -Source $ApiBaseSource -Target $ApiBaseImage

    Write-Step 'Building backend builder image'
    Invoke-Checked 'docker' @(
        'build',
        '--build-arg', "BASE_IMAGE=$ApiBaseImage",
        '-t', $ApiBuilderImage,
        '-f', (Join-Path $ApiDir 'Dockerfile.builder'),
        $ApiDir
    )

    Write-Step 'Packaging backend jar'
    Invoke-Checked 'docker' @(
        'run', '--rm',
        '-v', "${ApiDir}:/workspace",
        '-w', '/workspace',
        $ApiBuilderImage,
        'bash', '-lc', 'mvn clean package'
    )

    Pull-And-Tag -Source $ApiRuntimeBaseSource -Target $ApiRuntimeBaseImage

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
    Pull-And-Tag -Source $WebBaseSource -Target $WebBaseImage

    Write-Step 'Building frontend builder image'
    Invoke-Checked 'docker' @(
        'build',
        '--build-arg', "BASE_IMAGE=$WebBaseImage",
        '-t', $WebBuilderImage,
        '-f', (Join-Path $WebDir 'Dockerfile.builder'),
        $WebDir
    )

    Write-Step 'Installing frontend dependencies and building dist'
    Invoke-Checked 'docker' @(
        'run', '--rm',
        '-v', "${WebDir}:/workspace",
        '-w', '/workspace',
        $WebBuilderImage,
        'bash', '-lc', 'npm ci --legacy-peer-deps && npm run build'
    )

    Pull-And-Tag -Source $WebRuntimeBaseSource -Target $WebRuntimeBaseImage

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
