$ErrorActionPreference = 'Stop'

$checker = Join-Path $PSScriptRoot 'architecture-check.ps1'
$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar)
$fixtureRoot = Join-Path $tempRoot ('architecture-check-' + [guid]::NewGuid())

$cases = @(
    @{
        Name = 'portfolio-service-to-controller'
        File = 'com\portfolio\agent\portfolio\service\BadService.java'
        Source = @'
package com.portfolio.agent.portfolio.service;
import com.portfolio.agent.portfolio.controller.PortfolioController;
public final class BadService {}
'@
        Rule = 'portfolio-service-controller'
    },
    @{
        Name = 'answer-domain-to-portfolio'
        File = 'com\portfolio\agent\answer\domain\BadAnswer.java'
        Source = @'
package com.portfolio.agent.answer.domain;
import com.portfolio.agent.portfolio.domain.ProjectProfile;
public final class BadAnswer {}
'@
        Rule = 'answer-core-portfolio'
    },
    @{
        Name = 'answer-engine-to-repository'
        File = 'com\portfolio\agent\answer\engine\BadEngine.java'
        Source = @'
package com.portfolio.agent.answer.engine;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
public final class BadEngine {}
'@
        Rule = 'answer-core-portfolio'
    },
    @{
        Name = 'answer-dto-to-portfolio-dto'
        File = 'com\portfolio\agent\answer\dto\response\BadResponse.java'
        Source = @'
package com.portfolio.agent.answer.dto.response;
import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;
public final class BadResponse {}
'@
        Rule = 'answer-portfolio-boundary'
    },
    @{
        Name = 'answer-portfolio-adapter-to-controller'
        File = 'com\portfolio\agent\answer\adapter\portfolio\BadControllerAdapter.java'
        Source = @'
package com.portfolio.agent.answer.adapter.portfolio;
import com.portfolio.agent.portfolio.controller.PortfolioController;
public final class BadControllerAdapter {}
'@
        Rule = 'answer-portfolio-adapter-boundary'
    },
    @{
        Name = 'answer-portfolio-adapter-to-file-repository'
        File = 'com\portfolio\agent\answer\adapter\portfolio\BadFileRepositoryAdapter.java'
        Source = @'
package com.portfolio.agent.answer.adapter.portfolio;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
public final class BadFileRepositoryAdapter {}
'@
        Rule = 'answer-portfolio-adapter-boundary'
    },
    @{
        Name = 'controller-to-file-repository'
        File = 'com\portfolio\agent\portfolio\controller\BadController.java'
        Source = @'
package com.portfolio.agent.portfolio.controller;
import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;
public final class BadController {}
'@
        Rule = 'controller-infrastructure'
    },
    @{
        Name = 'common-to-business'
        File = 'com\portfolio\agent\common\web\BadCommon.java'
        Source = @'
package com.portfolio.agent.common.web;
import com.portfolio.agent.answer.domain.AnswerResult;
public final class BadCommon {}
'@
        Rule = 'common-business'
    },
    @{
        Name = 'portfolio-to-answer'
        File = 'com\portfolio\agent\portfolio\domain\BadPortfolio.java'
        Source = @'
package com.portfolio.agent.portfolio.domain;
import com.portfolio.agent.answer.domain.AnswerResult;
public final class BadPortfolio {}
'@
        Rule = 'portfolio-answer'
    },
    @{
        Name = 'common-static-to-business'
        File = 'com\portfolio\agent\common\web\BadStaticCommon.java'
        Source = @'
package com.portfolio.agent.common.web;
import static com.portfolio.agent.answer.domain.AnswerResult.create;
public final class BadStaticCommon {}
'@
        Rule = 'common-business'
    }
)

$allowedFile = 'com\portfolio\agent\answer\adapter\portfolio\AllowedAdapter.java'
$allowedSource = @'
package com.portfolio.agent.answer.adapter.portfolio;

import com.portfolio.agent.answer.gateway.PortfolioKnowledgeGateway;
import com.portfolio.agent.portfolio.domain.PortfolioSnapshot;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;

public final class AllowedAdapter {
    private PortfolioKnowledgeGateway gateway;
    private PortfolioSnapshot snapshot;
    private PublicPortfolioRepository repository;
}
'@

function Invoke-Checker([string]$SourcePath) {
    $output = (& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $checker -Path $SourcePath 2>&1 | Out-String)
    return @{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
}

try {
    if (-not (Test-Path -LiteralPath $checker -PathType Leaf)) {
        throw "Checker does not exist: $checker"
    }

    New-Item -ItemType Directory -Path $fixtureRoot | Out-Null

    foreach ($case in $cases) {
        $caseRoot = Join-Path $fixtureRoot $case.Name
        $samplePath = Join-Path (Join-Path $caseRoot 'main\java') $case.File
        New-Item -ItemType Directory -Path (Split-Path -Parent $samplePath) -Force | Out-Null
        Set-Content -LiteralPath $samplePath -Value $case.Source -Encoding UTF8

        $result = Invoke-Checker $caseRoot
        if ($result.ExitCode -ne 1) {
            throw "Expected unsafe fixture '$($case.Name)' to exit 1. Exit: $($result.ExitCode). Output: $($result.Output)"
        }

        $expectedImport = @($case.Source -split '\r?\n' | Where-Object { $_ -match '^\s*import\s+' })[0].Trim()
        $expectedViolation = "$($case.Rule):$samplePath`:2:$expectedImport"
        $outputLines = @($result.Output -split '\r?\n' | Where-Object { $_.Length -gt 0 })
        if ($expectedViolation -notin $outputLines) {
            throw "Expected unsafe fixture '$($case.Name)' output to include '$expectedViolation'. Output: $($result.Output)"
        }
    }

    $allowedRoot = Join-Path $fixtureRoot 'allowed-adapter'
    $allowedPath = Join-Path (Join-Path $allowedRoot 'main\java') $allowedFile
    New-Item -ItemType Directory -Path (Split-Path -Parent $allowedPath) -Force | Out-Null
    Set-Content -LiteralPath $allowedPath -Value $allowedSource -Encoding UTF8

    $allowedResult = Invoke-Checker $allowedRoot
    if ($allowedResult.ExitCode -ne 0) {
        throw "Expected allowed adapter fixture to pass. Output: $($allowedResult.Output)"
    }

    Write-Output 'architecture-check tests passed'
}
finally {
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixtureRoot = (Resolve-Path -LiteralPath $fixtureRoot).Path
        $fixtureParent = [System.IO.Path]::GetDirectoryName($resolvedFixtureRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar)
        if ($fixtureParent -ne $tempRoot -or -not ([System.IO.Path]::GetFileName($resolvedFixtureRoot).StartsWith('architecture-check-'))) {
            throw "Refusing to remove unverified fixture path: $resolvedFixtureRoot"
        }
        Remove-Item -LiteralPath $resolvedFixtureRoot -Recurse -Force
    }
}
