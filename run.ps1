# PowerShell run script
$binDir = Join-Path $PSScriptRoot "bin"
java -Xmx4g -cp $binDir com.segmentation.Main $args
