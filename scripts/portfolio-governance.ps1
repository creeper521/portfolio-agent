param(
    [Parameter(Mandatory = $true)]
    [string]$Command,
    [string]$Workspace,
    [string]$Candidate,
    [string]$ReviewRunId,
    [string]$ApprovedBy,
    [string]$PrivacyReviewId,
    [string]$BenchmarkRunId,
    [string]$ApprovalId,
    [string]$ReleaseRoot,
    [string]$TargetVersion,
    [string]$CaseId,
    [string]$TargetStatus,
    [string]$CaseSource,
    [string]$ContentVersion,
    [string]$FailureType,
    [string]$SanitizedObservation,
    [string]$ExpectedBehavior,
    [string]$RootCause,
    [string]$ResolutionNote,
    [string]$FixedVersion,
    [string]$RegressionBenchmarkCaseId,
    [string]$PlaybookDecision,
    [string[]]$PostSwitchProbeUri,
    [string]$JarPath,
    [string]$ModelDirectory,
    [string]$JavaExecutable = 'java.exe',
    [switch]$Confirm
)
$skillCli = Join-Path (Split-Path $PSScriptRoot -Parent) '.agents\skills\portfolio-governance\scripts\portfolio-governance.ps1'
& $skillCli -Command $Command -Workspace $Workspace -Candidate $Candidate `
    -ReviewRunId $ReviewRunId -ApprovedBy $ApprovedBy -PrivacyReviewId $PrivacyReviewId `
    -BenchmarkRunId $BenchmarkRunId -ApprovalId $ApprovalId -ReleaseRoot $ReleaseRoot `
    -TargetVersion $TargetVersion -CaseId $CaseId -TargetStatus $TargetStatus `
    -CaseSource $CaseSource -ContentVersion $ContentVersion -FailureType $FailureType `
    -SanitizedObservation $SanitizedObservation -ExpectedBehavior $ExpectedBehavior `
    -RootCause $RootCause -ResolutionNote $ResolutionNote -FixedVersion $FixedVersion `
    -RegressionBenchmarkCaseId $RegressionBenchmarkCaseId -PlaybookDecision $PlaybookDecision `
    -PostSwitchProbeUri $PostSwitchProbeUri `
    -JarPath $JarPath -ModelDirectory $ModelDirectory -JavaExecutable $JavaExecutable `
    -Confirm:$Confirm
exit $LASTEXITCODE
