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
        Name = 'answer-engine-to-service'
        File = 'com\portfolio\agent\answer\engine\BadServiceEngine.java'
        Source = @'
package com.portfolio.agent.answer.engine;
import com.portfolio.agent.answer.service.AnswerService;
public final class BadServiceEngine {
    private AnswerService service;
}
'@
        Rule = 'answer-engine-boundary'
        Stubs = @{
            'com\portfolio\agent\answer\service\AnswerService.java' = @'
package com.portfolio.agent.answer.service;
public final class AnswerService {}
'@
        }
    },
    @{
        Name = 'answer-engine-to-mapper'
        File = 'com\portfolio\agent\answer\engine\BadMapperEngine.java'
        Source = @'
package com.portfolio.agent.answer.engine;
import com.portfolio.agent.answer.mapper.AnswerResponseMapper;
public final class BadMapperEngine {
    private AnswerResponseMapper mapper;
}
'@
        Rule = 'answer-engine-boundary'
        Stubs = @{
            'com\portfolio\agent\answer\mapper\AnswerResponseMapper.java' = @'
package com.portfolio.agent.answer.mapper;
public final class AnswerResponseMapper {}
'@
        }
    },
    @{
        Name = 'misplaced-answer-engine-to-mapper'
        File = 'misplaced\MapperEngine.java'
        Source = @'
package com.portfolio.agent.answer.engine;
import com.portfolio.agent.answer.mapper.AnswerResponseMapper;
public final class MapperEngine {
    private AnswerResponseMapper importedMapper;
    private com.portfolio.agent.answer.mapper.AnswerResponseMapper qualifiedMapper;
}
'@
        Rule = 'answer-engine-boundary'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.answer.mapper.AnswerResponseMapper;'
        AdditionalExpectedViolations = @(
            @{
                Rule = 'answer-engine-boundary'
                Line = 5
                Statement = 'com.portfolio.agent.answer.mapper.AnswerResponseMapper'
            }
        )
        Stubs = @{
            'com\portfolio\agent\answer\mapper\AnswerResponseMapper.java' = @'
package com.portfolio.agent.answer.mapper;
public final class AnswerResponseMapper {}
'@
        }
    },
    @{
        Name = 'package-path-mismatch'
        File = 'misplaced\AllowedEngine.java'
        Source = @'
package com.portfolio.agent.answer.engine;
import com.portfolio.agent.answer.domain.AnswerResult;
public final class AllowedEngine {
    private AnswerResult result;
}
'@
        Rule = 'package-path-mismatch'
        ExpectedLine = 1
        ExpectedStatement = 'package com.portfolio.agent.answer.engine;'
        Stubs = @{
            'com\portfolio\agent\answer\domain\AnswerResult.java' = @'
package com.portfolio.agent.answer.domain;
public final class AnswerResult {}
'@
        }
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
        Rule = 'controller-layer-boundary'
    },
    @{
        Name = 'portfolio-controller-to-repository'
        File = 'com\portfolio\agent\portfolio\controller\RepositoryController.java'
        Source = @'
package com.portfolio.agent.portfolio.controller;
import com.portfolio.agent.portfolio.repository.PublicPortfolioRepository;
public final class RepositoryController {
    private PublicPortfolioRepository repository;
}
'@
        Rule = 'controller-layer-boundary'
        Stubs = @{
            'com\portfolio\agent\portfolio\repository\PublicPortfolioRepository.java' = @'
package com.portfolio.agent.portfolio.repository;
public interface PublicPortfolioRepository {}
'@
        }
    },
    @{
        Name = 'answer-controller-to-engine'
        File = 'com\portfolio\agent\answer\controller\EngineController.java'
        Source = @'
package com.portfolio.agent.answer.controller;
import com.portfolio.agent.answer.engine.AnswerEngine;
public final class EngineController {
    private AnswerEngine engine;
}
'@
        Rule = 'controller-layer-boundary'
        Stubs = @{
            'com\portfolio\agent\answer\engine\AnswerEngine.java' = @'
package com.portfolio.agent.answer.engine;
public interface AnswerEngine {}
'@
        }
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
    },
    @{
        Name = 'legacy-package-declaration'
        File = 'com\portfolio\agent\answer\application\LegacyAnswerService.java'
        Source = @'
package com.portfolio.agent.answer.application;
public final class LegacyAnswerService {}
'@
        Rule = 'legacy-package'
    },
    @{
        Name = 'legacy-package-import'
        File = 'com\portfolio\agent\portfolio\service\LegacyImport.java'
        Source = @'
package com.portfolio.agent.portfolio.service;
import com.portfolio.agent.portfolio.application.LegacyPortfolioService;
public final class LegacyImport {}
'@
        Rule = 'legacy-package'
    },
    @{
        Name = 'legacy-commented-multiline-static-import'
        File = 'com\portfolio\agent\common\web\CommentedLegacyStaticImport.java'
        Source = @'
package com.portfolio.agent.common.web;
import /* static legacy member */ static
    com.portfolio.agent.answer.infrastructure
    . LegacyAnswerFactory
    . create
    ;
public final class CommentedLegacyStaticImport {}
'@
        Rule = 'legacy-package'
        ExpectedLine = 2
        ExpectedStatement = 'import static com.portfolio.agent.answer.infrastructure.LegacyAnswerFactory.create;'
    },
    @{
        Name = 'legacy-commented-multiline-import'
        File = 'com\portfolio\agent\portfolio\service\CommentedLegacyImport.java'
        Source = @'
package com.portfolio.agent.portfolio.service;
import com.portfolio.agent.portfolio
    // legacy layer follows on the next line
    . application
    . LegacyPortfolioService
    ;
public final class CommentedLegacyImport {}
'@
        Rule = 'legacy-package'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.portfolio.application.LegacyPortfolioService;'
    },
    @{
        Name = 'legacy-commented-multiline-package'
        File = 'com\portfolio\agent\answer\application\CommentedLegacyPackage.java'
        Source = @'
package
    com.portfolio.agent.answer./* legacy layer */application
    ;
public final class CommentedLegacyPackage {}
'@
        Rule = 'legacy-package'
        ExpectedLine = 1
        ExpectedStatement = 'package com.portfolio.agent.answer.application;'
    },
    @{
        Name = 'legacy-unicode-escaped-package'
        File = 'com\portfolio\agent\answer\application\UnicodeLegacyPackage.java'
        Source = @'
package com.portfolio.agent.answer.\uuuu0061pplication;
public final class UnicodeLegacyPackage {}
'@
        Rule = 'legacy-package'
        ExpectedLine = 1
        ExpectedStatement = 'package com.portfolio.agent.answer.application;'
    },
    @{
        Name = 'legacy-unicode-escaped-static-import'
        File = 'com\portfolio\agent\common\web\UnicodeLegacyImport.java'
        Source = @'
package com.portfolio.agent.common.web;
import static com.portfolio.agent.answer.\uu0069nfrastructure.LegacyFactory.create;
public final class UnicodeLegacyImport {}
'@
        Rule = 'legacy-package'
        ExpectedLine = 2
        ExpectedStatement = 'import static com.portfolio.agent.answer.infrastructure.LegacyFactory.create;'
    },
    @{
        Name = 'multiline-common-static-to-business'
        File = 'com\portfolio\agent\common\web\MultilineCommon.java'
        Source = @'
package com.portfolio.agent.common.web;
import /* cross-module */ static
    com.portfolio.agent.answer.domain.AnswerResult
    . create
    ;
public final class MultilineCommon {}
'@
        Rule = 'common-business'
        ExpectedLine = 2
        ExpectedStatement = 'import static com.portfolio.agent.answer.domain.AnswerResult.create;'
    },
    @{
        Name = 'multiline-portfolio-service-to-controller'
        File = 'com\portfolio\agent\portfolio\service\MultilineService.java'
        Source = @'
package com.portfolio.agent.portfolio.service;
import com.portfolio.agent.portfolio
    . /* forbidden web dependency */ controller
    . PortfolioController;
public final class MultilineService {}
'@
        Rule = 'portfolio-service-controller'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.portfolio.controller.PortfolioController;'
    },
    @{
        Name = 'multiline-answer-core-to-portfolio'
        File = 'com\portfolio\agent\answer\service\MultilineAnswerService.java'
        Source = @'
package com.portfolio.agent.answer.service;
import com.portfolio.agent
    . portfolio
    . domain
    . ProjectProfile;
public final class MultilineAnswerService {}
'@
        Rule = 'answer-core-portfolio'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.portfolio.domain.ProjectProfile;'
    },
    @{
        Name = 'multiline-answer-boundary-to-portfolio'
        File = 'com\portfolio\agent\answer\dto\response\MultilineAnswerResponse.java'
        Source = @'
package com.portfolio.agent.answer.dto.response;
import com.portfolio.agent.portfolio
    // response types cannot cross the boundary
    . dto
    . response
    . EvidenceResponse;
public final class MultilineAnswerResponse {}
'@
        Rule = 'answer-portfolio-boundary'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.portfolio.dto.response.EvidenceResponse;'
    },
    @{
        Name = 'multiline-adapter-boundary'
        File = 'com\portfolio\agent\answer\adapter\portfolio\MultilineAdapter.java'
        Source = @'
package com.portfolio.agent.answer.adapter.portfolio;
import com.portfolio.agent.portfolio.repository
    . /* concrete file repository */ file
    . JsonPublicPortfolioRepository;
public final class MultilineAdapter {}
'@
        Rule = 'answer-portfolio-adapter-boundary'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;'
    },
    @{
        Name = 'multiline-controller-to-infrastructure'
        File = 'com\portfolio\agent\portfolio\controller\MultilineController.java'
        Source = @'
package com.portfolio.agent.portfolio.controller;
import com.portfolio.agent.portfolio.repository
    . file
    . JsonPublicPortfolioRepository;
public final class MultilineController {}
'@
        Rule = 'controller-layer-boundary'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.portfolio.repository.file.JsonPublicPortfolioRepository;'
    },
    @{
        Name = 'multiline-portfolio-to-answer'
        File = 'com\portfolio\agent\portfolio\domain\MultilinePortfolio.java'
        Source = @'
package com.portfolio.agent.portfolio.domain;
import com.portfolio.agent
    . /* reverse dependency */ answer
    . domain
    . AnswerResult;
public final class MultilinePortfolio {}
'@
        Rule = 'portfolio-answer'
        ExpectedLine = 2
        ExpectedStatement = 'import com.portfolio.agent.answer.domain.AnswerResult;'
    },
    @{
        Name = 'fqn-answer-service-to-portfolio-domain'
        File = 'com\portfolio\agent\answer\service\FqnAnswerService.java'
        Source = @'
package com.portfolio.agent.answer.service;
public final class FqnAnswerService {
    private com.portfolio.agent.portfolio.domain.ProjectProfile profile;
}
'@
        Rule = 'answer-core-portfolio'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.portfolio.domain.ProjectProfile'
        Stubs = @{
            'com\portfolio\agent\portfolio\domain\ProjectProfile.java' = @'
package com.portfolio.agent.portfolio.domain;
public final class ProjectProfile {}
'@
        }
    },
    @{
        Name = 'fqn-answer-engine-to-mapper'
        File = 'com\portfolio\agent\answer\engine\FqnMapperEngine.java'
        Source = @'
package com.portfolio.agent.answer.engine;
public final class FqnMapperEngine {
    private com.portfolio.agent.answer.mapper.AnswerResponseMapper mapper;
}
'@
        Rule = 'answer-engine-boundary'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.answer.mapper.AnswerResponseMapper'
        Stubs = @{
            'com\portfolio\agent\answer\mapper\AnswerResponseMapper.java' = @'
package com.portfolio.agent.answer.mapper;
public final class AnswerResponseMapper {}
'@
        }
    },
    @{
        Name = 'fqn-common-to-business'
        File = 'com\portfolio\agent\common\web\FqnCommon.java'
        Source = @'
package com.portfolio.agent.common.web;
public final class FqnCommon {
    private com.portfolio.agent.answer.domain.AnswerResult result;
}
'@
        Rule = 'common-business'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.answer.domain.AnswerResult'
        Stubs = @{
            'com\portfolio\agent\answer\domain\AnswerResult.java' = @'
package com.portfolio.agent.answer.domain;
public final class AnswerResult {}
'@
        }
    },
    @{
        Name = 'fqn-portfolio-service-to-controller'
        File = 'com\portfolio\agent\portfolio\service\FqnPortfolioService.java'
        Source = @'
package com.portfolio.agent.portfolio.service;
public final class FqnPortfolioService {
    private com.portfolio.agent.portfolio.controller.PortfolioController controller;
}
'@
        Rule = 'portfolio-service-controller'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.portfolio.controller.PortfolioController'
        Stubs = @{
            'com\portfolio\agent\portfolio\controller\PortfolioController.java' = @'
package com.portfolio.agent.portfolio.controller;
public final class PortfolioController {}
'@
        }
    },
    @{
        Name = 'fqn-answer-boundary-to-portfolio'
        File = 'com\portfolio\agent\answer\dto\response\FqnAnswerResponse.java'
        Source = @'
package com.portfolio.agent.answer.dto.response;
public final class FqnAnswerResponse {
    private com.portfolio.agent.portfolio
            . dto . response . EvidenceResponse evidence;
}
'@
        Rule = 'answer-portfolio-boundary'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.portfolio.dto.response.EvidenceResponse'
        Stubs = @{
            'com\portfolio\agent\portfolio\dto\response\EvidenceResponse.java' = @'
package com.portfolio.agent.portfolio.dto.response;
public final class EvidenceResponse {}
'@
        }
    },
    @{
        Name = 'fqn-adapter-boundary'
        File = 'com\portfolio\agent\answer\adapter\portfolio\FqnAdapter.java'
        Source = @'
package com.portfolio.agent.answer.adapter.portfolio;
public final class FqnAdapter {
    private com.portfolio.agent.portfolio.controller.PortfolioController controller;
}
'@
        Rule = 'answer-portfolio-adapter-boundary'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.portfolio.controller.PortfolioController'
        Stubs = @{
            'com\portfolio\agent\portfolio\controller\PortfolioController.java' = @'
package com.portfolio.agent.portfolio.controller;
public final class PortfolioController {}
'@
        }
    },
    @{
        Name = 'fqn-portfolio-to-answer'
        File = 'com\portfolio\agent\portfolio\domain\FqnPortfolio.java'
        Source = @'
package com.portfolio.agent.portfolio.domain;
public final class FqnPortfolio {
    private com.portfolio.agent.answer.domain.AnswerResult result;
}
'@
        Rule = 'portfolio-answer'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.answer.domain.AnswerResult'
        Stubs = @{
            'com\portfolio\agent\answer\domain\AnswerResult.java' = @'
package com.portfolio.agent.answer.domain;
public final class AnswerResult {}
'@
        }
    },
    @{
        Name = 'fqn-legacy-package'
        File = 'com\portfolio\agent\answer\service\FqnLegacyService.java'
        Source = @'
package com.portfolio.agent.answer.service;
public final class FqnLegacyService {
    private com.portfolio.agent.answer.application.LegacyAnswerService legacy;
}
'@
        Rule = 'legacy-package'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.answer.application.LegacyAnswerService'
        Stubs = @{
            'com\portfolio\agent\answer\application\LegacyAnswerService.java' = @'
package com.portfolio.agent.answer.application;
public final class LegacyAnswerService {}
'@
        }
    },
    @{
        Name = 'fqn-controller-to-repository'
        File = 'com\portfolio\agent\portfolio\controller\FqnRepositoryController.java'
        Source = @'
package com.portfolio.agent.portfolio.controller;
public final class FqnRepositoryController {
    private com.portfolio.agent.portfolio.repository.PublicPortfolioRepository repository;
}
'@
        Rule = 'controller-layer-boundary'
        ExpectedLine = 3
        ExpectedStatement = 'com.portfolio.agent.portfolio.repository.PublicPortfolioRepository'
        Stubs = @{
            'com\portfolio\agent\portfolio\repository\PublicPortfolioRepository.java' = @'
package com.portfolio.agent.portfolio.repository;
public interface PublicPortfolioRepository {}
'@
        }
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
    private com.portfolio.agent.portfolio.domain.PortfolioSnapshot qualifiedSnapshot;
    private com.portfolio.agent.portfolio.repository.PublicPortfolioRepository qualifiedRepository;
    private String legacyImportText =
            "import com.portfolio.agent.portfolio.application.LegacyService;";
    private String legacyPackageText = """
            package com.portfolio.agent.answer.infrastructure;
            import static com.portfolio.agent.answer.application.LegacyFactory.create;
            """;
    private String escapedDelimiterText = """
            escaped delimiter: \"""
            package com.portfolio.agent.answer.infrastructure;
            import static com.portfolio.agent.answer.application.LegacyFactory.create;
            """;
    private String unicodeEscapedDelimiterText = """
            unicode escaped delimiter: \u005c"""
            package com.portfolio.agent.answer.infrastructure;
            """;
    private String qualifiedReferenceText =
            "com.portfolio.agent.answer.service.AnswerService";
    private String qualifiedReferenceTextBlock = """
            com.portfolio.agent.portfolio.repository.PublicPortfolioRepository
            """;
    // com.portfolio.agent.answer.engine.AnswerEngine
    /* com.portfolio.agent.portfolio.validation.PortfolioSnapshotValidator */
    // \\u000apackage com.portfolio.agent.answer.application;
}
'@

$unicodeEligibilityCases = @(
    @{
        Name = 'one-raw-backslash'
        ClassName = 'SlashOne'
        Slashes = '\'
        ExpectedUnsafe = $true
    },
    @{
        Name = 'two-raw-backslashes'
        ClassName = 'SlashTwo'
        Slashes = '\\'
        ExpectedUnsafe = $false
    },
    @{
        Name = 'three-raw-backslashes'
        ClassName = 'SlashThree'
        Slashes = '\\\'
        ExpectedUnsafe = $true
    },
    @{
        Name = 'four-raw-backslashes'
        ClassName = 'SlashFour'
        Slashes = '\\\\'
        ExpectedUnsafe = $false
    }
)

$unicodeGeneratedBackslashCases = @(
    @{
        Name = 'generated-plus-one-raw-backslash'
        ClassName = 'GeneratedSlashOne'
        Slashes = '\'
        ExpectedUnsafe = $true
    },
    @{
        Name = 'generated-plus-two-raw-backslashes'
        ClassName = 'GeneratedSlashTwo'
        Slashes = '\\'
        ExpectedUnsafe = $true
    },
    @{
        Name = 'generated-plus-three-raw-backslashes'
        ClassName = 'GeneratedSlashThree'
        Slashes = '\\\'
        ExpectedUnsafe = $false
    },
    @{
        Name = 'generated-plus-four-raw-backslashes'
        ClassName = 'GeneratedSlashFour'
        Slashes = '\\\\'
        ExpectedUnsafe = $true
    }
)

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
        [System.IO.File]::WriteAllText(
            $samplePath,
            $case.Source,
            [System.Text.UTF8Encoding]::new($false)
        )
        if ($case.ContainsKey('Stubs')) {
            foreach ($stubEntry in $case.Stubs.GetEnumerator()) {
                $stubPath = Join-Path (Join-Path $caseRoot 'main\java') $stubEntry.Key
                New-Item -ItemType Directory -Path (Split-Path -Parent $stubPath) -Force | Out-Null
                [System.IO.File]::WriteAllText(
                    $stubPath,
                    $stubEntry.Value,
                    [System.Text.UTF8Encoding]::new($false)
                )
            }

            $javaSources = Get-ChildItem -LiteralPath (Join-Path $caseRoot 'main\java') -Recurse -File -Filter '*.java' |
                    ForEach-Object { $_.FullName }
            $classesPath = Join-Path $caseRoot 'classes'
            New-Item -ItemType Directory -Path $classesPath | Out-Null
            $javacOutput = (& javac -d $classesPath $javaSources 2>&1 | Out-String)
            if ($LASTEXITCODE -ne 0) {
                throw "Expected unsafe fixture '$($case.Name)' to compile. Output: $javacOutput"
            }
        }

        $result = Invoke-Checker $caseRoot
        if ($result.ExitCode -ne 1) {
            throw "Expected unsafe fixture '$($case.Name)' to exit 1. Exit: $($result.ExitCode). Output: $($result.Output)"
        }

        if ($case.ContainsKey('ExpectedStatement')) {
            $expectedMatch = $case.ExpectedStatement
            $expectedLineNumber = $case.ExpectedLine
        } else {
            $sourceLines = @($case.Source -split '\r?\n')
            $expectedSourceLine = $sourceLines | Where-Object {
                $_ -match '^\s*import\s+' -or
                $_ -match '^\s*package\s+com\.portfolio\.agent\.(portfolio|answer)\.(api|application|infrastructure|domain\.model|domain\.repository)(\.|;)'
            } | Select-Object -First 1
            if ($null -eq $expectedSourceLine) {
                throw "Expected unsafe fixture '$($case.Name)' to contain an import or legacy package declaration."
            }
            $expectedMatch = $expectedSourceLine.Trim()
            $expectedLineNumber = [array]::IndexOf($sourceLines, $expectedSourceLine) + 1
        }
        $expectedViolation = "$($case.Rule):$samplePath`:$expectedLineNumber`:$expectedMatch"
        $outputLines = @($result.Output -split '\r?\n' | Where-Object { $_.Length -gt 0 })
        if ($expectedViolation -notin $outputLines) {
            throw "Expected unsafe fixture '$($case.Name)' output to include '$expectedViolation'. Output: $($result.Output)"
        }
        if ($case.ContainsKey('AdditionalExpectedViolations')) {
            foreach ($additional in $case.AdditionalExpectedViolations) {
                $additionalViolation = "$($additional.Rule):$samplePath`:$($additional.Line):$($additional.Statement)"
                if ($additionalViolation -notin $outputLines) {
                    throw "Expected unsafe fixture '$($case.Name)' output to include '$additionalViolation'. Output: $($result.Output)"
                }
            }
        }
    }

    foreach ($eligibilityCase in $unicodeEligibilityCases) {
        $caseRoot = Join-Path $fixtureRoot ('unicode-eligibility-' + $eligibilityCase.Name)
        $samplePath = Join-Path $caseRoot ($eligibilityCase.ClassName + '.java')
        New-Item -ItemType Directory -Path $caseRoot -Force | Out-Null
        $source = @(
            "// $($eligibilityCase.Slashes)u000apackage com.portfolio.agent.answer.application;"
            "public final class $($eligibilityCase.ClassName) {}"
        ) -join "`r`n"
        Set-Content -LiteralPath $samplePath -Value $source -Encoding Ascii

        $javacOutput = (& javac -d $caseRoot $samplePath 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0) {
            throw "Expected Unicode eligibility fixture '$($eligibilityCase.Name)' to compile. Output: $javacOutput"
        }

        $result = Invoke-Checker $caseRoot
        if ($eligibilityCase.ExpectedUnsafe) {
            if ($result.ExitCode -ne 1) {
                throw "Expected Unicode eligibility fixture '$($eligibilityCase.Name)' to exit 1. Output: $($result.Output)"
            }
            $expectedViolation = "legacy-package:$samplePath`:2:package com.portfolio.agent.answer.application;"
            $outputLines = @($result.Output -split '\r?\n' | Where-Object { $_.Length -gt 0 })
            if ($expectedViolation -notin $outputLines) {
                throw "Expected Unicode eligibility fixture '$($eligibilityCase.Name)' output to include '$expectedViolation'. Output: $($result.Output)"
            }
        } elseif ($result.ExitCode -ne 0) {
            throw "Expected Unicode eligibility fixture '$($eligibilityCase.Name)' to pass. Output: $($result.Output)"
        }
    }

    foreach ($eligibilityCase in $unicodeGeneratedBackslashCases) {
        $caseRoot = Join-Path $fixtureRoot ('unicode-generated-backslash-' + $eligibilityCase.Name)
        $samplePath = Join-Path $caseRoot ($eligibilityCase.ClassName + '.java')
        New-Item -ItemType Directory -Path $caseRoot -Force | Out-Null
        $source = @(
            "// \u005c$($eligibilityCase.Slashes)u000apackage com.portfolio.agent.answer.application;"
            "public final class $($eligibilityCase.ClassName) {}"
        ) -join "`r`n"
        Set-Content -LiteralPath $samplePath -Value $source -Encoding Ascii

        $javacOutput = (& javac -d $caseRoot $samplePath 2>&1 | Out-String)
        if ($LASTEXITCODE -ne 0) {
            throw "Expected generated-backslash fixture '$($eligibilityCase.Name)' to compile. Output: $javacOutput"
        }

        $result = Invoke-Checker $caseRoot
        if ($eligibilityCase.ExpectedUnsafe) {
            if ($result.ExitCode -ne 1) {
                throw "Expected generated-backslash fixture '$($eligibilityCase.Name)' to exit 1. Output: $($result.Output)"
            }
            $expectedViolation = "legacy-package:$samplePath`:2:package com.portfolio.agent.answer.application;"
            $outputLines = @($result.Output -split '\r?\n' | Where-Object { $_.Length -gt 0 })
            if ($expectedViolation -notin $outputLines) {
                throw "Expected generated-backslash fixture '$($eligibilityCase.Name)' output to include '$expectedViolation'. Output: $($result.Output)"
            }
        } elseif ($result.ExitCode -ne 0) {
            throw "Expected generated-backslash fixture '$($eligibilityCase.Name)' to pass. Output: $($result.Output)"
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
